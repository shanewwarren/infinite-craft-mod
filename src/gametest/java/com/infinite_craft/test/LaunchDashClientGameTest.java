package com.infinite_craft.test;

import java.util.Properties;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A real client punches an iron golem (which shrugs off knockback) with a Mega Puncher and watches it fly,
 * then dashes in mid-air with dash boots. On a dedicated server without allow-flight, a triple dash must not be
 * pulled back or kicked: the server ends up agreeing where the player is.
 * Opens a game window. Run with `./gradlew runClientGameTest`; screenshots land in build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class LaunchDashClientGameTest implements FabricClientGameTest {

	private static final String MEGA_PUNCHER = "infinite_craft:custom_crafted_item[minecraft:item_model=\"infinite_craft:power_fist\","
		+ "minecraft:item_name=\"Mega Puncher\",infinite_craft:launch={power:2.0f,up:0.8f}]";

	private static final String DASH_BOOTS = "minecraft:iron_boots[minecraft:item_name=\"Dash Boots\",infinite_craft:dash={power:1.0f,charges:1}]";

	private static final String TRIPLE_DASH_BOOTS = "minecraft:iron_boots[minecraft:item_name=\"Triple Dash Boots\","
		+ "infinite_craft:dash={power:3.0f,charges:3}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("clear @a");
			server.runCommand("give @a " + MEGA_PUNCHER);
			server.runCommand("item replace entity @a armor.feet with " + DASH_BOOTS);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 20");
			server.runCommand("execute as @a at @s run summon minecraft:iron_golem ~ ~ ~2.2 {Tags:[\"punched\"]}");
			// a full-strength punch needs the attack cooldown recharged
			context.waitTicks(30);

			Vec3d golemStart = golemPos(server);
			context.getInput().pressKey(options -> options.attackKey);
			context.waitTicks(6);
			context.takeScreenshot("mega-puncher-launch");
			context.waitTicks(54);
			double flung = golemPos(server).subtract(golemStart).horizontalLength();
			System.out.printf("[LaunchDashClientGameTest] Mega Puncher sent an iron golem %.1f blocks%n", flung);
			if (flung < 15) throw new AssertionError("Mega Puncher sent the golem only %.1f blocks".formatted(flung));
			server.runCommand("kill @e[type=minecraft:iron_golem]");

			Vec3d start = clientPos(context);
			jumpThenDash(context, 1);
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			context.waitTicks(1);
			context.takeScreenshot("dash-boots");
			context.waitTicks(40);
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			double dashed = clientPos(context).subtract(start).horizontalLength();
			float health = context.computeOnClient(client -> client.player.getHealth());
			System.out.printf("[LaunchDashClientGameTest] singleplayer dash: %.1f blocks, health %.1f%n", dashed, health);
			if (dashed < 5) throw new AssertionError("dash boots went only %.1f blocks".formatted(dashed));
			if (health < 20) throw new AssertionError("dashing hurt: health " + health);
		}

		Properties properties = new Properties();
		properties.setProperty("allow-flight", "false");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
				TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("item replace entity @a armor.feet with " + TRIPLE_DASH_BOOTS);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(20);

			Vec3d start = clientPos(context);
			jumpThenDash(context, 3);
			context.waitTicks(60);
			boolean connected = context.computeOnClient(client -> client.getNetworkHandler() != null && client.world != null);
			Vec3d client = clientPos(context);
			Vec3d onServer = server.computeOnServer(s -> {
				ServerPlayerEntity player = s.getPlayerManager().getPlayerList().get(0);
				return player.getEntityPos();
			});
			double dashed = client.subtract(start).horizontalLength();
			double disagreement = client.distanceTo(onServer);
			System.out.printf("[LaunchDashClientGameTest] dedicated server triple dash: %.1f blocks, server %.2f blocks from the client, connected: %s%n",
				dashed, disagreement, connected);
			if (!connected) throw new AssertionError("kicked from the server for dashing");
			if (disagreement > 1) throw new AssertionError("server put the player %.1f blocks from where the client is".formatted(disagreement));
			if (dashed < 15) throw new AssertionError("triple dash went only %.1f blocks on the server".formatted(dashed));
		}
	}

	/** Jumps, then presses jump again in mid-air, once per dash, a few ticks apart. Jump only counts while held over a tick */
	private static void jumpThenDash(ClientGameTestContext context, int dashes) {
		context.getInput().holdKeyFor(options -> options.jumpKey, 2);
		for (int i = 0; i < dashes; i++) {
			context.waitTicks(i == 0 ? 3 : 5);
			context.getInput().holdKeyFor(options -> options.jumpKey, 2);
		}
	}

	private static Vec3d clientPos(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getEntityPos());
	}

	private static Vec3d golemPos(TestServerContext server) {
		return server.computeOnServer(s -> s.getOverworld()
			.getEntitiesByType(EntityType.IRON_GOLEM, golem -> golem.getCommandTags().contains("punched")).stream()
			.map(IronGolemEntity::getEntityPos).findFirst().orElseThrow());
	}
}
