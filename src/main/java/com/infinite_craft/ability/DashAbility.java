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
 * Mid-air dashes for players wearing or holding an item with {@link DashComponent}, run every player tick on both sides.
 * Like {@link ThrustAbility}: the player's client moves the player, and the server, which sees the jump key in input packets,
 * clears fall distance and shows the dash to everyone else
 */
public class DashAbility {

	// horizontal speed of a dash at power 1, in blocks per tick, and the small hop that comes with it
	private static final double SPEED = 1.0;
	private static final double HOP = 0.3;
	// ticks between dashes, so one long press can't fire twice
	private static final int GAP = 6;
	// ticks of cloud trail after a dash
	private static final int TRAIL = 6;

	/** Per-player dash state, kept on each player object (PlayerEntityMixin), so the client's and server's copies stay apart */
	public static final class State {
		boolean wasJumping;
		int airTicks;
		int used;
		int lastDash = -100;
	}

	public interface Dasher {
		State infiniteCraft$dashState();
	}

	public static void tick(PlayerEntity player) {
		State state = ((Dasher) player).infiniteCraft$dashState();
		if (player.age - state.lastDash > 0 && player.age - state.lastDash < TRAIL) {
			trail(player, 2);
		}
		boolean jump = jump(player);
		boolean pressed = jump && !state.wasJumping;
		state.wasJumping = jump;
		DashComponent dash = dash(player);
		if (dash == null) return;

		// the tick a jump leaves the ground is still a ground jump, not a mid-air press
		boolean wasAirborne = state.airTicks > 0;
		state.airTicks = player.isOnGround() || player.isClimbing() || player.isTouchingWater() ? 0 : state.airTicks + 1;
		if (state.airTicks == 0) {
			state.used = 0;
			return;
		}
		if (!pressed || !wasAirborne || state.used >= dash.charges() || player.age - state.lastDash < GAP
			|| player.isSpectator() || player.getAbilities().flying || player.isGliding() || player.hasVehicle()) {
			return;
		}
		state.used++;
		state.lastDash = player.age;

		Vec3d facing = Vec3d.fromPolar(0, player.getYaw());
		if (player.isLogicalSideForUpdatingMovement()) {
			player.setVelocity(facing.x * SPEED * dash.power(), HOP, facing.z * SPEED * dash.power());
		}
		player.fallDistance = 0;

		trail(player, 10);
		if (player.getEntityWorld() instanceof ServerWorld world) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 0.8f, 1.3f);
		}
	}

	/** Clouds behind the player: their own client draws them (in step with its movement), the server sends them to everyone else */
	private static void trail(PlayerEntity player, int clouds) {
		Vec3d behind = player.getEntityPos().add(Vec3d.fromPolar(0, player.getYaw()).multiply(-0.4)).add(0, 0.4, 0);
		if (player.getEntityWorld().isClient()) {
			for (int i = 0; i < clouds; i++) {
				player.getEntityWorld().addParticleClient(ParticleTypes.CLOUD,
					behind.x + (player.getRandom().nextDouble() - 0.5) * 0.3, behind.y + (player.getRandom().nextDouble() - 0.5) * 0.3,
					behind.z + (player.getRandom().nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
			}
		} else if (player.getEntityWorld() instanceof ServerWorld world) {
			for (ServerPlayerEntity viewer : world.getPlayers()) {
				if (viewer != player) {
					world.spawnParticles(viewer, ParticleTypes.CLOUD, false, false, behind.x, behind.y, behind.z, clouds, 0.15, 0.15, 0.15, 0.01);
				}
			}
		}
	}

	/** The dash of an item worn in its own slot or held in a hand, or null */
	public static DashComponent dash(PlayerEntity player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getEquippedStack(slot);
			DashComponent dash = stack.get(Abilities.DASH);
			if (dash == null) continue;
			EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
			if (slot.getType() == EquipmentSlot.Type.HAND || equippable != null && equippable.slot() == slot) {
				return dash;
			}
		}
		return null;
	}

	// a server player's keys come from its input packets; the client player and test players use their own state
	private static boolean jump(PlayerEntity player) {
		return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer.getPlayerInput().jump() : player.isJumping();
	}
}
