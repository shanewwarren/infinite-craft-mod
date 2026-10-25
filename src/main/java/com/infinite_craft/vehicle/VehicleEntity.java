package com.infinite_craft.vehicle;

import org.jetbrains.annotations.Nullable;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.Blast;
import com.infinite_craft.ability.VehicleComponent;

import net.minecraft.entity.Dismounting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A tank or car placed from an item with {@link VehicleComponent}. It keeps that item, so it knows its body, speed,
 * color and gun on both sides and gives the item back when packed up or destroyed.
 * Driving uses vanilla's ridden-mob path (as horses and pigs do): the rider's client moves it and the server checks
 */
public class VehicleEntity extends MobEntity {

	private static final TrackedData<ItemStack> ITEM = DataTracker.registerData(VehicleEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

	// on ground with slipperiness 0.6 a ridden mob settles at movementSpeed / 0.454 blocks per tick
	private static final float BLOCKS_PER_SECOND_TO_SPEED = 0.454f / 20;
	// throttle change per tick: full speed in half a second
	private static final float ACCELERATION = 0.1f;
	private static final float TANK_TURN = 5;
	private static final float CAR_TURN = 4;

	/** -0.5 (reversing) to 1 (full ahead), ramped toward what the rider holds */
	private float throttle;

	// where the gun sits at size 1: the turret's middle, and how far out the barrel tip is from there
	private static final double TURRET_HEIGHT = 1.2;
	private static final double BARREL_LENGTH = 2.2;

	// ramming: at least this fast, in blocks per second
	private static final double RAM_SPEED = 3;
	private @Nullable Vec3d lastTickPos;

	public VehicleEntity(EntityType<? extends VehicleEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createVehicleAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.MAX_HEALTH, 100)
			.add(EntityAttributes.MOVEMENT_SPEED, 0.1)
			.add(EntityAttributes.STEP_HEIGHT, 1.0)
			.add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(EntityAttributes.SAFE_FALL_DISTANCE, 6);
	}

	/** Places the item's vehicle at pos, or returns null when there's no room for it there */
	public static @Nullable VehicleEntity place(ServerWorld world, ItemStack stack, Vec3d pos, float yaw) {
		VehicleEntity vehicle = Vehicles.VEHICLE.create(world, SpawnReason.SPAWN_ITEM_USE);
		if (vehicle == null) {
			return null;
		}
		vehicle.setItem(stack);
		VehicleComponent settings = vehicle.vehicle();
		vehicle.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(settings.health());
		vehicle.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(settings.size());
		vehicle.setHealth(vehicle.getMaxHealth());
		// the hitbox follows the scale attribute only on the next tick otherwise, too late for the room check
		vehicle.calculateDimensions();
		vehicle.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
		vehicle.setBodyYaw(yaw);
		vehicle.setHeadYaw(yaw);
		if (!world.isSpaceEmpty(vehicle)) {
			return null;
		}
		world.spawnEntity(vehicle);
		return vehicle;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ITEM, ItemStack.EMPTY);
	}

	/** The item that placed this vehicle, count 1 */
	public ItemStack getItem() {
		return dataTracker.get(ITEM).copy();
	}

	private void setItem(ItemStack stack) {
		dataTracker.set(ITEM, stack.copyWithCount(1));
	}

	public VehicleComponent vehicle() {
		return dataTracker.get(ITEM).getOrDefault(Abilities.VEHICLE, VehicleComponent.DEFAULT);
	}

	/** Where shots leave the barrel, for an aim: out from the turret, clear of the hull */
	public Vec3d muzzle(float pitch, float yaw) {
		float scale = getScale();
		Vec3d turret = getEntityPos().add(0, TURRET_HEIGHT * scale, 0);
		return turret.add(Vec3d.fromPolar(pitch, yaw).multiply(BARREL_LENGTH * scale));
	}

	public float throttle() {
		return throttle;
	}

	// --- driving

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof PlayerEntity player ? player : null;
	}

	@Override
	protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
		return new Vec3d(0, 0, throttle);
	}

	@Override
	protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
		super.tickControlled(controllingPlayer, movementInput);
		float wanted = controllingPlayer.forwardSpeed > 0 ? 1 : controllingPlayer.forwardSpeed < 0 ? -0.5f : 0;
		throttle += MathHelper.clamp(wanted - throttle, -ACCELERATION, ACCELERATION);
		// A (positive sideways) turns left, which is toward lower yaw
		float turn = switch (vehicle().body()) {
			case TANK -> TANK_TURN;
			case CAR -> CAR_TURN * Math.abs(throttle);
		};
		float yaw = getYaw() - Math.signum(controllingPlayer.sidewaysSpeed) * turn;
		setYaw(yaw);
		lastYaw = yaw;
		setBodyYaw(yaw);
		setHeadYaw(yaw);
	}

	@Override
	protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
		return vehicle().speed() * BLOCKS_PER_SECOND_TO_SPEED;
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (player.isSneaking() || hasPassengers()) {
			return ActionResult.PASS;
		}
		if (!getEntityWorld().isClient()) {
			player.startRiding(this);
		}
		return ActionResult.SUCCESS;
	}

	/** Mobs that driving into hurts: not players, not the rider's pets, not armor stands */
	public static boolean isRammable(Entity target, @Nullable PlayerEntity rider) {
		return !(target instanceof PlayerEntity) && Blast.isFairGame(target, rider);
	}

	// --- safety: the vehicle never hurts its rider, and always gives its item back

	@Override
	public void tick() {
		super.tick();
		if (getEntityWorld() instanceof ServerWorld world) {
			// from where it actually went: a player's client moves its vehicle and the server only hears the result
			Vec3d pos = getEntityPos();
			double blocksPerSecond = lastTickPos == null ? 0 : pos.subtract(lastTickPos).horizontalLength() * 20;
			lastTickPos = pos;
			if (getControllingPassenger() instanceof PlayerEntity rider && blocksPerSecond >= RAM_SPEED) {
				ram(world, rider);
			}
		}
		for (Entity passenger : getPassengerList()) {
			// a drop or jump while riding mustn't hurt once they get out
			passenger.onLanding();
		}
	}

	private void ram(ServerWorld world, PlayerEntity rider) {
		Vec3d facing = Vec3d.fromPolar(0, getYaw());
		for (Entity target : world.getOtherEntities(this, getBoundingBox().expand(0.3),
				target -> target.isAlive() && !hasPassenger(target) && isRammable(target, rider))) {
			LivingEntity living = (LivingEntity) target;
			if (living.hurtTime > 0) {
				continue;
			}
			living.damage(world, getDamageSources().mobAttack(this), 2 + vehicle().speed() / 2);
			living.takeKnockback(1.0, -facing.x, -facing.z);
		}
	}

	@Override
	public boolean handleFallDamage(double fallDistance, float damagePerDistance, DamageSource damageSource) {
		// and passes none on to the rider
		return false;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		Entity attacker = source.getAttacker();
		if (attacker != null && hasPassenger(attacker)) {
			// its rider's punches and its own shells
			return false;
		}
		if (attacker instanceof PlayerEntity player && player.isSneaking() && source.getSource() == player) {
			packUp(world);
			return false;
		}
		return super.damage(world, source, amount);
	}

	/** Back into the item, whole again: a vehicle's damage isn't stored on its item */
	private void packUp(ServerWorld world) {
		dropStack(world, getItem());
		discard();
	}

	@Override
	protected void drop(ServerWorld world, DamageSource damageSource) {
		dropStack(world, getItem());
		// looks and sounds like it blew up, without hurting anything or breaking blocks
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
	}

	@Override
	public Vec3d updatePassengerForDismount(LivingEntity passenger) {
		// beside it, then in front and behind; on top only if there's no room anywhere else
		for (float side : new float[] {90, -90, 0, 180}) {
			Vec3d offset = getPassengerDismountOffset(getWidth(), passenger.getWidth(), getYaw() + side);
			Vec3d spot = safeDismountSpot(offset, passenger);
			if (spot != null) {
				return spot;
			}
		}
		return super.updatePassengerForDismount(passenger);
	}

	/** The lowest spot at that offset where the passenger fits standing on something, as horses do */
	private @Nullable Vec3d safeDismountSpot(Vec3d offset, LivingEntity passenger) {
		double x = getX() + offset.x;
		double z = getZ() + offset.z;
		BlockPos.Mutable pos = new BlockPos.Mutable(x, getBoundingBox().minY, z);
		double highest = getBoundingBox().maxY + 0.75;
		while (pos.getY() < highest) {
			double height = getEntityWorld().getDismountHeight(pos);
			if (pos.getY() + height > highest) {
				break;
			}
			if (Dismounting.canDismountInBlock(height)) {
				Vec3d spot = new Vec3d(x, pos.getY() + height, z);
				if (Dismounting.canPlaceEntityAt(getEntityWorld(), passenger, passenger.getBoundingBox(EntityPose.STANDING).offset(spot))) {
					return spot;
				}
			}
			pos.move(Direction.UP);
		}
		return null;
	}

	// --- a parked vehicle stays put and stays around

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean canBreatheInWater() {
		return true;
	}

	// --- saving

	@Override
	protected void writeCustomData(WriteView view) {
		super.writeCustomData(view);
		ItemStack item = dataTracker.get(ITEM);
		if (!item.isEmpty()) {
			view.put("vehicle_item", ItemStack.CODEC, item);
		}
	}

	@Override
	protected void readCustomData(ReadView view) {
		super.readCustomData(view);
		view.read("vehicle_item", ItemStack.CODEC).ifPresent(this::setItem);
	}
}
