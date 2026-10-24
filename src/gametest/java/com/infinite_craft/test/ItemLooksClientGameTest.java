package com.infinite_craft.test;

import java.util.ArrayList;
import java.util.List;

import com.infinite_craft.ai.AiPrompt;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.item.model.MissingItemModel;
import net.minecraft.util.Identifier;

/**
 * The mod's crafted-item models all load in a real client, and screenshots show them in the hotbar and in hand
 * (build/run/clientGameTest/screenshots). Glow colors: the Ray Gun's comes from its shoot color, the
 * Lightsaber's from custom_model_data, the rest are model defaults
 */
@SuppressWarnings("UnstableApiUsage")
public class ItemLooksClientGameTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> models = AiPrompt.craftedModels().stream().map(AiPrompt.CraftedModel::model).toList();

		List<String> missing = new ArrayList<>();
		for (String model : models) {
			boolean isMissing = context.computeOnClient(client ->
				client.getBakedModelManager().getItemModel(Identifier.of(model)) instanceof MissingItemModel);
			if (isMissing) missing.add(model);
		}
		if (!missing.isEmpty()) throw new AssertionError("models that didn't load: " + missing);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");

			for (int page = 0; page * 9 < models.size(); page++) {
				server.runCommand("clear @a");
				for (int slot = 0; slot < 9 && page * 9 + slot < models.size(); slot++) {
					server.runCommand("item replace entity @a hotbar.%d with %s".formatted(slot, item(models.get(page * 9 + slot))));
				}
				context.waitTicks(5);
				context.takeScreenshot("looks-hotbar-" + (page + 1));
			}

			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with " + item("infinite_craft:ray_gun"));
			server.runCommand("item replace entity @a hotbar.1 with " + item("infinite_craft:lightsaber"));
			server.runCommand("item replace entity @a weapon.offhand with " + item("infinite_craft:minigun"));
			context.getInput().pressKey(options -> options.hotbarKeys[0]);
			context.waitTicks(20);
			context.takeScreenshot("looks-ray-gun-first-person");
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("looks-ray-gun-third-person");
			context.getInput().pressKey(options -> options.hotbarKeys[1]);
			context.waitTicks(10);
			context.takeScreenshot("looks-lightsaber-third-person");
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			context.waitTicks(5);
			context.takeScreenshot("looks-lightsaber-first-person");
		}
	}

	private static String item(String model) {
		String extra = switch (model) {
			case "infinite_craft:ray_gun" -> ",infinite_craft:shoot={kind:\"beam\",color:\"#AA44FF\"}";
			case "infinite_craft:lightsaber" -> ",minecraft:custom_model_data={colors:[16724016]}";
			default -> "";
		};
		return "infinite_craft:custom_crafted_item[minecraft:item_model=\"%s\"%s]".formatted(model, extra);
	}
}
