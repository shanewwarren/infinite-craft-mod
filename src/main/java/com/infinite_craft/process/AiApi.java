package com.infinite_craft.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.ai.AiPrompt;

import net.minecraft.server.network.ServerPlayerEntity;

public class AiApi {
	
	public static JsonObject doPost(String prompt, ServerPlayerEntity player) throws IOException, RuntimeException {
		switch(InfiniteCraft.config.ModelType){
			case "none":
				throw new AiProblemException("Unable to run the model because it's disabled in the config file(.\"model\")");
			case "gemini":
				return doPostGemini(prompt, player);
			case "claude":
				return doPostClaude(prompt);
			case "ollama":
				return doPostOllama(prompt);
		}
		throw new AiProblemException("IDK why but this isn't supposed to happen");
	};

    public static JsonObject doPostGemini(String prompt, ServerPlayerEntity player) throws IOException, RuntimeException {
		try {
			Client geminiClient = InfiniteCraft.gemini.getClient(player);
			if(geminiClient==null){
				throw new AiProblemException("Gemini is not initialized");
			}
			GenerateContentResponse response =
				geminiClient.models.generateContent(
					InfiniteCraft.config.GeminiModelName,
					prompt,
					InfiniteCraft.gemini.getConfig(player)
				);
			return JsonParser.parseString(response.text()).getAsJsonObject();
		} catch( Exception e ){
            InfiniteCraft.LOGGER.error(e.getMessage());
			throw e;
		}
    }
    public static JsonObject doPostClaude(String prompt) throws RuntimeException {
		if(InfiniteCraft.claude.getClient()==null){
			throw new AiProblemException("Claude is not initialized, check \"claude\".\"api_key\" in the config file");
		}
		BetaMessage response = InfiniteCraft.claude.getClient().beta().messages().create(
			InfiniteCraft.claude.buildParams(prompt)
		);
		double cost = InfiniteCraft.claude.logUsage(response);
		BetaStopReason stopReason = response.stopReason().orElse(null);
		if(BetaStopReason.REFUSAL.equals(stopReason)){
			throw new AiProblemException("Claude declined to craft this");
		}
		if(BetaStopReason.MAX_TOKENS.equals(stopReason)){
			throw new AiProblemException("Claude ran out of tokens before finishing the craft");
		}
		StringBuilder text = new StringBuilder();
		response.content().forEach(block -> block.text().ifPresent(t -> text.append(t.text())));
		JsonObject result;
		try {
			result = JsonParser.parseString(text.toString()).getAsJsonObject();
		} catch(Exception e) {
			throw new AiProblemException("Claude returned something that isn't JSON: " + text);
		}
		// for the playtest log
		result.addProperty("costUsd", cost);
		return result;
    }
    public static JsonObject doPostOllama(String prompt) throws IOException, RuntimeException {
		String urlStr="http://localhost:11434/api/generate";
        // if(true){JsonObject tmp = new JsonObject();tmp.addProperty("success", true);tmp.addProperty("itemNbt", "{\"id\":\"minecraft:diamond\",\"count\":1,\"components\":{\"enchantments\":{\"sharpness\":2}}}");JsonObject response = new JsonObject();response.addProperty("response", tmp.toString());return response;}
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

		JsonObject body = JsonParser.parseString("""
			{
				"model": "deepseek-r1:8b",
				"stream": false
			}
			""").getAsJsonObject();
		body.addProperty("prompt", prompt);
		body.add("format", AiPrompt.getPromptJsonSchema());
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream is = conn.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			try{
				return JsonParser.parseString(
					JsonParser.parseReader(reader)
						.getAsJsonObject()
						.get("response")
						.getAsString()
				).getAsJsonObject();
			} catch(Exception e){
				throw new AiProblemException("The AI api is kinda broken,Unable to Get Response!\n"+e.getMessage());
			}
        }
    }
}
