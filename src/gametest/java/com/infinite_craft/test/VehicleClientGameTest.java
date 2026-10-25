package com.infinite_craft.test;

import java.util.Optional;
import java.util.Properties;

import com.infinite_craft.vehicle.VehicleEntity;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A real client places a crafted Tank, climbs in, drives it with W, and fires with right click (while holding dirt,
 * which must not be placed) at the ground in front and at a husk down range. On a dedicated server without
 * allow-flight it drives for 30 s over bumps, turning: no kick, and the server agrees where the tank is.
 * Opens a game window. Run with `./gradlew runClientGameTest`; screenshots land in build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class VehicleClientGameTest implements FabricClientGameTest {

	private static final String TANK = "infinite_craft:custom_crafted_item[minecraft:item_model=\"infinite_craft:tank\","
		+ "minecraft:item_name=\"Tank\",minecraft:max_stack_size=1,"
		+ "infinite_craft:vehicle={body:\"tank\",speed:6.0f,health:120.0f,color:\"#556B2F\"},"
		+ "infinite_craft:shoot={kind:\"rocket\",damage:10.0f,explode:3.0f,interval:30}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (ClientTestFilter.skip(getClass())) return;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("clear @a");
			server.runCommand("give @a " + TANK);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(10);

			placeAndMount(context);
			Vec3d start = clientPos(context);
			context.getInput().holdKeyFor(options -> options.forwardKey, 60);
			double drove = clientPos(context).subtract(start).horizontalLength();
			System.out.printf("[VehicleClientGameTest] singleplayer: drove %.1f blocks in 3 s%n", drove);
			if (!riding(context)) throw new AssertionError("fell out of the tank while driving");
			if (drove < 15) throw new AssertionError("tank drove only %.1f blocks in 3 s".formatted(drove));

			context.runOnClient(client -> {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				client.player.setPitch(30);
			});
			context.waitTicks(5);
			context.takeScreenshot("tank-riding");
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("tank-riding-front");
			// looking off to the side from behind: the tank side-on, its turret turned to follow the look
			context.runOnClient(client -> {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				client.player.setYaw(client.player.getYaw() + 70);
			});
			context.waitTicks(5);
			context.takeScreenshot("tank-riding-side");
			context.runOnClient(client -> client.player.setYaw(client.player.getYaw() - 70));
			context.waitTicks(5);
			context.runOnClient(client -> {
				client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				client.player.setPitch(0);
			});

			// right click fires the tank's gun, not the dirt in hand: first at a husk down range,
			// then at the ground just ahead (the rider must be unhurt; it leaves a crater, so it goes last)
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:dirt 16");
			// on the ground ahead: summoned at the rider it would hang in the air (no AI, no falling) above the shell's path
			server.runCommand("execute as @e[type=infinite_craft:vehicle,limit=1] at @s rotated ~ 0 positioned ^ ^ ^14 run summon minecraft:husk ~ ~ ~ {NoAI:1b,Tags:[\"target\"]}");
			context.waitTicks(5);
			context.getInput().holdKeyFor(options -> options.useKey, 4);
			context.waitTicks(10);
			context.takeScreenshot("tank-firing");
			context.waitTicks(30);
			// a killed husk is gone after its death animation
			float huskHealth = server.computeOnServer(s -> husk(s).map(HuskEntity::getHealth).orElse(0.0f));
			System.out.printf("[VehicleClientGameTest] husk 14 blocks ahead: health %.1f of 20%n", huskHealth);
			if (huskHealth >= 20) throw new AssertionError("the tank's shell missed the husk");

			// the gun's 30-tick interval has passed
			context.runOnClient(client -> client.player.setPitch(35));
			context.waitTicks(2);
			context.getInput().holdKeyFor(options -> options.useKey, 4);
			context.waitTicks(20);
			context.takeScreenshot("tank-point-blank");
			float health = context.computeOnClient(client -> client.player.getHealth());
			int dirt = context.computeOnClient(client -> client.player.getMainHandStack().isOf(Items.DIRT) ? client.player.getMainHandStack().getCount() : 0);
			System.out.printf("[VehicleClientGameTest] point-blank shot: rider health %.1f, dirt in hand %d%n", health, dirt);
			if (health < 20) throw new AssertionError("the tank's own shell hurt its rider: health " + health);
			if (dirt != 16) throw new AssertionError("right click while riding placed dirt: %d left".formatted(dirt));
			if (!riding(context)) throw new AssertionError("firing got the rider out");

			context.getInput().holdKeyFor(options -> options.sneakKey, 2);
			context.waitTicks(5);
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			if (riding(context)) throw new AssertionError("shift didn't get out of the tank");
		}

		Properties properties = new Properties();
		properties.setProperty("allow-flight", "false");
		properties.setProperty("level-type", "minecraft:flat");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
				TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("give @a " + TANK);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			// 1-block bumps across the way every 12 blocks, out to 100 blocks in every direction
			for (int i = 12; i <= 96; i += 12) {
				server.runCommand("execute as @a at @s run fill ~-100 ~ ~%d ~100 ~ ~%d minecraft:stone".formatted(i, i));
				server.runCommand("execute as @a at @s run fill ~-100 ~ ~-%d ~100 ~ ~-%d minecraft:stone".formatted(i, i));
			}
			context.waitTicks(20);

			placeAndMount(context);
			Vec3d start = clientPos(context);
			// forward for 30 s, steering left for half a second every 5 s
			for (int leg = 0; leg < 6; leg++) {
				context.getInput().holdKey(options -> options.forwardKey);
				context.getInput().holdKeyFor(options -> options.leftKey, 10);
				context.waitTicks(90);
			}
			context.getInput().releaseKey(options -> options.forwardKey);
			context.waitTicks(20);

			boolean connected = context.computeOnClient(client -> client.getNetworkHandler() != null && client.world != null);
			Vec3d onClient = context.computeOnClient(client -> client.player.getVehicle().getEntityPos());
			Vec3d onServer = server.computeOnServer(s -> {
				ServerPlayerEntity player = s.getPlayerManager().getPlayerList().get(0);
				return player.getVehicle() == null ? player.getEntityPos() : player.getVehicle().getEntityPos();
			});
			double drove = onClient.subtract(start).horizontalLength();
			double disagreement = onClient.distanceTo(onServer);
			System.out.printf("[VehicleClientGameTest] dedicated server: drove %.1f blocks from the start in 30 s, server %.2f blocks from the client, connected: %s, riding: %s%n",
				drove, disagreement, connected, riding(context));
			if (!connected) throw new AssertionError("kicked from the server for driving");
			if (!riding(context)) throw new AssertionError("fell out of the tank on the server");
			if (disagreement > 1) throw new AssertionError("server put the tank %.1f blocks from where the client has it".formatted(disagreement));
			if (drove < 20) throw new AssertionError("tank drove only %.1f blocks from the start on the server".formatted(drove));
		}
	}

	/** Looks down at the ground ahead, places the tank there, and right-clicks it to get in */
	private static void placeAndMount(ClientGameTestContext context) {
		context.runOnClient(client -> client.player.setPitch(50));
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(10);
		if (!riding(context)) throw new AssertionError("couldn't place the tank and get in");
		context.runOnClient(client -> client.player.setPitch(0));
		context.waitTicks(2);
	}

	private static boolean riding(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getVehicle() instanceof VehicleEntity);
	}

	private static Vec3d clientPos(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getEntityPos());
	}

	private static Optional<HuskEntity> husk(MinecraftServer server) {
		return server.getOverworld().getEntitiesByType(EntityType.HUSK, husk -> husk.getCommandTags().contains("target"))
			.stream().<HuskEntity>map(husk -> husk).findFirst();
	}
}
