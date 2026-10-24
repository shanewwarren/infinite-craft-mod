package com.infinite_craft.process;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.InfiniteCraftConfig;
import com.infinite_craft.InfiniteItem;
import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.ai.Claude;
import com.infinite_craft.element.ElementItems;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
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
		Abilities.initialize();
		InfiniteItem.initialize();
		ElementItems.initialize();
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
			new Recipe("element fire + iron sword", List.of(ElementItems.ELEMENT_FIRE.getDefaultStack(), new ItemStack(Items.IRON_SWORD))),
			// Wishes: an ingredient renamed in an anvil, like the items in the unlimited-crafting videos
			new Recipe("wish: Prime Energy Drink", List.of(named(Items.GLASS_BOTTLE, "Prime Energy Drink"), new ItemStack(Items.SUGAR))),
			new Recipe("wish: Jetpack", List.of(named(Items.IRON_INGOT, "Jetpack"), new ItemStack(Items.FIREWORK_ROCKET), new ItemStack(Items.LEATHER))),
			new Recipe("wish: Blue Lightsaber", List.of(named(Items.STICK, "Blue Lightsaber"), new ItemStack(Items.DIAMOND), new ItemStack(Items.GLOWSTONE_DUST))),
			new Recipe("wish: Ray Gun", List.of(named(Items.IRON_INGOT, "Ray Gun"), new ItemStack(Items.REDSTONE), new ItemStack(Items.EMERALD))),
			new Recipe("wish: Disc Saw", List.of(named(Items.IRON_INGOT, "Disc Saw"), new ItemStack(Items.STONE))),
			new Recipe("wish: Mega Puncher", List.of(named(Items.PISTON, "Mega Puncher"), new ItemStack(Items.SLIME_BALL))),
			new Recipe("wish: Nail Gun", List.of(named(Items.IRON_NUGGET, "Nail Gun"), new ItemStack(Items.IRON_INGOT))),
			new Recipe("wish: Chain Gun", List.of(named(Items.IRON_BLOCK, "Chain Gun"), new ItemStack(Items.GUNPOWDER))),
			new Recipe("wish: Rocket Launcher", List.of(named(Items.IRON_INGOT, "Rocket Launcher"), new ItemStack(Items.TNT))),
			new Recipe("wish: Thor's Hammer", List.of(named(Items.IRON_BLOCK, "Thor's Hammer"), new ItemStack(Items.STICK))),
			new Recipe("wish: Dash Boots", List.of(named(Items.LEATHER_BOOTS, "Dash Boots"), new ItemStack(Items.FEATHER))),
			new Recipe("wish: Steam Armor", List.of(named(Items.IRON_CHESTPLATE, "Steam Armor"), new ItemStack(Items.WATER_BUCKET))),
			new Recipe("wish: God Armor", List.of(named(Items.DIAMOND_CHESTPLATE, "God Armor"), new ItemStack(Items.GOLDEN_APPLE)))
		);
	}

	private static ItemStack named(Item item, String name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
		return stack;
	}

	// Components that make an item do something when used, worn or held
	private static final Set<String> FUNCTIONAL = Set.of(
		"minecraft:attribute_modifiers", "minecraft:consumable", "minecraft:food", "minecraft:equippable",
		"minecraft:glider", "minecraft:tool", "minecraft:weapon", "minecraft:enchantments", "minecraft:death_protection",
		"minecraft:blocks_attacks", "minecraft:damage_resistant", "minecraft:charged_projectiles",
		"infinite_craft:shoot", "infinite_craft:thrust", "infinite_craft:launch", "infinite_craft:dash", "infinite_craft:aura"
	);

	// The video's guns: each should shoot with no ammo, and feel different from the others; the last two should blast
	private static final List<String> GUN_WISHES = List.of("wish: Ray Gun", "wish: Nail Gun", "wish: Chain Gun",
		"wish: Rocket Launcher", "wish: Thor's Hammer");

	/** How a gun result shoots, e.g. `beam 6 dmg every 10t`, or why it doesn't shoot without ammo */
	static String gunSummary(String nbt) throws Exception {
		NbtCompound components = StringNbtReader.readCompound(nbt).getCompound("components").orElse(new NbtCompound());
		Optional<NbtCompound> shoot = components.getCompound("infinite_craft:shoot");
		if (shoot.isEmpty()) return "no shoot (needs ammo)";
		NbtCompound s = shoot.get();
		boolean destroys = s.getBoolean("destroy").orElse(s.getString("kind").orElse("beam").equals("rocket"));
		return "%s %s dmg every %st%s%s%s%s".formatted(s.getString("kind").orElse("beam"), s.get("damage"), s.get("interval"),
			s.contains("count") ? " ×" + s.get("count") : "", s.contains("spread") ? " spread " + s.get("spread") : "",
			s.contains("explode") || s.getString("kind").orElse("").equals("rocket")
				? " explode " + s.get("explode") + (destroys ? " (breaks blocks)" : "") : "",
			s.getBoolean("lightning").orElse(false) ? " lightning" : "");
	}

	/** The item_model a result asks for, if any */
	static Optional<String> itemModel(String nbt) throws Exception {
		return StringNbtReader.readCompound(nbt).getCompound("components").flatMap(c -> c.getString("minecraft:item_model"));
	}

	/** A vanilla item's model or one of the mod's; anything else renders as the missing texture */
	static boolean modelExists(String model) {
		Identifier id = Identifier.tryParse(model);
		return id != null && (Registries.ITEM.containsId(id) && !id.getNamespace().equals(InfiniteCraft.MOD_ID)
			|| AiPrompt.craftedModels().stream().anyMatch(m -> m.model().equals(model)));
	}

	@Test
	void shootTakesEverydayColorNames() throws Exception {
		for (String color : List.of("\"orange\"", "\"purple\"", "\"gold\"", "\"#33FF66\"", "16711680")) {
			String nbt = "{id:\"minecraft:bow\",count:1i,components:{\"infinite_craft:shoot\":{kind:\"bullet\",color:" + color + "}}}";
			if (!parsesCleanly(nbt)) throw new AssertionError("shoot color " + color + " doesn't parse");
		}
	}

	@Test
	void everyCraftedModelHasItsFiles() {
		for (AiPrompt.CraftedModel model : AiPrompt.craftedModels()) {
			String name = Identifier.of(model.model()).getPath();
			for (String file : List.of("items/" + name + ".json", "models/item/" + name + ".json",
					"textures/item/" + name + ".png", "textures/item/" + name + "_glow.png")) {
				if (ClaudeCraftEval.class.getResource("/assets/infinite_craft/" + file) == null) {
					throw new AssertionError(model.model() + " is missing " + file);
				}
			}
		}
	}

	/**
	 * Vanilla items behave like themselves; a custom item only does something through the components it was given.
	 * Reads what the result asked for, so it doesn't depend on what this test setup can parse
	 */
	static boolean doesSomething(String nbt) throws Exception {
		NbtCompound item = StringNbtReader.readCompound(nbt);
		if (!"infinite_craft:custom_crafted_item".equals(item.getString("id").orElse(""))) return true;
		NbtCompound components = item.getCompound("components").orElse(new NbtCompound());
		return components.getKeys().stream().anyMatch(FUNCTIONAL::contains);
	}

	/**
	 * Whether Minecraft parses the whole item, with nothing dropped. Block tags (like #minecraft:mineable/axe) are
	 * only bound once a world loads its data packs, so an unbound-tag error is a gap in this setup, not a bad item
	 */
	static boolean parsesCleanly(String nbt) throws Exception {
		try {
			return ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), StringNbtReader.readCompound(nbt)).result().isPresent();
		} catch (IllegalStateException e) {
			if (e.getMessage() != null && e.getMessage().startsWith("Tags not bound")) return true;
			throw e;
		}
	}

	@Test
	void powerExamplesParseAndDoSomething() throws Exception {
		for (String example : AiPrompt.POWER_EXAMPLES) {
			String nbt = example.strip();
			if (!parsesCleanly(nbt)) {
				throw new AssertionError("prompt example does not parse on this version: " + nbt + "\n"
					+ ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), StringNbtReader.readCompound(nbt)).error().orElseThrow().message());
			}
			if (!doesSomething(nbt)) {
				throw new AssertionError("inert example: " + nbt);
			}
		}
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
	void compareModels() throws Exception {
		String apiKey = System.getenv("ANTHROPIC_API_KEY");
		assumeTrue(apiKey != null && !apiKey.isEmpty(), "ANTHROPIC_API_KEY not set, skipping the Claude eval");

		StringBuilder report = new StringBuilder("# Claude craft eval\n\n");
		StringBuilder summary = new StringBuilder("| Model | Clean | Usable | Inert | Failed | Avg time | Cost | Cost per craft | Video guns | Mod looks |\n|---|---|---|---|---|---|---|---|---|---|\n");
		// every item result, for the game test to use in a real world (see CraftedItemUseTest)
		StringBuilder fixtures = new StringBuilder("# Crafted items from the last Claude eval. One per line: label<TAB>item NBT\n");

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
			long inert = results.stream().filter(r -> r.verdict.equals("inert")).count();
			long failed = results.size() - clean - usable - inert;
			double avgSeconds = results.stream().mapToLong(Result::millis).average().orElse(0) / 1000;
			double cost = InfiniteCraft.claude.getSessionCost();
			List<String> guns = new ArrayList<>();
			List<String> looks = new ArrayList<>();
			for (Result r : results) {
				if (r.detail.startsWith("{")) {
					itemModel(r.detail).filter(m -> m.startsWith("infinite_craft:")).ifPresent(m -> looks.add(r.recipe.replace("wish: ", "") + ": " + m.substring(15)));
				}
				if (r.detail.startsWith("{")) {
					fixtures.append(model.replace("claude-", "")).append(": ").append(r.recipe).append('\t').append(r.detail).append('\n');
				}
				if (GUN_WISHES.contains(r.recipe)) {
					guns.add(r.recipe.replace("wish: ", "") + ": " + (r.detail.startsWith("{") ? gunSummary(r.detail) : r.verdict));
				}
			}
			summary.append("| %s | %d | %d | %d | %d | %.1fs | $%.4f | $%.4f | %s | %d: %s |\n".formatted(
				model, clean, usable, inert, failed, avgSeconds, cost, cost / results.size(), String.join("<br>", guns),
				looks.size(), String.join("<br>", looks)));

			report.append("## ").append(model).append("\n\n| Recipe | Verdict | Time | Result |\n|---|---|---|---|\n");
			for (Result r : results) {
				report.append("| %s | %s | %.1fs | `%s` |\n".formatted(
					r.recipe, r.verdict, r.millis / 1000.0, r.detail.replace("|", "\\|").replace("\n", " ")));
			}
			report.append("\n");
		}

		String full = report.insert(report.indexOf("\n\n") + 2, summary + "\n" +
			"*clean*: Minecraft parsed the whole result. *usable*: the item works but some components were dropped. " +
			"*inert*: a custom item that does nothing (only a name and a model). *failed*: no item.\n\n").toString();
		Path out = Path.of("build", "claude-eval", "report.md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, full);
		Files.writeString(out.resolveSibling("crafted_items.txt"), fixtures);
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
			boolean clean = parsesCleanly(nbt);
			ItemStack stack;
			try {
				stack = InfiniteCraftProcess.parseItemStackFromNbt(nbt, registries);
			} catch (IllegalStateException e) {
				// unbound block tags, see parsesCleanly; the item itself is fine
				if (e.getMessage() == null || !e.getMessage().startsWith("Tags not bound")) throw e;
				stack = new ItemStack(InfiniteItem.CUSTOM_CRAFTED_ITEM);
			}
			if (stack.isEmpty()) {
				return new Result(recipe.name, "failed", nbt, millis);
			}
			if (!doesSomething(nbt)) {
				return new Result(recipe.name, "inert", nbt, millis);
			}
			Optional<String> model = itemModel(nbt);
			if (model.isPresent() && !modelExists(model.get())) {
				return new Result(recipe.name, "usable", "MISSING MODEL " + model.get() + ": " + nbt, millis);
			}
			return new Result(recipe.name, clean ? "clean" : "usable", nbt, millis);
		} catch (Exception e) {
			return new Result(recipe.name, "failed", Optional.ofNullable(e.getMessage()).orElse(e.toString()),
				System.currentTimeMillis() - start);
		}
	}
}
