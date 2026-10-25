package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.player;
import static com.infinite_craft.test.CraftedItemUseTest.tick;
import static com.infinite_craft.test.CraftedItemUseTest.use;

import java.util.ArrayList;
import java.util.List;

import com.infinite_craft.ability.TimedAttributes;
import com.infinite_craft.ability.TimedAttributesConsumeEffect;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * A potion with the infinite_craft:attributes consume effect changes the drinker for a while: the Shrink Potion makes
 * the player a quarter size for 30 s. It wears off on time and on death, another drink restarts the time rather than
 * shrinking further, it's never saved, and growing back inside a low tunnel doesn't suffocate
 */
public class TimedAttributesTest {

	static final String SHRINK_POTION = """
		{id:"infinite_craft:custom_crafted_item",count:3i,components:{"minecraft:item_model":"minecraft:potion","minecraft:item_name":{text:"Shrink Potion",color:"light_purple"},"minecraft:max_stack_size":16,"minecraft:consumable":{consume_seconds:0.8f,animation:"drink",sound:"minecraft:entity.generic.drink",on_consume_effects:[{type:"infinite_craft:attributes",seconds:30.0f,modifiers:[{type:"minecraft:scale",id:"infinite_craft:shrink",amount:-0.75d,operation:"add_multiplied_total"}]}]}}}""";

	private static final Identifier SHRINK = Identifier.of("infinite_craft", "shrink");

	/** Drinks the item in the main hand the way a player does: hold use until it's consumed */
	static void drink(PlayerEntity player, ItemStack potion) {
		player.setStackInHand(Hand.MAIN_HAND, potion);
		use(player, Hand.MAIN_HAND);
		for (int t = 0; t < 40 && player.isUsingItem(); t++) {
			tick(player);
		}
	}

	private static void ticks(PlayerEntity player, int count) {
		for (int t = 0; t < count; t++) tick(player);
	}

	private static double scale(PlayerEntity player) {
		return player.getAttributeValue(EntityAttributes.SCALE);
	}

	@GameTest(maxTicks = 20)
	public void shrinkPotionShrinksThenWearsOff(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		drink(player, ThrustTest.parse(context, SHRINK_POTION));
		context.assertTrue(Math.abs(scale(player) - 0.25) < 0.01, Text.literal("after drinking, scale " + scale(player)));
		ticks(player, 30 * 20 + 5);
		context.assertTrue(scale(player) == 1.0, Text.literal("30 s later, scale " + scale(player)));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void shrinkWearsOffOnDeath(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		drink(player, ThrustTest.parse(context, SHRINK_POTION));
		context.assertTrue(scale(player) < 1.0, Text.literal("drinking didn't shrink: scale " + scale(player)));
		player.kill(context.getWorld());
		context.assertTrue(scale(player) == 1.0 && !TimedAttributes.isActive(player),
			Text.literal("after dying, scale %s, still active %s".formatted(scale(player), TimedAttributes.isActive(player))));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void secondDrinkRefreshesInsteadOfStacking(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		ItemStack potions = ThrustTest.parse(context, SHRINK_POTION);
		drink(player, potions);
		ticks(player, 20 * 20);
		drink(player, player.getMainHandStack());
		context.assertTrue(Math.abs(scale(player) - 0.25) < 0.01, Text.literal("after a second drink, scale " + scale(player)));
		ticks(player, 15 * 20);
		context.assertTrue(Math.abs(scale(player) - 0.25) < 0.01,
			Text.literal("15 s after the second drink (35 s after the first), scale " + scale(player)));
		ticks(player, 15 * 20 + 5);
		context.assertTrue(scale(player) == 1.0, Text.literal("30 s after the second drink, scale " + scale(player)));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void timedModifierIsNeverPersistent(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		drink(player, ThrustTest.parse(context, SHRINK_POTION));
		var instance = player.getAttributeInstance(EntityAttributes.SCALE);
		context.assertTrue(instance.hasModifier(SHRINK), Text.literal("drinking put no shrink modifier on"));
		context.assertTrue(instance.getPersistentModifiers().stream().noneMatch(m -> m.id().equals(SHRINK)),
			Text.literal("the shrink would be saved with the player: " + instance.getPersistentModifiers()));
		context.complete();
	}

	// three times as tall: even crawling, the head is 1.2 blocks up
	private static final String GIANT_POTION = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:consumable":{consume_seconds:0.8f,on_consume_effects:[{type:"infinite_craft:attributes",seconds:10.0f,modifiers:[{type:"minecraft:scale",id:"infinite_craft:grow",amount:2.0d,operation:"add_multiplied_total"}]}]}}}""";

	@GameTest(maxTicks = 20)
	public void growingUnderALowCeilingDoesNotSuffocate(TestContext context) {
		floor(context);
		// a 1-block-high room, like a tunnel he dug
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlockState(new BlockPos(x, 2, z), Blocks.STONE);
			}
		}
		PlayerEntity player = player(context);
		ticks(player, 5);
		float before = player.getHealth();
		drink(player, ThrustTest.parse(context, GIANT_POTION));
		context.assertTrue(scale(player) > 2.9, Text.literal("the giant potion didn't grow: scale " + scale(player)));
		ticks(player, 10 * 20 + 40);
		context.assertTrue(player.getHealth() == before,
			Text.literal("growing under the ceiling hurt: health %s to %s, pose %s".formatted(before, player.getHealth(), player.getPose())));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void badSecondsAndTooManyModifiersAreClamped(TestContext context) {
		List<TimedAttributesConsumeEffect.Modifier> nine = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			nine.add(new TimedAttributesConsumeEffect.Modifier(EntityAttributes.SCALE,
				new EntityAttributeModifier(Identifier.of("infinite_craft", "m" + i), 0.1, EntityAttributeModifier.Operation.ADD_VALUE)));
		}
		TimedAttributesConsumeEffect effect = new TimedAttributesConsumeEffect(nine, 99999f);
		context.assertTrue(effect.seconds() == 600 && effect.modifiers().size() == 6,
			Text.literal("clamped to %s s and %d modifiers".formatted(effect.seconds(), effect.modifiers().size())));
		context.assertTrue(new TimedAttributesConsumeEffect(nine, -3f).seconds() == 1, Text.literal("negative seconds not clamped to 1"));
		// a parsed potion keeps its effect
		ItemStack potion = ThrustTest.parse(context, SHRINK_POTION);
		context.assertTrue(potion.get(DataComponentTypes.CONSUMABLE).onConsumeEffects().stream().anyMatch(TimedAttributesConsumeEffect.class::isInstance),
			Text.literal("the Shrink Potion parsed without its timed attributes"));
		context.complete();
	}
}
