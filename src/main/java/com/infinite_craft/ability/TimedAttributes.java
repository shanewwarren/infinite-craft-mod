package com.infinite_craft.ability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * Attribute modifiers that come off after a while, from {@link TimedAttributesConsumeEffect} (a Shrink Potion).
 * They're added as temporary modifiers, which Minecraft never saves, so logging out or a server stop clears them,
 * and dying takes them off too. The same modifier again restarts its time instead of stacking.
 * Changing size can put the head inside a block (growing big in a low tunnel), so in-wall damage is off while
 * they're on and for a few seconds after
 */
public final class TimedAttributes {

	// time to crawl out after the last one comes off
	private static final int SUFFOCATION_GRACE_TICKS = 60;

	/** Kept on each living entity (LivingEntityMixin), so a respawned player, a new entity, starts clean */
	public interface Holder {
		State infiniteCraft$timedAttributes();
	}

	// ids only need to be unique within one attribute, so the attribute is part of the key
	private record Key(RegistryEntry<EntityAttribute> attribute, Identifier id) {}

	public static final class State {
		private final Map<Key, Integer> ticksLeft = new HashMap<>();
		private int graceLeft;
	}

	static void apply(LivingEntity entity, List<TimedAttributesConsumeEffect.Modifier> modifiers, int ticks) {
		State state = state(entity);
		for (TimedAttributesConsumeEffect.Modifier modifier : modifiers) {
			EntityAttributeInstance instance = entity.getAttributeInstance(modifier.type());
			if (instance == null) continue;
			// its own id, so a potion never replaces (and later removes) a worn item's modifier that Claude gave the same id
			Identifier id = modifier.modifier().id().withPrefixedPath("timed/");
			// another drink restarts the time; adding the same id twice would throw
			instance.removeModifier(id);
			instance.addTemporaryModifier(new EntityAttributeModifier(id,
				modifier.modifier().value(), modifier.modifier().operation()));
			state.ticksLeft.put(new Key(modifier.type(), id), ticks);
		}
		state.graceLeft = SUFFOCATION_GRACE_TICKS;
	}

	public static boolean isActive(LivingEntity entity) {
		return !state(entity).ticksLeft.isEmpty();
	}

	/** Called at the end of every living entity's tick on the server */
	public static void tick(LivingEntity entity) {
		State state = state(entity);
		if (state.ticksLeft.isEmpty()) {
			if (state.graceLeft > 0) state.graceLeft--;
			return;
		}
		state.ticksLeft.replaceAll((key, left) -> left - 1);
		state.ticksLeft.entrySet().removeIf(entry -> {
			if (entry.getValue() > 0) return false;
			remove(entity, entry.getKey());
			return true;
		});
		state.graceLeft = SUFFOCATION_GRACE_TICKS;
	}

	public static void removeAll(LivingEntity entity) {
		State state = state(entity);
		state.ticksLeft.keySet().forEach(key -> remove(entity, key));
		state.ticksLeft.clear();
		state.graceLeft = 0;
	}

	private static void remove(LivingEntity entity, Key key) {
		EntityAttributeInstance instance = entity.getAttributeInstance(key.attribute());
		if (instance != null) instance.removeModifier(key.id());
	}

	private static State state(LivingEntity entity) {
		return ((Holder) entity).infiniteCraft$timedAttributes();
	}

	public static void initialize() {
		// registers the consume effect type
		TimedAttributesConsumeEffect.TYPE.codec();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> removeAll(entity));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.isOf(DamageTypes.IN_WALL) && (isActive(entity) || state(entity).graceLeft > 0)));
	}
}
