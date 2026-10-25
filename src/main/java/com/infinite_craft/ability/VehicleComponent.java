package com.infinite_craft.ability;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.component.ComponentsAccess;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

/**
 * `infinite_craft:vehicle`: using the item on the ground places a vehicle you can ride (see VehicleEntity).
 * Claude writes it in the item's components, e.g. {body:"tank",speed:6.0f,health:120.0f,color:"#556B2F"}.
 * The item's infinite_craft:shoot, if any, is the vehicle's gun; it fires only from the vehicle, not the hand
 *
 * @param body   tank: turns in place, turret aims where the rider looks; car: steers while moving
 * @param speed  top speed in blocks per second (running is about 5.6)
 * @param health the vehicle's hit points
 * @param color  hull or paint color, a color name, "#RRGGBB" or an RGB int
 * @param size   scale of the whole vehicle, its hitbox and its gun
 */
public record VehicleComponent(Body body, float speed, float health, int color, float size) implements TooltipAppender {

	public VehicleComponent {
		// keep whatever Claude writes drivable
		speed = MathHelper.clamp(speed, 2, 20);
		health = MathHelper.clamp(health, 10, 300);
		size = MathHelper.clamp(size, 0.5f, 2);
	}

	/** A tank with every default, for a vehicle whose item has lost its component */
	public static final VehicleComponent DEFAULT = of(Body.TANK, Optional.empty(), Optional.empty(), Optional.empty(), 1);

	private static VehicleComponent of(Body body, Optional<Float> speed, Optional<Float> health, Optional<Integer> color, float size) {
		return new VehicleComponent(body, speed.orElse(body.speed), health.orElse(body.health), color.orElse(body.color), size);
	}

	public static final Codec<VehicleComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Body.CODEC.optionalFieldOf("body", Body.TANK).forGetter(VehicleComponent::body),
		Codec.FLOAT.optionalFieldOf("speed").forGetter(vehicle -> Optional.of(vehicle.speed())),
		Codec.FLOAT.optionalFieldOf("health").forGetter(vehicle -> Optional.of(vehicle.health())),
		ShootComponent.COLOR_CODEC.optionalFieldOf("color").forGetter(vehicle -> Optional.of(vehicle.color())),
		Codec.FLOAT.optionalFieldOf("size", 1.0f).forGetter(VehicleComponent::size)
	).apply(instance, VehicleComponent::of));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.vehicle." + body.asString(), switch (body) {
				case TANK -> "Tank: place it and climb in";
				case CAR -> "Car: place it and climb in";
			})
			.withColor(color));
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.vehicle.stats", "%s blocks/s, %s health",
				"%.0f".formatted(speed), "%.0f".formatted(health))
			.formatted(Formatting.GRAY));
	}

	public enum Body implements StringIdentifiable {
		TANK("tank", 6, 100, 0x556B2F),
		CAR("car", 12, 40, 0xC0392B);

		public static final Codec<Body> CODEC = StringIdentifiable.createCodec(Body::values);

		private final String name;
		final float speed;
		final float health;
		final int color;

		Body(String name, float speed, float health, int color) {
			this.name = name;
			this.speed = speed;
			this.health = health;
			this.color = color;
		}

		@Override
		public String asString() {
			return name;
		}
	}
}
