package com.infinite_craft.ability;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.util.Identifier;

/**
 * Attribute modifiers that come off after a while, from {@link TimedAttributesConsumeEffect} (a Shrink Potion).
 * They're added as temporary modifiers, which Minecraft never saves, so logging out or a server stop clears them,
 * and dying takes them off too. The same modifier id again restarts its time instead of stacking.
 * Changing size can put the head inside a block (growing back in the 1-block tunnel a shrunk player just walked
 * through), so in-wall damage is off while they're on and for a few seconds after
 */
public final class TimedAttributes {

	// time to crawl out after growing back
	private static final int SUFFOCATION_GRACE_TICKS = 60;

	private record Timed(EntityAttributeInstance instance, int ticksLeft) {}

	private static final class State {
		final Map<Identifier, Timed> modifiers = new HashMap<>();
		int graceLeft;
	}

	// server side only; counted down by each entity's own tick, so it pauses with the entity
	private static final Map<LivingEntity, State> states = Collections.synchronizedMap(new WeakHashMap<>());

	static void apply(LivingEntity entity, List<TimedAttributesConsumeEffect.Modifier> modifiers, int ticks) {
		State state = states.computeIfAbsent(entity, e -> new State());
		for (TimedAttributesConsumeEffect.Modifier modifier : modifiers) {
			EntityAttributeInstance instance = entity.getAttributeInstance(modifier.type());
			if (instance == null) continue;
			// another drink restarts the time; adding the same id twice would throw
			instance.removeModifier(modifier.modifier().id());
			instance.addTemporaryModifier(modifier.modifier());
			state.modifiers.put(modifier.modifier().id(), new Timed(instance, ticks));
		}
	}

	public static boolean isActive(LivingEntity entity) {
		State state = states.get(entity);
		return state != null && !state.modifiers.isEmpty();
	}

	/** Called at the end of every living entity's tick */
	public static void tick(LivingEntity entity) {
		State state = states.get(entity);
		if (state == null) return;
		if (!state.modifiers.isEmpty()) {
			state.modifiers.replaceAll((id, timed) -> new Timed(timed.instance(), timed.ticksLeft() - 1));
			state.modifiers.entrySet().removeIf(entry -> {
				if (entry.getValue().ticksLeft() > 0) return false;
				entry.getValue().instance().removeModifier(entry.getKey());
				return true;
			});
			state.graceLeft = SUFFOCATION_GRACE_TICKS;
		} else if (--state.graceLeft <= 0) {
			states.remove(entity);
		}
	}

	public static void removeAll(LivingEntity entity) {
		State state = states.remove(entity);
		if (state == null) return;
		state.modifiers.forEach((id, timed) -> timed.instance().removeModifier(id));
	}

	public static void initialize() {
		// registers the consume effect type
		TimedAttributesConsumeEffect.TYPE.codec();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> removeAll(entity));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.isOf(DamageTypes.IN_WALL) && states.containsKey(entity)));
	}
}
