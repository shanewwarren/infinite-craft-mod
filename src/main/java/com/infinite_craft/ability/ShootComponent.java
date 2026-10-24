package com.infinite_craft.ability;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.component.ComponentsAccess;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

/**
 * `infinite_craft:shoot`: the item fires while right click is held, with no ammo.
 * Claude writes it in the item's components, e.g. {kind:"beam",damage:6.0f,color:"green",interval:10}
 *
 * @param kind     beam: an instant ray that hits the first mob in line; bullet: a fast arrow-like projectile
 * @param damage   per hit
 * @param color    beam and muzzle flash color, a color name, "#RRGGBB" or an RGB int
 * @param interval ticks between shots (20 = once a second); holding right click repeats every 4 ticks, so 4 is the fastest
 * @param count    shots per trigger pull, like a shotgun
 * @param spread   random aim error in degrees
 * @param fire     sets what it hits on fire
 * @param sound    sound when firing; defaults by kind
 */
public record ShootComponent(Kind kind, float damage, int color, int interval, int count, float spread, boolean fire,
	Optional<RegistryEntry<SoundEvent>> sound)
	implements TooltipAppender {

	public ShootComponent {
		// keep whatever Claude writes playable
		damage = MathHelper.clamp(damage, 0, 50);
		interval = MathHelper.clamp(interval, 1, 200);
		count = MathHelper.clamp(count, 1, 10);
		spread = MathHelper.clamp(spread, 0, 45);
	}

	private static final Codec<Integer> COLOR_CODEC = Codec.either(Codec.INT, TextColor.CODEC)
		.xmap(either -> either.map(rgb -> rgb, TextColor::getRgb), Either::left);

	public static final Codec<ShootComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Kind.CODEC.optionalFieldOf("kind", Kind.BEAM).forGetter(ShootComponent::kind),
		Codec.FLOAT.optionalFieldOf("damage", 4.0f).forGetter(ShootComponent::damage),
		COLOR_CODEC.optionalFieldOf("color", 0xFF3030).forGetter(ShootComponent::color),
		Codec.INT.optionalFieldOf("interval", 10).forGetter(ShootComponent::interval),
		Codec.INT.optionalFieldOf("count", 1).forGetter(ShootComponent::count),
		Codec.FLOAT.optionalFieldOf("spread", 0.0f).forGetter(ShootComponent::spread),
		Codec.BOOL.optionalFieldOf("fire", false).forGetter(ShootComponent::fire),
		SoundEvent.ENTRY_CODEC.optionalFieldOf("sound").forGetter(ShootComponent::sound)
	).apply(instance, ShootComponent::new));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		float shotsPerSecond = 20.0f / Math.max(interval, 4);
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot." + kind.asString(),
				kind == Kind.BEAM ? "Shoots a beam" : "Shoots bullets")
			.withColor(color));
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot.stats", "%s damage, %s shots/s, no ammo needed",
				"%.0f".formatted(damage * count), "%.1f".formatted(shotsPerSecond))
			.formatted(Formatting.GRAY));
		if (fire) {
			textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot.fire", "Sets targets on fire").formatted(Formatting.GOLD));
		}
	}

	public enum Kind implements StringIdentifiable {
		BEAM("beam"),
		BULLET("bullet");

		public static final Codec<Kind> CODEC = StringIdentifiable.createCodec(Kind::values);

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}
}
