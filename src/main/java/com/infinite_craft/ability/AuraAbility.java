package com.infinite_craft.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Auras of items worn or held with {@link AuraComponent}, run every player tick on both sides.
 * The server keeps the effects on and hurts monsters close by; particles are drawn by the wearer's own client
 * and sent by the server to everyone else, like {@link ThrustAbility}'s flames
 */
public class AuraAbility {

	// Effects are given for a minute and topped up when under 10 seconds, so they tick like a potion's:
	// regeneration only heals when its remaining time hits a multiple of its period, which constant refreshing
	// would skip, and night vision flickers under 200 ticks
	private static final int DURATION = 1200;
	private static final int TOP_UP_BELOW = 200;
	private static final int CHECK_EVERY = 10;

	/** Effects this player's auras gave, kept on each player object (PlayerEntityMixin), to take back when the item comes off */
	public static final class State {
		Set<RegistryEntry<StatusEffect>> given = new HashSet<>();
	}

	public interface Holder {
		State infiniteCraft$auraState();
	}

	public static void tick(PlayerEntity player) {
		List<AuraComponent> auras = auras(player);
		if (!auras.isEmpty() && player.age % 2 == 0 && !player.isSpectator()) {
			drawParticles(player, auras);
		}
		if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

		if (player.age % CHECK_EVERY == 0) {
			keepEffects(player, auras);
		}
		if (player.age % 20 == 0) {
			for (AuraComponent aura : auras) {
				if (aura.damage() > 0) hurtMonsters(world, player, aura);
			}
		}
	}

	private static void keepEffects(PlayerEntity player, List<AuraComponent> auras) {
		State state = ((Holder) player).infiniteCraft$auraState();
		Map<RegistryEntry<StatusEffect>, Integer> wanted = new HashMap<>();
		for (AuraComponent aura : auras) {
			for (AuraComponent.Effect effect : aura.effects()) {
				wanted.merge(effect.id(), effect.amplifier(), Math::max);
			}
		}
		wanted.forEach((effect, amplifier) -> {
			StatusEffectInstance current = player.getStatusEffect(effect);
			if (current == null || current.getAmplifier() < amplifier || current.getAmplifier() == amplifier && current.isDurationBelow(TOP_UP_BELOW)) {
				// ambient like a beacon's: faint swirls, no potion bubbles hiding the aura's own particles
				player.addStatusEffect(new StatusEffectInstance(effect, DURATION, amplifier, true, false, true));
			}
		});
		// the item came off: take back what it gave, but not a potion or beacon effect that has replaced it since
		for (RegistryEntry<StatusEffect> effect : state.given) {
			if (wanted.containsKey(effect)) continue;
			StatusEffectInstance current = player.getStatusEffect(effect);
			if (current != null && current.isAmbient() && !current.isInfinite() && current.getDuration() <= DURATION) {
				player.removeStatusEffect(effect);
			}
		}
		state.given = new HashSet<>(wanted.keySet());
	}

	private static void hurtMonsters(ServerWorld world, PlayerEntity player, AuraComponent aura) {
		double radius = aura.radius();
		Box around = player.getBoundingBox().expand(radius);
		for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, around,
				mob -> mob instanceof Monster && mob.isAlive() && mob.squaredDistanceTo(player) <= radius * radius)) {
			if (mob.damage(world, player.getDamageSources().indirectMagic(player, player), aura.damage())) {
				world.spawnParticles(particle(aura), mob.getX(), mob.getBodyY(0.5), mob.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
			}
		}
	}

	private static void drawParticles(PlayerEntity player, List<AuraComponent> auras) {
		for (AuraComponent aura : auras) {
			ParticleEffect particle = particle(aura);
			double angle = player.getRandom().nextDouble() * Math.PI * 2;
			Vec3d at = player.getEntityPos().add(Math.cos(angle) * 0.6, player.getRandom().nextDouble() * player.getHeight(), Math.sin(angle) * 0.6);
			if (player.getEntityWorld().isClient()) {
				player.getEntityWorld().addParticleClient(particle, at.x, at.y, at.z, 0, 0.02, 0);
			} else if (player.getEntityWorld() instanceof ServerWorld world) {
				for (ServerPlayerEntity viewer : world.getPlayers()) {
					if (viewer != player) {
						world.spawnParticles(viewer, particle, false, false, at.x, at.y, at.z, 1, 0, 0, 0, 0.01);
					}
				}
			}
		}
	}

	/** The aura's particle if it names a simple one, otherwise dust in its color */
	static ParticleEffect particle(AuraComponent aura) {
		return aura.particle()
			.map(id -> Registries.PARTICLE_TYPE.get(id))
			.filter(type -> type instanceof SimpleParticleType)
			.map(type -> (ParticleEffect) type)
			.orElseGet(() -> new DustParticleEffect(aura.color(), 1.0f));
	}

	/** Auras of items worn in their own slot or held in a hand */
	public static List<AuraComponent> auras(PlayerEntity player) {
		List<AuraComponent> auras = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getEquippedStack(slot);
			AuraComponent aura = stack.get(Abilities.AURA);
			if (aura == null) continue;
			EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
			if (slot.getType() == EquipmentSlot.Type.HAND || equippable != null && equippable.slot() == slot) {
				auras.add(aura);
			}
		}
		return auras;
	}
}
