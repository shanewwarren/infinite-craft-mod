package com.infinite_craft.ability;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;

/**
 * Explosions and lightning where a shot from {@link ShootComponent} lands. They hurt mobs and never the player's world:
 * no broken blocks, no fire, and nothing that isn't a mob (dropped items, item frames, armor stands).
 * The shooter and their pets are safe too, so point-blank rockets are fun rather than fatal
 */
public class Blast {

	/**
	 * @param source    the projectile that landed, or null for a beam
	 * @param shooter   who fired, kept safe from the blast
	 * @param directHit the mob the shot hit, if any; it has just been hurt, so its hurt cooldown is reset for the blast to land too
	 */
	public static void at(ServerWorld world, Vec3d pos, ShootComponent shoot, @Nullable Entity source, @Nullable Entity shooter,
			@Nullable Entity directHit) {
		if (directHit instanceof LivingEntity living) {
			living.timeUntilRegen = 0;
		}
		if (shoot.explode() > 0) {
			// NONE keeps every block; createFire false places no fire
			world.createExplosion(source, world.getDamageSources().explosion(source, shooter), new SafeBlast(shooter),
				pos.x, pos.y, pos.z, shoot.explode(), false, World.ExplosionSourceType.NONE);
		}
		if (shoot.lightning()) {
			strikeLightning(world, pos, shooter);
		}
	}

	/** A bolt you can see and hear that strikes the mobs around it like vanilla lightning, minus the fires */
	private static void strikeLightning(ServerWorld world, Vec3d pos, @Nullable Entity shooter) {
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		// cosmetic: no fire, no copper scraping, no lightning rods, and it strikes no one itself
		bolt.setCosmetic(true);
		bolt.refreshPositionAfterTeleport(pos);
		world.spawnEntity(bolt);

		// vanilla lightning's reach
		Box reach = new Box(pos.x - 3, pos.y - 3, pos.z - 3, pos.x + 3, pos.y + 6 + 3, pos.z + 3);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, reach, target -> target.isAlive() && isFairGame(target, shooter))) {
			target.timeUntilRegen = 0;
			if (target instanceof CreeperEntity) {
				// a struck creeper turns charged, and a charged creeper's explosion breaks blocks
				target.setOnFireFor(8);
				target.damage(world, world.getDamageSources().lightningBolt(), 5);
			} else {
				// vanilla's own strike: 5 damage and fire, and pigs, villagers and mooshrooms transform
				target.onStruckByLightning(world, bolt);
			}
		}
	}

	/** Mobs a blast may hurt: not the shooter, not their pets, and not armor stands, which break into an item */
	static boolean isFairGame(Entity entity, @Nullable Entity shooter) {
		if (!(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity || entity.isSpectator()) {
			return false;
		}
		if (shooter != null) {
			if (entity == shooter || entity.getUuid().equals(shooter.getUuid())) {
				return false;
			}
			if (entity instanceof Tameable pet && pet.getOwnerReference() != null && pet.getOwnerReference().getUuid().equals(shooter.getUuid())) {
				return false;
			}
		}
		return true;
	}

	/** Damages and pushes only fair game; leaves every block in place */
	private static class SafeBlast extends ExplosionBehavior {

		private final @Nullable Entity shooter;

		SafeBlast(@Nullable Entity shooter) {
			this.shooter = shooter;
		}

		@Override
		public boolean canDestroyBlock(Explosion explosion, BlockView world, BlockPos pos, BlockState state, float power) {
			return false;
		}

		@Override
		public boolean shouldDamage(Explosion explosion, Entity entity) {
			return isFairGame(entity, shooter);
		}

		@Override
		public float getKnockbackModifier(Entity entity) {
			return isFairGame(entity, shooter) ? super.getKnockbackModifier(entity) : 0;
		}
	}
}
