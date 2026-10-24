package com.infinite_craft.ability;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Flings the mob hit by an item with {@link LaunchComponent}. Runs on the server after a melee hit lands
 * (ItemStackMixin wraps postHit), replacing that hit's vanilla knockback, and leaves a trail while it flies
 */
public class LaunchAbility {

	// launch speed per unit of power, in blocks per tick. Measured on LaunchTest's runway, a pig lands about
	// 10 blocks away at power 1, 20 at power 2 and 32 at power 3 (up 1), and peaks 3, 8 and 20 blocks up at up 0.5, 1 and 2
	private static final double HORIZONTAL = 2.2;
	private static final double VERTICAL = 0.6;
	private static final int TRAIL_TICKS = 30;

	// mobs in flight, with ticks of trail left
	private static final Map<LivingEntity, Integer> flying = Collections.synchronizedMap(new WeakHashMap<>());

	public static void initialize() {
		ServerTickEvents.END_WORLD_TICK.register(LaunchAbility::drawTrails);
	}

	public static void onHit(ServerWorld world, LivingEntity target, LivingEntity attacker, LaunchComponent launch) {
		Vec3d away = Vec3d.fromPolar(0, attacker.getYaw());
		// a higher arc trades distance for height
		double across = HORIZONTAL * launch.power() * (1 - 0.25 * launch.up());
		double up = 0.3 + VERTICAL * launch.up() * Math.sqrt(launch.power());
		target.setVelocity(away.x * across, up, away.z * across);
		target.velocityModified = true;
		if (target instanceof ServerPlayerEntity player) {
			// a player's client moves the player, so it has to be told
			player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
		}
		flying.put(target, TRAIL_TICKS);

		world.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY,
			SoundCategory.PLAYERS, 1.0f, 0.9f + world.getRandom().nextFloat() * 0.2f);
	}

	private static void drawTrails(ServerWorld world) {
		synchronized (flying) {
			flying.entrySet().removeIf(entry -> {
				LivingEntity mob = entry.getKey();
				int left = entry.getValue();
				if (mob.isRemoved() || left <= 0 || left < TRAIL_TICKS - 3 && mob.isOnGround()) {
					return true;
				}
				if (mob.getEntityWorld() == world) {
					world.spawnParticles(ParticleTypes.CLOUD, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 2, 0.15, 0.15, 0.15, 0.01);
				}
				entry.setValue(left - 1);
				return false;
			});
		}
	}
}
