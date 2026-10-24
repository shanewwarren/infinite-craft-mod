package com.infinite_craft.test;

import java.util.Properties;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

/**
 * A real client wearing a jetpack holds jump and flies, in singleplayer and on a dedicated server that doesn't
 * allow flight (which kicks players who float for 4 seconds). Opens a game window; screenshots in
 * build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class ThrustClientGameTest implements FabricClientGameTest {

	private static final String JETPACK = "infinite_craft:custom_crafted_item[minecraft:item_model=\"infinite_craft:jetpack\","
		+ "minecraft:item_name=\"Jetpack\",minecraft:equippable={slot:\"chest\",asset_id:\"infinite_craft:jetpack\"},infinite_craft:thrust={power:1.0f,speed:1.5f},"
		+ "minecraft:attribute_modifiers=[{type:\"minecraft:safe_fall_distance\",id:\"infinite_craft:jetpack_landing\",amount:20.0d,operation:\"add_value\",slot:\"chest\"}]]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("item replace entity @a armor.chest with " + JETPACK);
			context.waitTicks(10);

			double start = y(context);
			context.getInput().holdKey(options -> options.jumpKey);
			context.waitTicks(30);
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("jetpack-flying");
			context.waitTicks(5);
			context.getInput().releaseKey(options -> options.jumpKey);
			double climbed = y(context) - start;
			System.out.printf("[ThrustClientGameTest] singleplayer: climbed %.1f blocks in 2s%n", climbed);
			if (climbed < 6) throw new AssertionError("jetpack climbed only %.1f blocks in 2s".formatted(climbed));

			context.waitTicks(80);
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			float health = context.computeOnClient(client -> client.player.getHealth());
			double landed = y(context) - start;
			if (Math.abs(landed) > 1) throw new AssertionError("didn't land after letting go, %.1f above the start".formatted(landed));
			if (health < 20) throw new AssertionError("landing hurt: health " + health);
		}

		Properties properties = new Properties();
		properties.setProperty("allow-flight", "false");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
				TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("item replace entity @a armor.chest with " + JETPACK);
			context.waitTicks(10);

			double start = y(context);
			context.getInput().holdKey(options -> options.jumpKey);
			// the server kicks after 80 ticks of floating; fly for 120
			context.waitTicks(120);
			context.getInput().releaseKey(options -> options.jumpKey);
			boolean connected = context.computeOnClient(client -> client.getNetworkHandler() != null && client.world != null);
			double climbed = y(context) - start;
			System.out.printf("[ThrustClientGameTest] dedicated server: climbed %.1f blocks in 6s, connected: %s%n", climbed, connected);
			if (!connected) throw new AssertionError("kicked from the server for flying with a jetpack");
			if (climbed < 20) throw new AssertionError("jetpack climbed only %.1f blocks in 6s on the server".formatted(climbed));
			context.waitTicks(100);
		}
	}

	private static double y(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getY());
	}
}
