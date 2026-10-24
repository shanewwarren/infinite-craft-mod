package com.infinite_craft.ability;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.component.ComponentsAccess;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * `infinite_craft:launch`: a melee hit with the item flings the mob away, like the videos' mega punchers.
 * Claude writes it in the item's components, e.g. {power:2.0f,up:1.0f}
 *
 * @param power how far it flies; 1 is about 10 blocks, 3 about 30
 * @param up    how high it arcs; 0 skims the ground, 1 is a high arc, 2 is mostly straight up
 */
public record LaunchComponent(float power, float up) implements TooltipAppender {

	public LaunchComponent {
		// keep whatever Claude writes playable
		power = MathHelper.clamp(power, 0.2f, 4);
		up = MathHelper.clamp(up, 0, 2);
	}

	public static final Codec<LaunchComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.FLOAT.optionalFieldOf("power", 1.5f).forGetter(LaunchComponent::power),
		Codec.FLOAT.optionalFieldOf("up", 0.5f).forGetter(LaunchComponent::up)
	).apply(instance, LaunchComponent::new));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.launch", "Sends mobs flying").formatted(Formatting.GOLD));
	}
}
