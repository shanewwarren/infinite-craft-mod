package com.infinite_craft.ability;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.component.type.UseCooldownComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Fires items that have {@link ShootComponent}, in place of the item's own use (so a crossbow base needs no arrows).
 * Runs on both sides so the client's prediction matches; only the server shoots
 */
public class ShootAbility {

	/** Marks arrows fired as bullets: they skip hurt cooldowns and vanish when they hit a block */
	public static final String BULLET_TAG = "infinite_craft.bullet";

	private static final double BEAM_RANGE = 48;
	private static final float BULLET_SPEED = 3.0f;
	// slow enough to watch fly; with flight duration 2 a rocket bursts after 30 to 41 ticks, 45 to 60 blocks out
	private static final float ROCKET_SPEED = 1.5f;

	// When each player last fired, by player age. One map per side: in singleplayer the client's and the server's
	// player are equal (entities compare by network id), and the client's clock must not hold back the server's shot
	private static final Map<PlayerEntity, Integer> clientLastShot = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<PlayerEntity, Integer> serverLastShot = Collections.synchronizedMap(new WeakHashMap<>());

	public static ActionResult use(World world, PlayerEntity user, Hand hand, ItemStack stack, ShootComponent shoot) {
		return fire(world, user, stack, shoot, user.getEyePos());
	}

	/**
	 * Fires the gun's shots from `from` the way the user looks: their eyes for a hand-held gun, a barrel tip for a vehicle.
	 * Returns CONSUME while the gun is still reloading
	 */
	public static ActionResult fire(World world, PlayerEntity user, ItemStack stack, ShootComponent shoot, Vec3d from) {
		Map<PlayerEntity, Integer> lastShot = world.isClient() ? clientLastShot : serverLastShot;
		Integer last = lastShot.get(user);
		if (last != null && user.age - last >= 0 && user.age - last < shoot.interval()) {
			// still reloading: CONSUME so the client doesn't fall through to using the offhand item between shots
			return ActionResult.CONSUME;
		}
		lastShot.put(user, user.age);

		if (world instanceof ServerWorld serverWorld) {
			for (int i = 0; i < shoot.count(); i++) {
				switch (shoot.kind()) {
					case BEAM -> fireBeam(serverWorld, user, shoot, from);
					case BULLET -> fireBullet(serverWorld, user, stack, shoot, from);
					case ROCKET -> fireRocket(serverWorld, user, shoot, from);
				}
			}
			SoundEvent sound = shoot.sound().map(entry -> entry.value()).orElse(switch (shoot.kind()) {
				case BEAM -> SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL;
				case BULLET -> SoundEvents.ITEM_CROSSBOW_SHOOT;
				case ROCKET -> SoundEvents.ENTITY_FIREWORK_ROCKET_SHOOT;
			});
			world.playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundCategory.PLAYERS, 0.8f,
				0.9f + world.getRandom().nextFloat() * 0.2f);
		}
		UseCooldownComponent cooldown = stack.get(DataComponentTypes.USE_COOLDOWN);
		if (cooldown != null) {
			cooldown.set(stack, user);
		}
		return ActionResult.SUCCESS;
	}

	private static void fireBeam(ServerWorld world, PlayerEntity user, ShootComponent shoot, Vec3d start) {
		Vec3d direction = aim(user, shoot.spread());
		Vec3d end = start.add(direction.multiply(BEAM_RANGE));

		HitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getPos();
		}
		EntityHitResult entityHit = ProjectileUtil.raycast(user, start, end, user.getBoundingBox().stretch(end.subtract(start)).expand(1),
			entity -> !entity.isSpectator() && entity.canHit() && entity != user.getVehicle(), start.squaredDistanceTo(end));
		if (entityHit != null) {
			end = entityHit.getPos();
			hit(world, user, entityHit.getEntity(), shoot);
			Blast.at(world, entityHit.getEntity().getEntityPos(), shoot, null, user, entityHit.getEntity());
		} else if (blockHit.getType() != HitResult.Type.MISS) {
			Blast.at(world, end, shoot, null, user, null);
		}

		DustParticleEffect dust = new DustParticleEffect(shoot.color(), 1.0f);
		double length = start.distanceTo(end);
		// start in front of the eyes so the beam doesn't fill the shooter's view; a barrel tip is already out in front
		for (double d = user.getEyePos().equals(start) ? 1.5 : 0; d < length; d += 0.4) {
			Vec3d p = start.add(direction.multiply(d));
			world.spawnParticles(dust, p.x, p.y - 0.15, p.z, 1, 0, 0, 0, 0);
		}
	}

	private static void hit(ServerWorld world, PlayerEntity user, Entity target, ShootComponent shoot) {
		// rapid fire should land every shot, not bounce off the target's hurt cooldown
		if (target instanceof LivingEntity living) {
			living.timeUntilRegen = 0;
		}
		if (shoot.fire()) {
			target.setOnFireFor(5);
		}
		target.damage(world, user.getDamageSources().playerAttack(user), shoot.damage());
		applyHitEffects(target, shoot, user);
	}

	/** Puts the gun's effects on a mob a shot hit, unless it's the shooter or one of their pets */
	public static void applyHitEffects(Entity target, ShootComponent shoot, @Nullable Entity shooter) {
		if (shoot.effects().isEmpty() || !(target instanceof LivingEntity living) || !Blast.isFairGame(target, shooter)) {
			return;
		}
		for (ShootComponent.HitEffect effect : shoot.effects()) {
			// an instant effect (instant_damage, instant_health) applies on every tick it lasts, so it gets one tick
			int ticks = effect.id().value().isInstant() ? 1 : Math.round(effect.seconds() * 20);
			living.addStatusEffect(new StatusEffectInstance(effect.id(), ticks, effect.amplifier()), shooter);
		}
	}

	private static void fireBullet(ServerWorld world, PlayerEntity user, ItemStack stack, ShootComponent shoot, Vec3d from) {
		ArrowEntity bullet = new ArrowEntity(world, user, new ItemStack(Items.ARROW), stack.copyWithCount(1));
		if (!user.getEyePos().equals(from)) {
			bullet.setPosition(from);
		}
		bullet.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		bullet.addCommandTag(BULLET_TAG);
		// arrows deal speed × damage, rounded up, and fly a little faster or slower than BULLET_SPEED
		bullet.setDamage(shoot.damage() / BULLET_SPEED * 0.95);
		bullet.setVelocity(user, user.getPitch(), user.getYaw(), 0, BULLET_SPEED, shoot.spread());
		if (shoot.fire()) {
			// a burning arrow sets what it hits on fire
			bullet.setOnFireFor(100);
		}
		world.spawnEntity(bullet);

		Vec3d muzzle = user.getEyePos().equals(from) ? from.add(user.getRotationVector().multiply(1.2)) : from;
		world.spawnParticles(new DustParticleEffect(shoot.color(), 0.6f), muzzle.x, muzzle.y - 0.15, muzzle.z, 3, 0.05, 0.05, 0.05, 0);
	}

	/** A firework that flies straight and carries the gun's settings in its item, so a saved world keeps them */
	private static void fireRocket(ServerWorld world, PlayerEntity user, ShootComponent shoot, Vec3d from) {
		ItemStack rocketItem = new ItemStack(Items.FIREWORK_ROCKET);
		rocketItem.set(DataComponentTypes.FIREWORKS, new FireworksComponent(2, List.of()));
		rocketItem.set(Abilities.SHOOT, shoot);
		Vec3d start = user.getEyePos().equals(from) ? from.subtract(0, 0.15, 0) : from;
		FireworkRocketEntity rocket = new FireworkRocketEntity(world, rocketItem, user, start.x, start.y, start.z, true);
		rocket.setVelocity(user, user.getPitch(), user.getYaw(), 0, ROCKET_SPEED, shoot.spread());
		world.spawnEntity(rocket);
	}

	/** Rockets: called by FireworkRocketEntityMixin when one hits a mob */
	public static void onRocketHitEntity(ServerWorld world, FireworkRocketEntity rocket, ShootComponent shoot, Entity target) {
		Entity shooter = rocket.getOwner();
		if (target instanceof LivingEntity living) {
			living.timeUntilRegen = 0;
		}
		if (shoot.fire()) {
			target.setOnFireFor(5);
		}
		target.damage(world, world.getDamageSources().fireworks(rocket, shooter), shoot.damage());
		applyHitEffects(target, shoot, shooter);
		Blast.at(world, rocket.getEntityPos(), shoot, rocket, shooter, target);
	}

	private static Vec3d aim(PlayerEntity user, float spread) {
		if (spread <= 0) {
			return user.getRotationVector();
		}
		float pitch = user.getPitch() + (user.getRandom().nextFloat() * 2 - 1) * spread;
		float yaw = user.getYaw() + (user.getRandom().nextFloat() * 2 - 1) * spread;
		return Vec3d.fromPolar(pitch, yaw);
	}

	/** Bullet arrows: called by PersistentProjectileEntityMixin before the arrow hurts what it hit */
	public static void onBulletHitEntity(Entity target) {
		if (target instanceof LivingEntity living) {
			living.timeUntilRegen = 0;
		}
	}

	/** The gun that fired a bullet arrow, which carries its shoot settings */
	public static @Nullable ShootComponent bulletShoot(PersistentProjectileEntity bullet) {
		ItemStack gun = bullet.getWeaponStack();
		return gun == null ? null : gun.get(Abilities.SHOOT);
	}
}
