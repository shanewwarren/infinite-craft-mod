package com.infinite_craft.playtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.infinite_craft.TestBootstrap;
import com.infinite_craft.ai.AiPrompt;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;

/** The ruler for "these two items play the same": what an item does, ignoring its name, looks and numbers */
public class BehaviorSignatureTest {

	private static RegistryWrapper.WrapperLookup registries;

	@BeforeAll
	static void bootstrap() {
		registries = TestBootstrap.registries();
	}

	private static final String PLASMA_BLASTER = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_name":"Plasma Blaster","infinite_craft:shoot":{kind:"beam",damage:6.0f}}}""";

	private static String of(String nbt) {
		try {
			return BehaviorSignature.of(nbt);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static String example(String contains) {
		return AiPrompt.POWER_EXAMPLES.stream().filter(e -> e.contains(contains)).findFirst().orElseThrow().strip();
	}

	@Test
	void sameGunDifferentNameAndNumbersMatch() {
		assertEquals("shoot(beam)", of(PLASMA_BLASTER));
		assertEquals(of(PLASMA_BLASTER), of("""
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_name":"Laser Pistol","infinite_craft:shoot":{damage:9.0f,color:"red",interval:6}}}"""));
	}

	@Test
	void rocketDiffersFromBeam() {
		assertEquals("shoot(rocket,explode)", of(example("kind:\"rocket\"")));
	}

	@Test
	void heldSpeedBeamDiffersFromPlainBeam() {
		assertEquals("attributes(movement_speed) + shoot(beam)", of("""
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"infinite_craft:shoot":{kind:"beam"},"minecraft:attribute_modifiers":[{type:"minecraft:movement_speed",id:"infinite_craft:zoom",amount:0.2d,operation:"add_multiplied_base",slot:"mainhand"}]}}"""));
	}

	@Test
	void swordsOfDifferentMaterialMatch() {
		assertEquals("base(sword) + enchantments(fire_aspect)", of("""
			{id:"minecraft:diamond_sword",count:1i,components:{"minecraft:enchantments":{"minecraft:fire_aspect":2,"minecraft:sharpness":3}}}"""));
		assertEquals("base(sword) + enchantments(fire_aspect)", of("""
			{id:"minecraft:iron_sword",count:1i,components:{"minecraft:enchantments":{"minecraft:fire_aspect":1}}}"""));
	}

	@Test
	void statsDontCount() {
		// more armor, a faster swing or sharpness changes numbers, not what the item does
		assertEquals("base(chestplate)", of("""
			{id:"minecraft:diamond_chestplate",count:1i,components:{"minecraft:enchantments":{"minecraft:protection":4,"minecraft:unbreaking":3},"minecraft:attribute_modifiers":[{type:"minecraft:armor",id:"infinite_craft:a",amount:10.0d,operation:"add_value",slot:"chest"},{type:"minecraft:armor_toughness",id:"infinite_craft:b",amount:3.0d,operation:"add_value",slot:"chest"}]}}"""));
		assertEquals("base(sword)", of("""
			{id:"minecraft:iron_sword",count:1i,components:{"minecraft:enchantments":{"minecraft:sharpness":5},"minecraft:attribute_modifiers":[{type:"minecraft:attack_damage",id:"infinite_craft:a",amount:9.0d,operation:"add_value",slot:"mainhand"},{type:"minecraft:attack_speed",id:"infinite_craft:b",amount:-2.0d,operation:"add_value",slot:"mainhand"}]}}"""));
	}

	@Test
	void meleeWeaponsSayMelee() {
		assertEquals("melee", of(example("Green Lightsaber")));
	}

	@Test
	void drinkListsItsEffects() {
		assertEquals("consumable(jump_boost,speed)", of("""
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:consumable":{on_consume_effects:[{type:"minecraft:apply_effects",effects:[{id:"minecraft:speed",duration:600},{id:"minecraft:jump_boost",duration:600}]},{type:"minecraft:play_sound",sound:"minecraft:entity.generic.burp"}]}}}"""));
	}

	@Test
	void blinkOrbIsTeleport() {
		assertEquals("consumable(teleport)", of(example("Blink Orb")));
	}

	@Test
	void plainVanillaHasNoSignature() {
		assertEquals("", of("{id:\"minecraft:gunpowder\",count:5i}"));
	}

	@Test
	void craftIdAndLooksDontCount() {
		assertEquals("shoot(beam)", of("""
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"infinite_craft:craft_id":"ab12cd34","minecraft:item_model":"infinite_craft:ray_gun","minecraft:lore":[{text:"zap"}],"minecraft:max_stack_size":1,"minecraft:use_cooldown":{seconds:1.0f},"infinite_craft:shoot":{}}}"""));
	}

	@Test
	void everyPromptExampleHasASignature() {
		for (String example : AiPrompt.POWER_EXAMPLES) {
			assertFalse(of(example.strip()).isEmpty(), example);
		}
	}

	@Test
	void stackAndNbtAgree() throws Exception {
		String tank = example("infinite_craft:vehicle");
		ItemStack stack = ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), StringNbtReader.readCompound(tank)).getOrThrow();
		assertEquals(of(tank), BehaviorSignature.of(stack, registries));
		assertEquals("shoot(rocket,explode) + vehicle(tank)", of(tank));
	}

	private static final String SHRINK_POTION = """
		{id:"infinite_craft:custom_crafted_item",count:3i,components:{"minecraft:consumable":{consume_seconds:0.8f,on_consume_effects:[{type:"infinite_craft:attributes",seconds:30.0f,modifiers:[{type:"minecraft:scale",id:"infinite_craft:shrink",amount:-0.75d,operation:"add_multiplied_total"}]}]}}}""";

	@Test
	void shrinkPotionIsTimedScale() {
		assertEquals("consumable(timed_scale)", of(SHRINK_POTION));
	}

	@Test
	void timedStatsDontCount() {
		// a potion of bigger numbers is still just a drink
		assertEquals("consumable", of("""
			{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:consumable":{on_consume_effects:[{type:"infinite_craft:attributes",modifiers:[{type:"minecraft:armor",id:"infinite_craft:a",amount:4.0d,operation:"add_value"}]}]}}}"""));
	}
}
