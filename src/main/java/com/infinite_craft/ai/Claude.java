package com.infinite_craft.ai;

import java.util.Map;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.InfiniteCraftConfig;

public class Claude {
	private AnthropicClient client = null;
	private String model = null;

	// USD per 1M tokens (input, output), used only to log what each craft costs
	private static final Map<String, double[]> PRICES = Map.of(
		"claude-opus-5-5", new double[]{4.00, 20.00},
		"claude-sonnet-5-5", new double[]{2.00, 10.00},
		"claude-haiku-5-5", new double[]{0.10, 0.50}
	);
	private double sessionCost = 0;
	private long sessionCrafts = 0;

	public boolean initClient(InfiniteCraftConfig config){
		if(client!=null || config.ClaudeApiKey == null || config.ClaudeApiKey.isEmpty()) return false;
		if("system".equals(config.ModelProxy)){
			System.setProperty("java.net.useSystemProxies", "true");
			InfiniteCraft.LOGGER.info("Using System Proxies");
		}
		client = AnthropicOkHttpClient.builder()
			.apiKey(config.ClaudeApiKey)
			.build();
		model = config.ClaudeModelName;
		InfiniteCraft.LOGGER.info("Claude initialized with model {}", model);
		return true;
	}

	public AnthropicClient getClient(){
		return client;
	}

	public String getModel(){
		return model;
	}

	public MessageCreateParams buildParams(String prompt){
		MessageCreateParams.Builder builder = MessageCreateParams.builder()
			.model(model)
			.maxTokens(16000L)
			.outputConfig(BetaOutputConfig.builder()
				.format(BetaJsonOutputFormat.builder()
					.schema(toJsonValueSchema(toStructuredOutputSchema(AiPrompt.getPromptJsonSchema())))
					.build())
				.build())
			.addUserMessage(prompt);
		// Haiku has no server-side refusal fallback; on the larger models a refused craft falls back to another model instead of failing
		if(!model.contains("haiku")){
			builder.addBeta("server-side-fallback-2026-07-01")
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
		}
		return builder.build();
	}

	/** Logs the tokens and returns what the craft cost in dollars */
	public double logUsage(BetaMessage response){
		long input = response.usage().inputTokens();
		long output = response.usage().outputTokens();
		double[] price = PRICES.getOrDefault(response.model().asString(), PRICES.get(model));
		double cost = price == null ? 0 : (input * price[0] + output * price[1]) / 1_000_000;
		synchronized(this){
			sessionCost += cost;
			++sessionCrafts;
			InfiniteCraft.LOGGER.info("Claude craft ({}): {} in / {} out tokens, ~${} (session: {} crafts, ~${})",
				response.model().asString(), input, output,
				"%.4f".formatted(cost), sessionCrafts, "%.4f".formatted(sessionCost));
		}
		return cost;
	}

	public synchronized double getSessionCost(){
		return sessionCost;
	}

	/**
	 * Claude's structured outputs need `additionalProperties: false` on every object
	 * and reject string length constraints, so adapt the shared schema
	 */
	static JsonObject toStructuredOutputSchema(JsonObject schema){
		JsonObject result = schema.deepCopy();
		adapt(result);
		return result;
	}

	private static void adapt(JsonElement element){
		if(element.isJsonObject()){
			JsonObject object = element.getAsJsonObject();
			object.remove("minLength");
			object.remove("maxLength");
			if(object.has("type") && object.get("type").isJsonPrimitive() && "object".equals(object.get("type").getAsString())){
				object.addProperty("additionalProperties", false);
			}
			for(Map.Entry<String, JsonElement> entry : object.entrySet()){
				adapt(entry.getValue());
			}
		} else if(element.isJsonArray()){
			for(JsonElement child : element.getAsJsonArray()){
				adapt(child);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static BetaJsonOutputFormat.Schema toJsonValueSchema(JsonObject schema){
		Map<String, Object> map = new Gson().fromJson(schema, Map.class);
		BetaJsonOutputFormat.Schema.Builder builder = BetaJsonOutputFormat.Schema.builder();
		for(Map.Entry<String, Object> entry : map.entrySet()){
			builder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
		}
		return builder.build();
	}
}
