package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.example;
import static com.infinite_craft.test.CraftedItemUseTest.tick;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * A punch with infinite_craft:launch flings the mob far, where knockback alone shoves it a few blocks.
 * Runs on a long runway: the default test area's barrier walls would stop the flight at 3 blocks
 */
public class LaunchTest {

	private static final String MEGA_PUNCHER = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:power_fist","infinite_craft:launch":{power:2.0f,up:0.5f}}}""";

	// 9 wide and 48 long: the default test area is walled in at 8 blocks
	static final String RUNWAY = "infinite-craft-test:runway";
	static final int FLIGHT_TICKS = 60;

	@GameTest(maxTicks = 80, skyAccess = true, structure = RUNWAY)
	public void launchFlingsAMobFarAndHigh(TestContext context) {
		punch(context, MEGA_PUNCHER, (across, up) -> context.assertTrue(across >= 15 && up >= 2,
			Text.literal("launched pig went %.1f blocks across and %.1f up".formatted(across, up))));
	}

	// the prompt's Mega Puncher as it was before launch: knockback alone sends a mob far, but skimming the ground
	@GameTest(maxTicks = 80, skyAccess = true, structure = RUNWAY)
	public void knockbackAloneSkimsTheGround(TestContext context) {
		String knockbackOnly = """
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:power_fist","minecraft:attribute_modifiers":[{type:"minecraft:attack_knockback",id:"infinite_craft:punch",amount:6.0d,operation:"add_value",slot:"mainhand"}]}}""";
		punch(context, knockbackOnly, (across, up) -> context.assertTrue(up < 2,
			Text.literal("knockback alone sent the pig %.1f blocks up".formatted(up))));
	}

	@GameTest(maxTicks = 80, skyAccess = true, structure = RUNWAY)
	public void promptMegaPuncherLaunches(TestContext context) {
		punch(context, example("Mega Puncher"), (across, up) -> context.assertTrue(across >= 15 && up >= 2,
			Text.literal("the prompt's Mega Puncher sent the pig %.1f blocks across and %.1f up".formatted(across, up))));
	}

	/** A survival player at the start of the runway, facing down it */
	static PlayerEntity runwayPlayer(TestContext context) {
		PlayerEntity player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
		Vec3d pos = context.getAbsolute(new Vec3d(4.5, 1, 1.5));
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	interface Verdict {
		void check(double across, double up);
	}

	/** A charged punch at a pig (with AI, since no-AI mobs ignore knockback), then watches it fly and land */
	private static void punch(TestContext context, String nbt, Verdict verdict) {
		ItemStack fist = ThrustTest.parse(context, nbt);
		PlayerEntity player = runwayPlayer(context);
		player.setStackInHand(Hand.MAIN_HAND, fist);
		// equipment modifiers apply and the attack cooldown recharges
		for (int t = 0; t < 60; t++) tick(player);
		PigEntity pig = context.spawnEntity(EntityType.PIG, new Vec3d(4.5, 1, 3.2));
		Vec3d start = pig.getEntityPos();
		double[] peak = {start.y};
		int[] airborne = {0};
		// where it first comes down: a pig panics and runs off after it lands
		Vec3d[] landed = {null};
		context.runAtTick(2, () -> player.attack(pig));
		context.runAtEveryTick(() -> {
			peak[0] = Math.max(peak[0], pig.getY());
			if (!pig.isOnGround()) airborne[0]++;
			else if (airborne[0] > 2 && landed[0] == null) landed[0] = pig.getEntityPos();
		});
		context.runAtTick(2 + FLIGHT_TICKS, () -> {
			double across = (landed[0] == null ? pig.getEntityPos() : landed[0]).subtract(start).horizontalLength();
			System.out.printf("[LaunchTest] %s: landed %.1f blocks away, %.1f up at the peak%n",
				fist.getName().getString(), across, peak[0] - start.y);
			verdict.check(across, peak[0] - start.y);
			pig.discard();
			context.complete();
		});
	}
}
