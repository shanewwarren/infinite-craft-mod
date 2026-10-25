package com.infinite_craft.vehicle;

import org.jetbrains.annotations.Nullable;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.VehicleComponent;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
