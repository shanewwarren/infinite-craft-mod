package com.infinite_craft.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;

/**
 * A real client wears God Armor: the effects show up on the client, the aura sparkles around the player,
 * a husk close by gets hurt, and taking the armor off takes the effects away.
 * Opens a game window. Run with `./gradlew runClientGameTest`; screenshots land in build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class AuraClientGameTest implements FabricClientGameTest {

	private static final String GOD_ARMOR = "minecraft:golden_chestplate[minecraft:item_name=\"God Armor\","
		+ "infinite_craft:aura={effects:[{id:\"minecraft:regeneration\"},{id:\"minecraft:night_vision\"}],particle:\"minecraft:end_rod\",damage:2.0f,radius:4.0f}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("difficulty normal");
			server.runCommand("time set midnight");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a armor.chest with " + GOD_ARMOR);
			// room for the third-person camera
			server.runCommand("execute as @a at @s run fill ~-6 ~ ~-6 ~6 ~8 ~6 minecraft:air");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 10");
			server.runCommand("execute as @a at @s run summon minecraft:husk ~ ~ ~3 {NoAI:1b,Tags:[\"aura\"]}");
			context.waitTicks(30);

			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			context.waitTicks(30);
			context.takeScreenshot("god-armor-aura");
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			context.waitTicks(10);

			boolean effects = context.computeOnClient(client -> client.player.hasStatusEffect(StatusEffects.REGENERATION)
				&& client.player.hasStatusEffect(StatusEffects.NIGHT_VISION));
			float huskHurt = server.computeOnServer(s -> s.getOverworld()
				.getEntitiesByType(EntityType.HUSK, husk -> husk.getCommandTags().contains("aura")).stream()
				.map(husk -> husk.getMaxHealth() - husk.getHealth()).findFirst().orElse(-1f));
			System.out.printf("[AuraClientGameTest] effects on the client: %s, husk 3 blocks away took %.1f damage in 3.5s%n", effects, huskHurt);
			if (!effects) throw new AssertionError("God Armor's effects didn't reach the client");
			if (huskHurt < 4) throw new AssertionError("God Armor's aura did %.1f damage to a husk 3 blocks away".formatted(huskHurt));

			server.runCommand("item replace entity @a armor.chest with minecraft:air");
			context.waitTicks(20);
			boolean gone = context.computeOnClient(client -> client.player.getStatusEffects().isEmpty());
			System.out.printf("[AuraClientGameTest] a second after taking it off, effects gone: %s%n", gone);
			if (!gone) throw new AssertionError("effects stayed after taking God Armor off");
		}
	}
}
