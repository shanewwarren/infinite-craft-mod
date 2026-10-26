package com.infinite_craft.ability;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.component.ComponentsAccess;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * `infinite_craft:aura`: while the item is worn (or held), the wearer keeps status effects, glows with particles,
 * and can hurt monsters close by. Claude writes it in the item's components, e.g.
 * {effects:[{id:"minecraft:regeneration",amplifier:0}],particle:"minecraft:end_rod",damage:2.0f,radius:4.0f}
 *
 * @param effects  status effects kept on the wearer while it's on; they wear off within a couple of seconds when it comes off
 * @param particle a particle drawn around the wearer, like "minecraft:end_rod" or "minecraft:flame"; colored dust when left out
 * @param color    the dust's color when there is no particle; a color name, "#RRGGBB" or an RGB int
 * @param damage   damage each second to monsters within radius; never to animals, pets or players
 * @param radius   blocks around the wearer that damage reaches
 */
public record AuraComponent(List<Effect> effects, Optional<Identifier> particle, int color, float damage, float radius)
	implements TooltipAppender {

	public record Effect(RegistryEntry<StatusEffect> id, int amplifier) {

		public Effect {
			// resistance V would make the wearer invulnerable
			amplifier = MathHelper.clamp(amplifier, 0, 3);
		}

		public static final Codec<Effect> CODEC = Strict.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
			StatusEffect.ENTRY_CODEC.fieldOf("id").forGetter(Effect::id),
			Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier)
		).apply(instance, Effect::new)));
	}

	public AuraComponent {
		// keep whatever Claude writes playable
		effects = effects.stream().limit(6).toList();
		damage = MathHelper.clamp(damage, 0, 10);
		radius = MathHelper.clamp(radius, 1, 8);
	}

	public static final Codec<AuraComponent> CODEC = Strict.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
		Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(AuraComponent::effects),
		Identifier.CODEC.optionalFieldOf("particle").forGetter(AuraComponent::particle),
		ShootComponent.COLOR_CODEC.optionalFieldOf("color", 0xFFD700).forGetter(AuraComponent::color),
		Codec.FLOAT.optionalFieldOf("damage", 0.0f).forGetter(AuraComponent::damage),
		Codec.FLOAT.optionalFieldOf("radius", 4.0f).forGetter(AuraComponent::radius)
	).apply(instance, AuraComponent::new)));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.aura", "Aura while worn").withColor(color));
		for (Effect effect : effects) {
			Text name = Text.translatable(effect.id().value().getTranslationKey());
			textConsumer.accept(Text.literal(" ").append(effect.amplifier() > 0
				? Text.translatable("potion.withAmplifier", name, Text.translatable("potion.potency." + effect.amplifier()))
				: name).formatted(Formatting.BLUE));
		}
		if (damage > 0) {
			textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.aura.damage", "Hurts monsters within %s blocks",
				"%.0f".formatted(radius)).formatted(Formatting.RED));
		}
	}
}
