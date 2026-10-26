package com.infinite_craft.ability;

import java.util.List;

import com.infinite_craft.InfiniteCraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.ConsumeEffect;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * `infinite_craft:attributes`, a consume effect: attribute modifiers for a while, which vanilla potions can't do.
 * Claude writes it in on_consume_effects, e.g. a Shrink Potion:
 * {type:"infinite_craft:attributes",seconds:30.0f,modifiers:[{type:"minecraft:scale",id:"infinite_craft:shrink",amount:-0.75d,operation:"add_multiplied_total"}]}
 *
 * @param modifiers the attribute_modifiers entry format without a slot
 * @param seconds   how long they last; drinking another restarts the time (see TimedAttributes)
 */
public record TimedAttributesConsumeEffect(List<Modifier> modifiers, float seconds) implements ConsumeEffect {

	public record Modifier(RegistryEntry<EntityAttribute> type, EntityAttributeModifier modifier) {

		public static final Codec<Modifier> CODEC = Strict.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
			EntityAttribute.CODEC.fieldOf("type").forGetter(Modifier::type),
			EntityAttributeModifier.MAP_CODEC.forGetter(Modifier::modifier)
		).apply(instance, Modifier::new)));
	}

	public TimedAttributesConsumeEffect {
		// keep whatever Claude writes playable
		modifiers = modifiers.stream().limit(8).toList();
		seconds = MathHelper.clamp(seconds, 1, 600);
	}

	public static final MapCodec<TimedAttributesConsumeEffect> CODEC = Strict.map(RecordCodecBuilder.mapCodec(instance -> instance.group(
		Modifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(TimedAttributesConsumeEffect::modifiers),
		Codec.FLOAT.optionalFieldOf("seconds", 30.0f).forGetter(TimedAttributesConsumeEffect::seconds)
	).apply(instance, TimedAttributesConsumeEffect::new)), "type");

	public static final ConsumeEffect.Type<TimedAttributesConsumeEffect> TYPE = Registry.register(Registries.CONSUME_EFFECT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "attributes"), new ConsumeEffect.Type<>(CODEC, PacketCodecs.registryCodec(CODEC.codec())));

	@Override
	public ConsumeEffect.Type<TimedAttributesConsumeEffect> getType() {
		return TYPE;
	}

	@Override
	public boolean onConsume(World world, ItemStack stack, LivingEntity user) {
		TimedAttributes.apply(user, modifiers, Math.round(seconds * 20));
		return true;
	}
}
