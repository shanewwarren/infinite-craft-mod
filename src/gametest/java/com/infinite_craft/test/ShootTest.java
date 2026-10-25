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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

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

	private static final String FREEZE_RAY = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"infinite_craft:shoot":{kind:"beam",damage:1.0f,color:"aqua",interval:10,effects:[{id:"minecraft:slowness",amplifier:3,seconds:6.0f}]}}}""";

	private static final String FREEZE_BLASTER = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"infinite_craft:shoot":{kind:"bullet",damage:1.0f,color:"aqua",interval:10,effects:[{id:"minecraft:slowness",amplifier:3,seconds:6.0f}]}}}""";

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void freezeRaySlowsAHusk(TestContext context) {
		fireAtMob(context, FREEZE_RAY, false, true);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void freezeRaySparesOwnPet(TestContext context) {
		fireAtMob(context, FREEZE_RAY, true, false);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void freezeBulletSlowsAHusk(TestContext context) {
		fireAtMob(context, FREEZE_BLASTER, false, true);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void freezeBulletSparesOwnPet(TestContext context) {
		fireAtMob(context, FREEZE_BLASTER, true, false);
	}

	@GameTest(maxTicks = FIRING_TICKS + 20)
	public void plainBeamGivesNoEffects(TestContext context) {
		fireAtMob(context, RAY_GUN, false, false);
	}

	/**
	 * Holds the trigger for 3 seconds at a husk, or at the shooter's own tamed wolf, and checks whether it ended up
	 * with slowness IV. Both stand still where the dummy does
	 */
	private static void fireAtMob(TestContext context, String nbt, boolean ownPet, boolean expectSlowness) {
		floor(context);
		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, ThrustTest.parse(context, nbt));
		LivingEntity target;
		if (ownPet) {
			WolfEntity pet = context.spawnEntity(EntityType.WOLF, new Vec3d(4.5, 1, 6.5));
			pet.setTamed(true, true);
			pet.setOwner(player);
			pet.setAiDisabled(true);
			target = pet;
		} else {
			HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(4.5, 1, 6.5));
			husk.setAiDisabled(true);
			target = husk;
		}
		int[] tick = {0};
		context.runAtEveryTick(() -> {
			if (tick[0] < FIRING_TICKS) {
				aim(player, target);
				holdTrigger(player, tick[0]++);
				tick(player);
			}
		});
		context.runAtTick(FIRING_TICKS + 10, () -> {
			StatusEffectInstance slowness = target.getStatusEffect(StatusEffects.SLOWNESS);
			String got = "%s took %.1f damage and has %s".formatted(target.getType().getUntranslatedName(),
				target.getMaxHealth() - target.getHealth(), target.getStatusEffects());
			if (expectSlowness) {
				context.assertTrue(slowness != null && slowness.getAmplifier() == 3, Text.literal("expected slowness IV: " + got));
			} else {
				context.assertTrue(target.getStatusEffects().isEmpty(), Text.literal("expected no effects: " + got));
			}
			context.complete();
		});
	}

	@GameTest(maxTicks = 60)
	public void instantEffectHitsOnce(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, ThrustTest.parse(context, """
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"infinite_craft:shoot":{kind:"beam",damage:0.0f,interval:200,effects:[{id:"minecraft:instant_damage",amplifier:0,seconds:5.0f}]}}}"""));
		PigEntity dummy = dummy(context);
		aim(player, dummy);
		holdTrigger(player, 0);
		context.runAtTick(40, () -> {
			float damage = dummy.getMaxHealth() - dummy.getHealth();
			// instant damage I does 6 once, not 6 every tick of the effect
			context.assertTrue(damage > 0 && damage <= 6.5f, Text.literal("one instant-damage hit did %.1f damage".formatted(damage)));
			context.complete();
		});
	}
}
