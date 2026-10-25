package com.infinite_craft.test;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.vehicle.VehicleEntity;
import com.infinite_craft.vehicle.Vehicles;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Crafted vehicles: place one, climb in, drive it down the runway. Mock players aren't in the world, but a passenger
 * is ticked by its vehicle, so a mounted mock player needs no ticking of its own
 */
public class VehicleTest {

	static final String TANK = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_name":{text:"Tank"},"minecraft:max_stack_size":1,"infinite_craft:vehicle":{body:"tank",speed:6.0f,health:120.0f,color:"#556B2F"},"infinite_craft:shoot":{kind:"rocket",damage:10.0f,explode:3.0f,interval:30}}}""";

	static final String CAR = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_name":{text:"Race Car"},"minecraft:max_stack_size":1,"infinite_craft:vehicle":{body:"car"}}}""";

	/** Runway start, facing down it (+z) */
	static final Vec3d START = new Vec3d(4.5, 1, 1.5);

	@GameTest(maxTicks = 140, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void tankDrivesDownTheRunway(TestContext context) {
		drive(context, TANK, 100, 1, 0, (vehicle, player, start) -> {
			double distance = vehicle.getEntityPos().subtract(start).horizontalLength();
			System.out.printf("[VehicleTest] tank drove %.1f blocks in 5 s%n", distance);
			context.assertTrue(distance >= 25, Text.literal("tank drove only %.1f blocks in 5 s".formatted(distance)));
			context.assertTrue(vehicle.isOnGround(), Text.literal("tank isn't on the ground"));
			context.assertTrue(player.getVehicle() == vehicle, Text.literal("the rider fell out"));
		});
	}

	@GameTest(maxTicks = 100, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void carIsFasterThanTank(TestContext context) {
		double[] tank = {0};
		VehicleEntity tankVehicle = place(context, TANK, new Vec3d(2.5, 1, 1.5));
		PlayerEntity tankDriver = mount(context, tankVehicle);
		VehicleEntity car = place(context, CAR, new Vec3d(6.5, 1, 1.5));
		PlayerEntity carDriver = mount(context, car);
		Vec3d tankStart = tankVehicle.getEntityPos();
		Vec3d carStart = car.getEntityPos();
		context.runAtEveryTick(() -> {
			tankDriver.forwardSpeed = 1;
			carDriver.forwardSpeed = 1;
		});
		context.runAtTick(60, () -> {
			tank[0] = tankVehicle.getEntityPos().subtract(tankStart).horizontalLength();
			double carDistance = car.getEntityPos().subtract(carStart).horizontalLength();
			System.out.printf("[VehicleTest] in 3 s the tank drove %.1f blocks and the car %.1f%n", tank[0], carDistance);
			context.assertTrue(carDistance >= 1.6 * tank[0],
				Text.literal("car drove %.1f blocks, tank %.1f".formatted(carDistance, tank[0])));
			context.complete();
		});
	}

	@GameTest(maxTicks = 140, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void tankClimbsAOneBlockStep(TestContext context) {
		// a raised stretch across the runway from z = 12 on
		for (int x = 0; x < 9; x++) {
			for (int z = 12; z < 48; z++) {
				context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		drive(context, TANK, 100, 1, 0, (vehicle, player, start) -> {
			Vec3d pos = context.getRelative(vehicle.getEntityPos());
			context.assertTrue(pos.z > 14 && pos.y >= 1.9,
				Text.literal("tank stuck at the step: relative z %.1f, y %.1f".formatted(pos.z, pos.y)));
		});
	}

	@GameTest(maxTicks = 60, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void noInputMeansNoMovement(TestContext context) {
		drive(context, TANK, 40, 0, 0, (vehicle, player, start) -> {
			double moved = vehicle.getEntityPos().subtract(start).horizontalLength();
			context.assertTrue(moved < 0.1, Text.literal("parked tank moved %.2f blocks".formatted(moved)));
		});
	}

	@GameTest(maxTicks = 60, skyAccess = true, structure = LaunchTest.RUNWAY)
	public void turningRotatesTheHull(TestContext context) {
		float[] startYaw = {0};
		drive(context, TANK, 20, 0, 1, (vehicle, player, start) -> {
			float turned = Math.abs(MathHelper.wrapDegrees(vehicle.getYaw() - startYaw[0]));
			double moved = vehicle.getEntityPos().subtract(start).horizontalLength();
			context.assertTrue(turned >= 45, Text.literal("tank turned only %.0f degrees in 1 s".formatted(turned)));
			context.assertTrue(moved < 0.5, Text.literal("turning in place moved the tank %.2f blocks".formatted(moved)));
		});
	}

	@GameTest
	public void sizeScalesTheVehicle(TestContext context) {
		CraftedItemUseTest.floor(context);
		VehicleEntity mega = place(context, TANK.replace("color:\"#556B2F\"", "color:\"#556B2F\",size:2.0f"), new Vec3d(4.5, 1, 4.5));
		double width = mega.getBoundingBox().getLengthX();
		context.assertTrue(Math.abs(width - 4.0) < 0.05, Text.literal("size 2 tank is %.2f wide".formatted(width)));
		mega.discard();
		VehicleEntity mini = place(context, TANK.replace("color:\"#556B2F\"", "color:\"#556B2F\",size:0.5f"), new Vec3d(4.5, 1, 4.5));
		width = mini.getBoundingBox().getLengthX();
		context.assertTrue(Math.abs(width - 1.0) < 0.05, Text.literal("size 0.5 tank is %.2f wide".formatted(width)));
		mini.discard();
		ItemStack huge = ThrustTest.parse(context, TANK.replace("color:\"#556B2F\"", "color:\"#556B2F\",size:9.0f"));
		context.assertTrue(huge.get(Abilities.VEHICLE).size() == 2.0f, Text.literal("size 9 wasn't clamped to 2"));
		context.complete();
	}

	@GameTest
	public void placementNeedsRoom(TestContext context) {
		CraftedItemUseTest.floor(context);
		context.setBlockState(new BlockPos(4, 2, 4), Blocks.STONE);
		VehicleEntity vehicle = VehicleEntity.place(context.getWorld(), ThrustTest.parse(context, TANK),
			context.getAbsolute(new Vec3d(4.5, 1, 4.5)), 0);
		context.assertTrue(vehicle == null, Text.literal("tank was placed under a block"));
		context.complete();
	}

	@GameTest
	public void vehicleSurvivesSaveAndLoad(TestContext context) {
		CraftedItemUseTest.floor(context);
		VehicleEntity vehicle = place(context, TANK, new Vec3d(4.5, 1, 4.5));
		ServerWorld world = context.getWorld();
		NbtWriteView out = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
		vehicle.writeData(out);
		VehicleEntity loaded = new VehicleEntity(Vehicles.VEHICLE, world);
		loaded.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), out.getNbt()));
		ItemStack item = loaded.getItem();
		context.assertTrue(item.get(Abilities.VEHICLE) != null && item.get(Abilities.SHOOT) != null,
			Text.literal("reloaded vehicle lost its item: " + item));
		context.assertTrue(loaded.vehicle().health() == 120.0f, Text.literal("reloaded vehicle lost its settings"));
		vehicle.discard();
		context.complete();
	}

	@GameTest
	public void usingTheItemOnABlockPlacesIt(TestContext context) {
		CraftedItemUseTest.floor(context);
		PlayerEntity player = CraftedItemUseTest.player(context);
		player.setStackInHand(Hand.MAIN_HAND, ThrustTest.parse(context, TANK));
		BlockPos floor = context.getAbsolutePos(new BlockPos(4, 0, 2));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(floor).add(0, 0.5, 0), Direction.UP, floor, false);
		ActionResult result = player.getMainHandStack().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
		context.assertTrue(result.isAccepted(), Text.literal("using the tank on the floor gave " + result));
		Box around = new Box(floor).expand(3);
		context.assertTrue(!context.getWorld().getEntitiesByClass(VehicleEntity.class, around, v -> true).isEmpty(),
			Text.literal("no vehicle was placed"));
		context.assertTrue(player.getMainHandStack().isEmpty(), Text.literal("a survival player kept the tank item"));
		context.getWorld().getEntitiesByClass(VehicleEntity.class, around, v -> true).forEach(VehicleEntity::discard);
		context.complete();
	}

	@GameTest
	public void vehicleItemDoesNotShootFromTheHand(TestContext context) {
		CraftedItemUseTest.floor(context);
		PlayerEntity player = CraftedItemUseTest.player(context);
		player.setStackInHand(Hand.MAIN_HAND, ThrustTest.parse(context, TANK));
		ActionResult result = CraftedItemUseTest.use(player, Hand.MAIN_HAND);
		context.assertTrue(result == ActionResult.PASS, Text.literal("using the tank item in the air gave " + result));
		context.runAtTick(5, () -> {
			Box around = player.getBoundingBox().expand(16);
			context.assertTrue(context.getWorld().getEntitiesByClass(FireworkRocketEntity.class, around, r -> true).isEmpty(),
				Text.literal("the tank item fired a rocket from the hand"));
			context.complete();
		});
	}

	// --- helpers

	interface Result {
		void check(VehicleEntity vehicle, PlayerEntity player, Vec3d start);
	}

	static VehicleEntity place(TestContext context, String nbt, Vec3d relative) {
		VehicleEntity vehicle = VehicleEntity.place(context.getWorld(), ThrustTest.parse(context, nbt), context.getAbsolute(relative), 0);
		context.assertTrue(vehicle != null, Text.literal("couldn't place the vehicle"));
		return vehicle;
	}

	/** A survival mock player riding the vehicle, looking the way it faces */
	static PlayerEntity mount(TestContext context, VehicleEntity vehicle) {
		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		player.refreshPositionAndAngles(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYaw(), 0);
		context.assertTrue(player.startRiding(vehicle, true, false), Text.literal("couldn't get in"));
		return player;
	}

	/** Places the vehicle at the runway start, gets in and holds the keys for some ticks */
	static void drive(TestContext context, String nbt, int ticks, float forward, float sideways, Result result) {
		VehicleEntity vehicle = place(context, nbt, START);
		PlayerEntity player = mount(context, vehicle);
		Vec3d start = vehicle.getEntityPos();
		context.runAtEveryTick(() -> {
			player.forwardSpeed = forward;
			player.sidewaysSpeed = sideways;
		});
		context.runAtTick(ticks, () -> {
			result.check(vehicle, player, start);
			vehicle.discard();
			context.complete();
		});
	}
}
