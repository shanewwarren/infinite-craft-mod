package com.infinite_craft.ability;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
 * @param kind     beam: an instant ray that hits the first mob in line; bullet: a fast arrow-like projectile;
 *                 rocket: a slower flying rocket that always explodes
 * @param damage   per hit
 * @param color    beam and muzzle flash color, a color name, "#RRGGBB" or an RGB int
 * @param interval ticks between shots (20 = once a second); holding right click repeats every 4 ticks, so 4 is the fastest
 * @param count    shots per trigger pull, like a shotgun
 * @param spread   random aim error in degrees
 * @param fire     sets what it hits on fire
 * @param explode  explosion power where a shot lands (2 is a fireball, 3 a creeper, 4 TNT); 0 is none.
 *                 Hurts mobs but never blocks, the shooter or their pets
 * @param lightning lightning strikes where a shot lands, without starting fires
 * @param sound    sound when firing; defaults by kind
 */
public record ShootComponent(Kind kind, float damage, int color, int interval, int count, float spread, boolean fire,
	float explode, boolean lightning, Optional<RegistryEntry<SoundEvent>> sound)
	implements TooltipAppender {

	public ShootComponent {
		// keep whatever Claude writes playable
		damage = MathHelper.clamp(damage, 0, 50);
		interval = MathHelper.clamp(interval, 1, 200);
		count = MathHelper.clamp(count, 1, 10);
		spread = MathHelper.clamp(spread, 0, 45);
		// bigger blasts hurt the player's own mobs from too far away and lag rapid fire
		explode = MathHelper.clamp(explode, 0, 4);
		if (kind == Kind.ROCKET && explode == 0) {
			explode = 2;
		}
	}

	// Minecraft's text colors only have 16 names; Claude also writes everyday ones like "orange"
	private static final Map<String, Integer> MORE_COLOR_NAMES = Map.ofEntries(
		Map.entry("orange", 0xFF8800), Map.entry("purple", 0x9933FF), Map.entry("violet", 0x8800FF), Map.entry("pink", 0xFF66CC),
		Map.entry("magenta", 0xFF00FF), Map.entry("cyan", 0x00FFFF), Map.entry("teal", 0x008080), Map.entry("lime", 0x66FF33),
		Map.entry("brown", 0x8B5A2B), Map.entry("silver", 0xC0C0C0), Map.entry("crimson", 0xDC143C)
	);

	private static final Codec<Integer> COLOR_NAME_CODEC = Codec.STRING.comapFlatMap(
		name -> TextColor.parse(name).result().map(TextColor::getRgb)
			.or(() -> Optional.ofNullable(MORE_COLOR_NAMES.get(name.toLowerCase(Locale.ROOT))))
			.map(DataResult::success)
			.orElseGet(() -> DataResult.error(() -> "Unknown color " + name, 0xFF3030)),
		rgb -> "#%06X".formatted(rgb));

	private static final Codec<Integer> COLOR_CODEC = Codec.either(Codec.INT, COLOR_NAME_CODEC)
		.xmap(either -> either.map(rgb -> rgb, rgb -> rgb), Either::left);

	public static final Codec<ShootComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Kind.CODEC.optionalFieldOf("kind", Kind.BEAM).forGetter(ShootComponent::kind),
		Codec.FLOAT.optionalFieldOf("damage", 4.0f).forGetter(ShootComponent::damage),
		COLOR_CODEC.optionalFieldOf("color", 0xFF3030).forGetter(ShootComponent::color),
		Codec.INT.optionalFieldOf("interval", 10).forGetter(ShootComponent::interval),
		Codec.INT.optionalFieldOf("count", 1).forGetter(ShootComponent::count),
		Codec.FLOAT.optionalFieldOf("spread", 0.0f).forGetter(ShootComponent::spread),
		Codec.BOOL.optionalFieldOf("fire", false).forGetter(ShootComponent::fire),
		Codec.FLOAT.optionalFieldOf("explode", 0.0f).forGetter(ShootComponent::explode),
		Codec.BOOL.optionalFieldOf("lightning", false).forGetter(ShootComponent::lightning),
		SoundEvent.ENTRY_CODEC.optionalFieldOf("sound").forGetter(ShootComponent::sound)
	).apply(instance, ShootComponent::new));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		float shotsPerSecond = 20.0f / Math.max(interval, 4);
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot." + kind.asString(), switch (kind) {
				case BEAM -> "Shoots a beam";
				case BULLET -> "Shoots bullets";
				case ROCKET -> "Shoots rockets";
			})
			.withColor(color));
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot.stats", "%s damage, %s shots/s, no ammo needed",
				"%.0f".formatted(damage * count), "%.1f".formatted(shotsPerSecond))
			.formatted(Formatting.GRAY));
		if (fire) {
			textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot.fire", "Sets targets on fire").formatted(Formatting.GOLD));
		}
		if (explode > 0) {
			textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot.explode", "Explodes where it hits").formatted(Formatting.RED));
		}
		if (lightning) {
			textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.shoot.lightning", "Calls down lightning").formatted(Formatting.YELLOW));
		}
	}

	public enum Kind implements StringIdentifiable {
		BEAM("beam"),
		BULLET("bullet"),
		ROCKET("rocket");

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
