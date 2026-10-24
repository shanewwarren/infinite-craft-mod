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
 * `infinite_craft:thrust`: while the item is worn (or held), holding jump flies up and holding forward speeds you along.
 * Claude writes it in the item's components, e.g. {power:1.0f,speed:1.5f}
 *
 * @param power how hard it lifts; 1 climbs about 7 blocks a second
 * @param speed how much holding forward speeds you up in the air; 0 for straight up and down
 */
public record ThrustComponent(float power, float speed) implements TooltipAppender {

	public ThrustComponent {
		// keep whatever Claude writes playable
		power = MathHelper.clamp(power, 0.2f, 3);
		speed = MathHelper.clamp(speed, 0, 3);
	}

	public static final Codec<ThrustComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.FLOAT.optionalFieldOf("power", 1.0f).forGetter(ThrustComponent::power),
		Codec.FLOAT.optionalFieldOf("speed", 1.0f).forGetter(ThrustComponent::speed)
	).apply(instance, ThrustComponent::new));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.thrust", "Hold jump to fly").formatted(Formatting.GOLD));
	}
}
