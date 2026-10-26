package com.infinite_craft.ability;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * The helpers {@link SummonAbility} calls: who owns each one, how long it has left, and at most
 * {@link #MAX_PER_PLAYER} per player. They go with a puff when their time is up, when their player leaves, dies or
 * changes dimension. Summons are saved with their chunk like any mob, so one found without an owner (left over from a
 * restart) goes too
 */
public class Summons {

	/** Marks a summon in its saved data, so a leftover one can be told apart from a wild mob */
	public static final String TAG = "infinite_craft.summon";

	public static final int MAX_PER_PLAYER = 12;

	/** Each summon's owner and time left; MobEntityMixin adds it to every mob */
	public interface Holder {
		State infiniteCraft$summon();
	}

	public static class State {
		// the player itself, not a uuid: game test mock players aren't in the world's player list
		@Nullable PlayerEntity owner;
		int ticksLeft;
		Optional<Integer> color = Optional.empty();
	}

	// by player object: a respawned player is a new object, and its old summons go (AFTER_RESPAWN)
	private static final Map<PlayerEntity, ArrayDeque<MobEntity>> byOwner = new IdentityHashMap<>();

	public static void initialize() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> forget(entity));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeAll(handler.player));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> removeAll(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> removeAll(oldPlayer));
	}

	private static State state(Entity entity) {
		return ((Holder) entity).infiniteCraft$summon();
	}

	public static boolean isSummon(Entity entity) {
		return entity instanceof MobEntity && state(entity).owner != null;
	}

	public static @Nullable PlayerEntity ownerOf(Entity entity) {
		return entity instanceof MobEntity ? state(entity).owner : null;
	}

	/** The player's summons, oldest first */
	public static List<MobEntity> of(PlayerEntity player) {
		ArrayDeque<MobEntity> summons = byOwner.get(player);
		return summons == null ? List.of() : List.copyOf(summons);
	}

	/** Makes the mob the player's summon for that many ticks; past the limit, the oldest goes. Call before spawning it */
	static void register(MobEntity mob, PlayerEntity owner, int ticks, Optional<Integer> color) {
		State state = state(mob);
		state.owner = owner;
		state.ticksLeft = ticks;
		state.color = color;
		mob.addCommandTag(TAG);
		ArrayDeque<MobEntity> summons = byOwner.computeIfAbsent(owner, player -> new ArrayDeque<>());
		while (summons.size() >= MAX_PER_PLAYER) {
			vanish(summons.pollFirst());
		}
		summons.addLast(mob);
	}

	/** Takes the mob off its owner's list when it leaves the world, for whatever reason */
	static void forget(Entity entity) {
		if (!(entity instanceof MobEntity) || state(entity).owner == null) return;
		ArrayDeque<MobEntity> summons = byOwner.get(state(entity).owner);
		if (summons != null) {
			summons.removeIf(mob -> mob == entity);
			if (summons.isEmpty()) byOwner.remove(state(entity).owner);
		}
	}

	public static void removeAll(PlayerEntity player) {
		ArrayDeque<MobEntity> summons = byOwner.remove(player);
		if (summons != null) summons.forEach(Summons::vanish);
	}

	/** Every mob tick, on the server (LivingEntityMixin) */
	public static void tick(MobEntity mob) {
		State state = state(mob);
		if (state.owner == null) {
			if (mob.getCommandTags().contains(TAG)) mob.discard();
			return;
		}
		if (--state.ticksLeft <= 0 || state.owner.isRemoved() || state.owner.getEntityWorld() != mob.getEntityWorld()) {
			forget(mob);
			vanish(mob);
			return;
		}
		if (state.color.isPresent() && mob.age % 10 == 0 && mob.getEntityWorld() instanceof ServerWorld world) {
			world.spawnParticles(new DustParticleEffect(state.color.get(), 1.0f), mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(),
				1, mob.getWidth() / 2, mob.getHeight() / 3, mob.getWidth() / 2, 0);
		}
	}

	/** A colored puff where the mob stands (or a plain one), for arriving and leaving */
	static void puff(MobEntity mob) {
		if (!(mob.getEntityWorld() instanceof ServerWorld world)) return;
		ParticleEffect particle = state(mob).color.<ParticleEffect>map(color -> new DustParticleEffect(color, 1.5f)).orElse(ParticleTypes.POOF);
		world.spawnParticles(particle, mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(), 12,
			mob.getWidth() / 2, mob.getHeight() / 2, mob.getWidth() / 2, 0.02);
	}

	private static void vanish(MobEntity mob) {
		puff(mob);
		mob.discard();
	}
}
