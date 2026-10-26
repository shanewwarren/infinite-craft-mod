package com.infinite_craft.ability;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.component.ComponentsAccess;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipAppender;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * `infinite_craft:summon`: right-click calls helpers that fight monsters for the player and never hurt him, his pets,
 * villagers or each other (see {@link Summons}). Claude writes it in the item's components, e.g.
 * {entity:"minecraft:skeleton",count:5,seconds:60,gear:{mainhand:"minecraft:bow",head:"minecraft:iron_helmet"}}
 *
 * @param entity  the mob, from {@link #ALLOWED}; anything else is a wolf
 * @param count   how many each use
 * @param seconds how long they stay
 * @param name    a name shown above each one
 * @param gear    what they hold and wear
 * @param size    their scale: 0.5 is half size, 2 twice as big
 * @param color   the dust they arrive and leave in; a color name, "#RRGGBB" or an RGB int
 */
public record SummonComponent(EntityType<?> entity, int count, int seconds, Optional<String> name, Optional<Gear> gear, float size,
	Optional<Integer> color) implements TooltipAppender {

	/**
	 * Helpers only: mobs that fight and can be steered with target goals. No bosses, nothing that explodes or griefs,
	 * no Brain-AI mobs (goats, axolotls, piglins ignore target goals), no blazes (fireballs set blocks alight)
	 */
	public static final Set<EntityType<?>> ALLOWED = Set.of(EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM,
		EntityType.BEE, EntityType.FOX, EntityType.POLAR_BEAR, EntityType.LLAMA, EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
		EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.VEX);

	public record Gear(Optional<Item> mainhand, Optional<Item> head) {

		public static final Codec<Gear> CODEC = Strict.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
			Registries.ITEM.getCodec().lenientOptionalFieldOf("mainhand").forGetter(Gear::mainhand),
			Registries.ITEM.getCodec().lenientOptionalFieldOf("head").forGetter(Gear::head)
		).apply(instance, Gear::new)));
	}

	public SummonComponent {
		// keep whatever Claude writes playable
		if (!ALLOWED.contains(entity)) entity = EntityType.WOLF;
		count = MathHelper.clamp(count, 1, 6);
		seconds = MathHelper.clamp(seconds, 10, 180);
		size = MathHelper.clamp(size, 0.5f, 2);
	}

	// an unknown id is a wolf too, rather than dropping the whole component
	private static final Codec<EntityType<?>> ENTITY_CODEC = Identifier.CODEC.xmap(
		id -> Registries.ENTITY_TYPE.getOptionalValue(id).orElse(EntityType.WOLF), Registries.ENTITY_TYPE::getId);

	public static final Codec<SummonComponent> CODEC = Strict.of(RecordCodecBuilder.mapCodec(instance -> instance.group(
		ENTITY_CODEC.optionalFieldOf("entity", EntityType.WOLF).forGetter(SummonComponent::entity),
		Codec.INT.optionalFieldOf("count", 1).forGetter(SummonComponent::count),
		Codec.INT.optionalFieldOf("seconds", 60).forGetter(SummonComponent::seconds),
		Codec.STRING.optionalFieldOf("name").forGetter(SummonComponent::name),
		Gear.CODEC.optionalFieldOf("gear").forGetter(SummonComponent::gear),
		Codec.FLOAT.optionalFieldOf("size", 1.0f).forGetter(SummonComponent::size),
		ShootComponent.COLOR_CODEC.optionalFieldOf("color").forGetter(SummonComponent::color)
	).apply(instance, SummonComponent::new)));

	@Override
	public void appendTooltip(Item.TooltipContext context, Consumer<Text> textConsumer, TooltipType type, ComponentsAccess components) {
		textConsumer.accept(Text.translatableWithFallback("item.infinite_craft.summon", "Summons %s %s for %s s", count,
			entity.getName(), seconds).formatted(Formatting.GREEN));
	}
}
