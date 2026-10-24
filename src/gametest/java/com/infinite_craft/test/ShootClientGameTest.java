package com.infinite_craft.test;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

/**
 * The whole path a player takes: a real client holds right click with a crafted gun, the server shoots,
 * the beam renders and a mob gets hurt. Opens a game window. Run with `./gradlew runClientGameTest`;
 * screenshots land in build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class ShootClientGameTest implements FabricClientGameTest {

	private static final String RAY_GUN = "infinite_craft:custom_crafted_item[minecraft:item_model=\"minecraft:blaze_rod\","
		+ "minecraft:item_name=\"Ray Gun\",infinite_craft:shoot={kind:\"beam\",damage:5.0f,color:\"green\",interval:10}]";

	private static final String CHAIN_GUN = "minecraft:crossbow[minecraft:item_name=\"Chain Gun\","
		+ "infinite_craft:shoot={kind:\"bullet\",damage:2.0f,interval:4,count:2,spread:3.0f,color:\"gold\"}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("clear @a");
			server.runCommand("give @a " + RAY_GUN);
			server.runCommand("give @a " + CHAIN_GUN);
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
			context.waitTicks(10);

			List<String> tooltip = context.computeOnClient(client -> client.player.getMainHandStack()
				.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC).stream().map(Text::getString).toList());
			if (tooltip.stream().noneMatch(line -> line.contains("Shoots a beam"))) {
				throw new AssertionError("Ray Gun tooltip doesn't say it shoots: " + tooltip);
			}

			fireAtGolem(context, server, "ray-gun", 2 * 5);

			context.getInput().pressKey(options -> options.hotbarKeys[1]);
			context.waitTicks(2);
			fireAtGolem(context, server, "chain-gun", 2 * 2);
		}
	}

	/** Holds right click at a golem for 2 seconds, screenshots mid-fire, and checks the golem lost health */
	private static void fireAtGolem(ClientGameTestContext context, TestServerContext server, String name, float minDamage) {
		server.runCommand("kill @e[type=minecraft:iron_golem]");
		server.runCommand("execute as @a at @s run summon minecraft:iron_golem ~ ~ ~6 {NoAI:1b,Tags:[\"" + name + "\"]}");
		context.waitTicks(10);
		float before = golemHealth(server, name);

		context.getInput().holdKey(options -> options.useKey);
		context.waitTicks(6);
		context.takeScreenshot(name + "-firing");
		context.waitTicks(34);
		context.getInput().releaseKey(options -> options.useKey);
		context.waitTicks(10);

		float damage = before - golemHealth(server, name);
		System.out.printf("[ShootClientGameTest] %s did %.1f damage in 2s%n", name, damage);
		if (damage < minDamage) {
			throw new AssertionError("%s did %.1f damage to the golem, expected at least %.1f".formatted(name, damage, minDamage));
		}
	}

	private static float golemHealth(TestServerContext server, String tag) {
		return server.computeOnServer(s -> s.getOverworld()
			.getEntitiesByType(EntityType.IRON_GOLEM, golem -> golem.getCommandTags().contains(tag)).stream()
			.map(IronGolemEntity::getHealth).findFirst().orElseThrow());
	}
}
