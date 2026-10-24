package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.aim;
import static com.infinite_craft.test.CraftedItemUseTest.dummy;
import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.holdTrigger;
import static com.infinite_craft.test.CraftedItemUseTest.player;
import static com.infinite_craft.test.CraftedItemUseTest.tick;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.ShootAbility;
import com.infinite_craft.process.InfiniteCraftProcess;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * Guns made with infinite_craft:shoot fire with nothing but the gun in the inventory, over real ticks,
 * and their shots hurt a mob. Bullets fly as real projectiles here, unlike in CraftedItemUseTest
 */
public class ShootTest {

	private static final String RAY_GUN = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:blaze_rod","infinite_craft:shoot":{kind:"beam",damage:5.0f,color:"green",interval:10}}}""";

	private static final String NAIL_GUN = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:iron_ingot","infinite_craft:shoot":{kind:"bullet",damage:3.0f,interval:4}}}""";

	// a crossbow base, as Claude made guns before: the shoot ability replaces its need for arrows
	private static final String CHAIN_GUN = """
		{id:"minecraft:crossbow",count:1i,components:{"minecraft:item_name":"Chain Gun","infinite_craft:shoot":{kind:"bullet",damage:2.0f,interval:4,count:2,spread:4.0f}}}""";

	// a 10 second use_cooldown allows one shot in the test's 3 seconds
	private static final String SLOW_BLASTER = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:blaze_rod","minecraft:use_cooldown":{seconds:10.0f},"infinite_craft:shoot":{kind:"beam",damage:4.0f,interval:4}}}""";

	private static final int FIRING_TICKS = 60;

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void rayGunBeamHurtsAMobWithoutAmmo(TestContext context) {
		fireAtDummy(context, RAY_GUN, 2, Integer.MAX_VALUE);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void nailGunBulletsHurtAMobWithoutAmmo(TestContext context) {
		fireAtDummy(context, NAIL_GUN, 2, Integer.MAX_VALUE);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void crossbowBasedChainGunFiresWithoutArrows(TestContext context) {
		fireAtDummy(context, CHAIN_GUN, 2, Integer.MAX_VALUE);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void useCooldownLimitsFireRate(TestContext context) {
		fireAtDummy(context, SLOW_BLASTER, 1, 1);
	}

	/** Holds the trigger at a dummy mob for 3 seconds and checks how many shots' worth of damage it took */
	private static void fireAtDummy(TestContext context, String nbt, int minHits, int maxHits) {
		floor(context);
		ItemStack gun;
		try {
			gun = InfiniteCraftProcess.parseItemStackFromNbt(nbt.strip(), context.getWorld().getRegistryManager());
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
		float damagePerShot = gun.get(Abilities.SHOOT).damage();

		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, gun);
		PigEntity dummy = dummy(context);

		int[] tick = {0};
		context.runAtEveryTick(() -> {
			if (tick[0] < FIRING_TICKS) {
				aim(player, dummy);
				holdTrigger(player, tick[0]++);
				tick(player);
			}
		});
		context.runAtTick(FIRING_TICKS + 10, () -> {
			float damage = dummy.getMaxHealth() - dummy.getHealth();
			double hits = damage / damagePerShot;
			context.assertTrue(player.getInventory().count(Items.ARROW) == 0, Text.literal("player had arrows"));
			context.assertTrue(hits >= minHits - 0.01 && hits <= maxHits + 0.99,
				Text.literal("expected %d to %d hits, got %.1f damage (%.1f hits)".formatted(minHits, maxHits, damage, hits)));
			context.assertTrue(context.getWorld().getEntitiesByClass(PersistentProjectileEntity.class,
				player.getBoundingBox().expand(16), arrow -> arrow.getCommandTags().contains(ShootAbility.BULLET_TAG)).isEmpty(),
				Text.literal("bullets stuck around in blocks"));
			context.complete();
		});
	}
}
