package com.infinite_craft.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Explosive and lightning guns in a real client: the rocket flies and bursts, lightning strikes, a golem gets hurt,
 * and a rocket fired point-blank into a glass wall blows a hole in it but leaves the shooter unharmed.
 * With /gamerule infiniteCraftBlastsBreakBlocks false the same rocket leaves a second wall whole.
 * Opens a game window. Run with `./gradlew runClientGameTest`; screenshots land in build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class BlastClientGameTest implements FabricClientGameTest {

	private static final String ROCKET_LAUNCHER = "infinite_craft:custom_crafted_item[minecraft:item_model=\"infinite_craft:rocket_launcher\","
		+ "minecraft:item_name=\"Rocket Launcher\",infinite_craft:shoot={kind:\"rocket\",damage:6.0f,explode:2.0f,interval:20}]";

	private static final String SMITE_WAND = "infinite_craft:custom_crafted_item[minecraft:item_model=\"infinite_craft:magic_wand\","
		+ "minecraft:item_name=\"Smite Wand\",infinite_craft:shoot={kind:\"beam\",damage:4.0f,lightning:true,color:\"yellow\",interval:20}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (ClientTestFilter.skip(getClass())) return;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty normal");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("clear @a");
			server.runCommand("give @a " + ROCKET_LAUNCHER);
			server.runCommand("give @a " + SMITE_WAND);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
			context.waitTicks(30);

			// lightning on a golem, first, while the ground is flat: rockets leave craters that lower the player
			context.getInput().pressKey(options -> options.hotbarKeys[1]);
			context.waitTicks(20);
			summonGolem(context, server, "smite", 8);
			float before = golemHealth(server, "smite");
			pullTrigger(context);
			context.waitTicks(2);
			context.takeScreenshot("lightning-strike");
			context.waitTicks(20);
			float damage = before - golemHealth(server, "smite");
			System.out.printf("[BlastClientGameTest] lightning wand did %.1f damage%n", damage);
			if (damage < 4 + 5) {
				throw new AssertionError("lightning wand did %.1f damage, expected the beam's 4 plus lightning's 5".formatted(damage));
			}
			context.getInput().pressKey(options -> options.hotbarKeys[0]);
			server.runCommand("kill @e[type=minecraft:iron_golem]");
			context.waitTicks(20);

			// the rocket in flight, then its burst on a golem 12 blocks away
			summonGolem(context, server, "rocket", 12);
			before = golemHealth(server, "rocket");
			pullTrigger(context);
			context.waitTicks(3);
			context.takeScreenshot("rocket-flying");
			context.waitTicks(5);
			context.takeScreenshot("rocket-blast");
			context.waitTicks(20);
			damage = before - golemHealth(server, "rocket");
			System.out.printf("[BlastClientGameTest] rocket did %.1f damage%n", damage);
			if (damage < 6) {
				throw new AssertionError("rocket did %.1f damage to the golem, expected at least 6".formatted(damage));
			}
			server.runCommand("kill @e[type=minecraft:iron_golem]");

			// point blank into a glass wall two blocks ahead: a hole in the wall, not in the shooter
			int glass = pointBlank(context, server, "rocket-point-blank");
			if (glass > 17) {
				throw new AssertionError("a rocket barely dented the wall: %d of 20 glass left".formatted(glass));
			}

			// the parent's off switch
			server.runCommand("gamerule infiniteCraftBlastsBreakBlocks false");
			glass = pointBlank(context, server, "rocket-point-blank-gamerule-off");
			server.runCommand("gamerule infiniteCraftBlastsBreakBlocks true");
			if (glass != 20) {
				throw new AssertionError("with the gamerule off a rocket still broke the wall: %d of 20 glass left".formatted(glass));
			}
		}
	}

	/** Fires a rocket into a fresh glass wall two blocks ahead; checks the shooter wasn't hurt and returns the glass left of 20 */
	private static int pointBlank(ClientGameTestContext context, TestServerContext server, String screenshot) {
		server.runCommand("execute as @a at @s run fill ~-2 ~ ~2 ~2 ~3 ~2 minecraft:glass");
		context.waitTicks(10);
		BlockPos wallCenter = server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getBlockPos().add(0, 1, 2));
		float health = context.computeOnClient(client -> client.player.getHealth());
		pullTrigger(context);
		context.waitTicks(4);
		context.takeScreenshot(screenshot);
		context.waitTicks(20);
		float healthAfter = server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getHealth());
		int glass = server.computeOnServer(s -> {
			int count = 0;
			for (BlockPos pos : BlockPos.iterate(wallCenter.add(-2, -1, 0), wallCenter.add(2, 2, 0))) {
				if (s.getOverworld().getBlockState(pos).isOf(Blocks.GLASS)) count++;
			}
			return count;
		});
		System.out.printf("[BlastClientGameTest] %s: health %.1f -> %.1f, %d of 20 glass left%n", screenshot, health, healthAfter, glass);
		if (healthAfter < health) {
			throw new AssertionError("a point-blank rocket hurt its shooter: %.1f -> %.1f".formatted(health, healthAfter));
		}
		server.runCommand("execute as @a at @s run fill ~-2 ~ ~2 ~2 ~3 ~2 minecraft:air");
		return glass;
	}

	private static void pullTrigger(ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.useKey);
		context.waitTicks(2);
		context.getInput().releaseKey(options -> options.useKey);
	}

	private static void summonGolem(ClientGameTestContext context, TestServerContext server, String tag, int distance) {
		server.runCommand("kill @e[type=minecraft:iron_golem]");
		server.runCommand("execute as @a at @s run summon minecraft:iron_golem ~ ~ ~" + distance + " {NoAI:1b,Tags:[\"" + tag + "\"]}");
		context.waitTicks(10);
	}

	private static float golemHealth(TestServerContext server, String tag) {
		return server.computeOnServer(s -> s.getOverworld()
			.getEntitiesByType(EntityType.IRON_GOLEM, golem -> golem.getCommandTags().contains(tag)).stream()
			.map(IronGolemEntity::getHealth).findFirst().orElseThrow());
	}
}
