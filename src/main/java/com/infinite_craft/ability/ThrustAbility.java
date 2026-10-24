package com.infinite_craft.ability;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Jetpack flight for players wearing or holding an item with {@link ThrustComponent}, run every player tick on both sides.
 * A player's own movement is computed by their client, so the client pushes the player; the server, which knows the
 * keys held from the player's input packets, shows the flames, plays the sound and clears fall distance
 */
public class ThrustAbility {

	// vertical speed added per tick at power 1, and the most it climbs per tick
	private static final double LIFT = 0.16;
	private static final double MAX_CLIMB = 0.35;
	// horizontal push per tick while holding forward, at speed 1
	private static final double PUSH = 0.03;

	public static void tick(PlayerEntity player) {
		if (!isThrusting(player)) return;
		ThrustComponent thrust = thrust(player);

		if (player.isLogicalSideForUpdatingMovement()) {
			Vec3d velocity = player.getVelocity();
			double climb = Math.min(velocity.y + LIFT * thrust.power(), MAX_CLIMB * thrust.power());
			Vec3d push = Vec3d.ZERO;
			if (forward(player)) {
				push = Vec3d.fromPolar(0, player.getYaw()).multiply(PUSH * thrust.speed());
			}
			player.setVelocity(velocity.x + push.x, Math.max(velocity.y, climb), velocity.z + push.z);
		}
		player.fallDistance = 0;

		// flames below the tanks: the flyer's own client draws them (in step with its movement), the server sends them to everyone else
		Vec3d nozzle = player.getEntityPos().add(Vec3d.fromPolar(0, player.getYaw()).multiply(-0.35)).add(0, 0.5, 0);
		if (player.getEntityWorld().isClient()) {
			for (int i = 0; i < 3; i++) {
				player.getEntityWorld().addParticleClient(i == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME,
					nozzle.x + (player.getRandom().nextDouble() - 0.5) * 0.2, nozzle.y, nozzle.z + (player.getRandom().nextDouble() - 0.5) * 0.2,
					0, -0.25, 0);
			}
		} else if (player.getEntityWorld() instanceof ServerWorld world) {
			for (ServerPlayerEntity viewer : world.getPlayers()) {
				if (viewer != player) {
					world.spawnParticles(viewer, ParticleTypes.FLAME, false, false, nozzle.x, nozzle.y, nozzle.z, 3, 0.08, 0.05, 0.08, 0.01);
				}
			}
			if (player.age % 6 == 0) {
				world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.PLAYERS, 0.3f, 1.4f);
			}
		}
	}

	/** Holding jump, with a thrust item on, and not swimming, riding or already flying some other way */
	public static boolean isThrusting(PlayerEntity player) {
		return jump(player) && thrust(player) != null
			&& !player.isSpectator() && !player.getAbilities().flying && !player.isGliding()
			&& !player.hasVehicle() && !player.isTouchingWater();
	}

	/** The thrust of an item worn in its own slot or held in a hand, or null */
	public static ThrustComponent thrust(PlayerEntity player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getEquippedStack(slot);
			ThrustComponent thrust = stack.get(Abilities.THRUST);
			if (thrust == null) continue;
			EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
			if (slot.getType() == EquipmentSlot.Type.HAND || equippable != null && equippable.slot() == slot) {
				return thrust;
			}
		}
		return null;
	}

	// a server player's keys come from its input packets; the client player and test players use their own state
	private static boolean jump(PlayerEntity player) {
		return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer.getPlayerInput().jump() : player.isJumping();
	}

	private static boolean forward(PlayerEntity player) {
		return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer.getPlayerInput().forward() : player.forwardSpeed > 0;
	}
}
