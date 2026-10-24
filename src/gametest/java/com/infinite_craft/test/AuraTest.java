package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.player;
import static com.infinite_craft.test.CraftedItemUseTest.tick;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Items with infinite_craft:aura keep effects on the wearer while worn (and take them back when removed),
 * regeneration from an aura really heals, and an aura's damage hurts monsters but not animals or pets
 */
public class AuraTest {

	static final String GOD_ARMOR = """
		{id:"minecraft:golden_chestplate",count:1i,components:{"minecraft:item_name":"God Armor","infinite_craft:aura":{effects:[{id:"minecraft:regeneration"},{id:"minecraft:night_vision"},{id:"minecraft:fire_resistance"}],particle:"minecraft:end_rod",damage:2.0f,radius:4.0f}}}""";

	@GameTest(maxTicks = 20)
	public void wornAuraKeepsEffectsAndTakesThemBack(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		player.equipStack(EquipmentSlot.CHEST, ThrustTest.parse(context, GOD_ARMOR));
		int lowestNightVision = Integer.MAX_VALUE;
		for (int t = 0; t < 1200; t++) {
			tick(player);
			if (t > 20) {
				StatusEffectInstance nightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
				lowestNightVision = Math.min(lowestNightVision, nightVision == null ? 0 : nightVision.getDuration());
			}
		}
		boolean allOn = player.hasStatusEffect(StatusEffects.REGENERATION) && player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)
			&& player.hasStatusEffect(StatusEffects.NIGHT_VISION);
		context.assertTrue(allOn, Text.literal("wearing it for a minute, effects were " + player.getStatusEffects()));
		// night vision flickers the screen under 200 ticks left
		context.assertTrue(lowestNightVision >= 200, Text.literal("night vision dropped to %d ticks while worn".formatted(lowestNightVision)));

		player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
		for (int t = 0; t < 20; t++) tick(player);
		context.assertTrue(player.getStatusEffects().isEmpty(), Text.literal("a second after taking it off: " + player.getStatusEffects()));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void auraRegenerationHeals(TestContext context) {
		context.assertTrue(healedIn5s(context, GOD_ARMOR) >= 1.5f, Text.literal("aura regeneration healed %.1f in 5s".formatted(healedIn5s(context, GOD_ARMOR))));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void withoutAnAuraNoHealing(TestContext context) {
		float healed = healedIn5s(context, null);
		context.assertTrue(healed < 0.5f, Text.literal("healed %.1f in 5s without an aura".formatted(healed)));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void auraInTheInventoryDoesNothing(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		player.getInventory().setStack(9, ThrustTest.parse(context, GOD_ARMOR));
		for (int t = 0; t < 40; t++) tick(player);
		context.assertTrue(player.getStatusEffects().isEmpty(), Text.literal("an aura in the backpack gave " + player.getStatusEffects()));
		context.complete();
	}

	@GameTest(maxTicks = 80)
	public void auraHurtsMonstersButNotAnimalsOrPets(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		player.equipStack(EquipmentSlot.CHEST, ThrustTest.parse(context, GOD_ARMOR));
		// a husk: a zombie that doesn't burn in daylight, which would hurt it without the aura
		HuskEntity husk = mob(context, EntityType.HUSK, 6.5, 4.5);
		PigEntity pig = mob(context, EntityType.PIG, 2.5, 4.5);
		WolfEntity pet = mob(context, EntityType.WOLF, 4.5, 6.5);
		pet.setTamed(true, true);
		pet.setOwner(player);
		context.runAtEveryTick(() -> tick(player));
		context.runAtTick(65, () -> {
			float huskHurt = husk.getMaxHealth() - husk.getHealth();
			context.assertTrue(huskHurt >= 4, Text.literal("husk 2 blocks away took %.1f damage in 3s".formatted(huskHurt)));
			context.assertTrue(pig.getHealth() == pig.getMaxHealth(), Text.literal("the aura hurt a pig"));
			context.assertTrue(pet.getHealth() == pet.getMaxHealth(), Text.literal("the aura hurt the wearer's wolf"));
			context.complete();
		});
	}

	/** Health regained in 5s by a hungry player (too hungry to heal naturally) at half health */
	private static float healedIn5s(TestContext context, String chest) {
		floor(context);
		PlayerEntity player = player(context);
		if (chest != null) player.equipStack(EquipmentSlot.CHEST, ThrustTest.parse(context, chest));
		player.getHungerManager().setFoodLevel(10);
		player.setHealth(10);
		for (int t = 0; t < 100; t++) tick(player);
		return player.getHealth() - 10;
	}

	private static <T extends LivingEntity> T mob(TestContext context, EntityType<T> type, double x, double z) {
		T mob = context.spawnEntity(type, new Vec3d(x, 1, z));
		if (mob instanceof net.minecraft.entity.mob.MobEntity ai) ai.setAiDisabled(true);
		return mob;
	}
}
