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
 * `infinite_craft:dash`: while the item is worn (or held), pressing jump in mid-air dashes the way you face.
 * Claude writes it in the item's components, e.g. {power:1.0f,charges:1}
 *
 * @param power   how far a dash goes; 1 is about 7 blocks
 * @param charges dashes before you must land again; 2 is a double dash
 */
public record DashComponent(float power, int charges) implements TooltipAppender {

	public DashComponent {
		// keep whatever Claude writes playable
		power = MathHelper.clamp(power, 0.3f, 3);
		charges = MathHelper.clamp(charges, 1, 5);
	}

	public static final Codec<DashComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.FLOAT.optionalFieldOf("power", 1.0f).forGetter(DashComponent::power),
		Codec.INT.optionalFieldOf("charges", 1).forGetter(DashComponent::charges)
	).apply(instance, DashComponent::new));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.dash", "Jump in mid-air to dash").formatted(Formatting.GOLD));
	}
}
