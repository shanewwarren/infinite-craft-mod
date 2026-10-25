package com.infinite_craft.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * A real client drinks a Shrink Potion: the client's own player becomes a quarter size (so the server's temporary
 * modifier reaches the client), and the screenshot shows a door frame towering over it.
 * Opens a game window. Run with `./gradlew runClientGameTest -PclientTests=TimedAttributes`
 */
@SuppressWarnings("UnstableApiUsage")
public class TimedAttributesClientGameTest implements FabricClientGameTest {

	private static final String SHRINK_POTION = "infinite_craft:custom_crafted_item[minecraft:item_model=\"minecraft:potion\","
		+ "minecraft:item_name=\"Shrink Potion\",minecraft:consumable={consume_seconds:0.8f,animation:\"drink\","
		+ "on_consume_effects:[{type:\"infinite_craft:attributes\",seconds:30.0f,modifiers:[{type:\"minecraft:scale\","
		+ "id:\"infinite_craft:shrink\",amount:-0.75d,operation:\"add_multiplied_total\"}]}]}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (ClientTestFilter.skip(getClass())) return;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a weapon.mainhand with " + SHRINK_POTION);
			// a stone door frame ahead, to show the size; nothing in it opens when right-clicked
			server.runCommand("execute as @a at @s run fill ~-4 ~ ~1 ~4 ~4 ~6 minecraft:air");
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~4 ~1 ~2 ~4 minecraft:stone_bricks");
			server.runCommand("execute as @a at @s run fill ~ ~ ~4 ~ ~1 ~4 minecraft:air");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(20);
			context.takeScreenshot("timed-attributes-before");

			context.getInput().holdKey(options -> options.useKey);
			context.waitTicks(25);
			context.getInput().releaseKey(options -> options.useKey);
			context.waitTicks(20);

			float scale = context.computeOnClient(client -> client.player.getScale());
			System.out.printf("[TimedAttributesClientGameTest] client scale after drinking: %.2f%n", scale);
			context.takeScreenshot("timed-attributes-shrunk");
			if (Math.abs(scale - 0.25f) > 0.02f) throw new AssertionError("after drinking, the client player's scale is " + scale);
		}
	}
}
