package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.tick;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Wearing dash boots, pressing jump again in mid-air dashes the way you face; landing resets the dashes.
 * Runs on LaunchTest's runway, since the default test area is walled in at 8 blocks.
 * The real client's dash, and a dedicated server's view of it, are in DashClientGameTest
 */
public class DashTest {

	static final String DASH_BOOTS = """
		{id:"minecraft:iron_boots",count:1i,components:{"minecraft:item_name":"Dash Boots","infinite_craft:dash":{power:1.0f,charges:1}}}""";

	static final String DOUBLE_DASH_BOOTS = """
		{id:"minecraft:iron_boots",count:1i,components:{"minecraft:item_name":"Double Dash Boots","infinite_craft:dash":{power:1.0f,charges:2}}}""";

	@GameTest(maxTicks = 20, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void jumpingInMidAirDashes(TestContext context) {
		double dashed = dash(context, DASH_BOOTS, 1);
		context.assertTrue(dashed >= 5, Text.literal("dash boots went only %.1f blocks".formatted(dashed)));
		context.complete();
	}

	@GameTest(maxTicks = 20, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void oneChargeDashesOnce(TestContext context) {
		double once = dash(context, DASH_BOOTS, 1);
		double twice = dash(context, DASH_BOOTS, 2);
		context.assertTrue(twice < once + 1.5, Text.literal("a one-charge boot dashed twice: %.1f vs %.1f blocks".formatted(twice, once)));
		context.complete();
	}

	@GameTest(maxTicks = 20, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void twoChargesDashTwice(TestContext context) {
		double once = dash(context, DOUBLE_DASH_BOOTS, 1);
		double twice = dash(context, DOUBLE_DASH_BOOTS, 2);
		context.assertTrue(twice > once + 2, Text.literal("double dash went %.1f blocks, one dash %.1f".formatted(twice, once)));
		context.complete();
	}

	@GameTest(maxTicks = 20, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void jumpingInMidAirWithoutBootsDoesNothing(TestContext context) {
		double dashed = dash(context, null, 1);
		context.assertTrue(dashed < 1, Text.literal("moved %.1f blocks without dash boots".formatted(dashed)));
		context.complete();
	}

	/**
	 * Jumps, lets go, presses jump again near the top of the jump (and again a few ticks later for a second dash),
	 * then waits to land. Returns how far the player went across
	 */
	static double dash(TestContext context, String boots, int presses) {
		PlayerEntity player = LaunchTest.runwayPlayer(context);
		if (boots != null) player.equipStack(EquipmentSlot.FEET, ThrustTest.parse(context, boots));
		for (int t = 0; t < 5; t++) tick(player);
		Vec3d start = player.getEntityPos();
		float health = player.getHealth();
		for (int t = 0; t < 40; t++) {
			boolean press = t < 2 || t == 5 || presses > 1 && t == 12;
			player.setJumping(press);
			tick(player);
		}
		if (player.getHealth() < health) {
			throw new AssertionError("dashing hurt: health %.1f -> %.1f".formatted(health, player.getHealth()));
		}
		double across = player.getEntityPos().subtract(start).horizontalLength();
		System.out.printf("[DashTest] %s, %d press(es): %.1f blocks across%n", boots == null ? "no boots" : "boots", presses, across);
		return across;
	}
}
