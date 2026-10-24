package com.infinite_craft.process;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.InfiniteCraftConfig;
import com.infinite_craft.InfiniteItem;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.ai.Claude;
import com.infinite_craft.element.ElementItems;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

/**
 * Runs a fixed set of crafts through the real prompt, Claude, and Minecraft's own NBT parser,
 * so models can be compared on whether their results actually work in game.
 *
 * Spends API credits. Run with:
 *   ANTHROPIC_API_KEY=... ./gradlew test --tests '*ClaudeCraftEval*' -DclaudeEval.models=claude-opus-5-5,claude-haiku-5-5
 */
public class ClaudeCraftEval {

	private record Recipe(String name, List<ItemStack> inputs) {}

	private record Result(String recipe, String verdict, String detail, long millis) {}

	// Stands in for a world's registries, which enchantments, sounds etc. are parsed against
	private static RegistryWrapper.WrapperLookup registries;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		registries = BuiltinRegistries.createWrapperLookup();
	}

	private static List<Recipe> recipes() {
		return List.of(
			new Recipe("diamond + diamond + stick", List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK))),
			new Recipe("ender pearl + feather", List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.FEATHER))),
			new Recipe("slime ball + iron boots", List.of(new ItemStack(Items.SLIME_BALL), new ItemStack(Items.IRON_BOOTS))),
			new Recipe("bow + blaze rod + arrow", List.of(new ItemStack(Items.BOW), new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.ARROW))),
			new Recipe("cake + golden apple", List.of(new ItemStack(Items.CAKE), new ItemStack(Items.GOLDEN_APPLE))),
			new Recipe("pufferfish + trident", List.of(new ItemStack(Items.PUFFERFISH), new ItemStack(Items.TRIDENT))),
			new Recipe("tnt (break down)", List.of(new ItemStack(Items.TNT))),
			new Recipe("vanillaify + netherite ingot + feather", List.of(new ItemStack(InfiniteItem.VANILLAIFY), new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.FEATHER))),
			new Recipe("element water + element fire", List.of(ElementItems.ELEMENT_WATER.getDefaultStack(), ElementItems.ELEMENT_FIRE.getDefaultStack())),
			new Recipe("element fire + iron sword", List.of(ElementItems.ELEMENT_FIRE.getDefaultStack(), new ItemStack(Items.IRON_SWORD)))
		);
	}

	@Test
	void buildsPromptAndRequestForEveryRecipe() {
		InfiniteCraftConfig config = new InfiniteCraftConfig();
		config.ClaudeApiKey = "offline";
		config.ClaudeModelName = "claude-opus-5-5";
		InfiniteCraft.config = config;
		Claude claude = new Claude();
		claude.initClient(config);
		for (Recipe recipe : recipes()) {
			String prompt = AiPrompt.GeneratePrompt(new ArrayList<>(recipe.inputs), 3);
			if (prompt.isBlank()) throw new AssertionError("empty prompt for " + recipe.name);
			claude.buildParams(prompt);
		}
	}

	@Test
	void compareModels() throws IOException {
		String apiKey = System.getenv("ANTHROPIC_API_KEY");
		assumeTrue(apiKey != null && !apiKey.isEmpty(), "ANTHROPIC_API_KEY not set, skipping the Claude eval");

		StringBuilder report = new StringBuilder("# Claude craft eval\n\n");
		StringBuilder summary = new StringBuilder("| Model | Clean | Usable | Failed | Avg time | Cost | Cost per craft |\n|---|---|---|---|---|---|---|\n");

		for (String model : System.getProperty("claudeEval.models", "claude-opus-5-5").split(",")) {
			InfiniteCraftConfig config = new InfiniteCraftConfig();
			config.ModelType = "claude";
			config.ClaudeApiKey = apiKey;
			config.ClaudeModelName = model.trim();
			InfiniteCraft.config = config;
			InfiniteCraft.claude = new Claude();
			InfiniteCraft.claude.initClient(config);

			List<Result> results = new ArrayList<>();
			for (Recipe recipe : recipes()) {
				results.add(run(recipe));
			}

			long clean = results.stream().filter(r -> r.verdict.equals("clean")).count();
			long usable = results.stream().filter(r -> r.verdict.equals("usable")).count();
			long failed = results.size() - clean - usable;
			double avgSeconds = results.stream().mapToLong(Result::millis).average().orElse(0) / 1000;
			double cost = InfiniteCraft.claude.getSessionCost();
			summary.append("| %s | %d | %d | %d | %.1fs | $%.4f | $%.4f |\n".formatted(
				model, clean, usable, failed, avgSeconds, cost, cost / results.size()));

			report.append("## ").append(model).append("\n\n| Recipe | Verdict | Time | Result |\n|---|---|---|---|\n");
			for (Result r : results) {
				report.append("| %s | %s | %.1fs | `%s` |\n".formatted(
					r.recipe, r.verdict, r.millis / 1000.0, r.detail.replace("|", "\\|").replace("\n", " ")));
			}
			report.append("\n");
		}

		String full = report.insert(report.indexOf("\n\n") + 2, summary + "\n" +
			"*clean*: Minecraft parsed the whole result. *usable*: the item works but some components were dropped. *failed*: no item.\n\n").toString();
		Path out = Path.of("build", "claude-eval", "report.md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, full);
		System.out.println(full);
		System.out.println("Report written to " + out.toAbsolutePath());
	}

	private Result run(Recipe recipe) {
		long start = System.currentTimeMillis();
		try {
			String prompt = AiPrompt.GeneratePrompt(new ArrayList<>(recipe.inputs), 3);
			JsonObject response = AiApi.doPostClaude(prompt);
			long millis = System.currentTimeMillis() - start;

			if (!response.has("success") || !response.get("success").getAsBoolean()) {
				return new Result(recipe.name, "failed", "success=false " + response, millis);
			}
			if (response.has("element")) {
				JsonObject element = response.getAsJsonObject("element");
				Identifier model = Identifier.tryParse(element.get("model").getAsString());
				boolean modelExists = model != null && Registries.ITEM.containsId(model);
				String detail = "%s %s (%s, model %s)".formatted(
					element.get("emoji").getAsString(), element.get("name").getAsString(),
					element.get("color").getAsString(), element.get("model").getAsString());
				return new Result(recipe.name, modelExists ? "clean" : "usable", detail, millis);
			}

			String nbt = response.get("itemNbt").getAsString();
			boolean clean = ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), StringNbtReader.readCompound(nbt)).result().isPresent();
			ItemStack stack = InfiniteCraftProcess.parseItemStackFromNbt(nbt, registries);
			if (stack.isEmpty()) {
				return new Result(recipe.name, "failed", nbt, millis);
			}
			return new Result(recipe.name, clean ? "clean" : "usable", nbt, millis);
		} catch (Exception e) {
			return new Result(recipe.name, "failed", Optional.ofNullable(e.getMessage()).orElse(e.toString()),
				System.currentTimeMillis() - start);
		}
	}
}
