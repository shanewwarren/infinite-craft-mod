package com.infinite_craft.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.mob.SkeletonEntity;

/**
 * A real client blows a Skeleton Army Horn at noon: skeletons in helmets appear around the player, on his client too,
 * and none of them burns. The screenshot shows the squad from behind the player.
 * Opens a game window. Run with `./gradlew runClientGameTest -PclientTests=Summon`
 */
@SuppressWarnings("UnstableApiUsage")
public class SummonClientGameTest implements FabricClientGameTest {

	private static final String HORN = "infinite_craft:custom_crafted_item[minecraft:item_model=\"infinite_craft:summon_horn\","
		+ "minecraft:item_name=\"Skeleton Army Horn\",infinite_craft:summon={entity:\"minecraft:skeleton\",count:5,seconds:60,"
		+ "gear:{mainhand:\"minecraft:bow\",head:\"minecraft:iron_helmet\"},color:\"#E0E0E0\"}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (ClientTestFilter.skip(getClass())) return;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a weapon.mainhand with " + HORN);
			server.runCommand("execute as @a at @s run fill ~-6 ~ ~-6 ~6 ~4 ~6 minecraft:air");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(20);

			context.getInput().pressKey(options -> options.useKey);
			context.waitTicks(40);

			int[] counts = context.computeOnClient(client -> {
				var skeletons = client.world.getEntitiesByClass(SkeletonEntity.class, client.player.getBoundingBox().expand(6), e -> true);
				return new int[] {skeletons.size(), (int) skeletons.stream().filter(SkeletonEntity::isOnFire).count()};
			});
			System.out.printf("[SummonClientGameTest] skeletons around the client player: %d, on fire: %d%n", counts[0], counts[1]);
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 25");
			context.waitTicks(10);
			context.takeScreenshot("summon-army");
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			if (counts[0] < 1) throw new AssertionError("no summoned skeletons on the client");
			if (counts[1] > 0) throw new AssertionError(counts[1] + " summoned skeletons burn at noon");
		}
	}
}
