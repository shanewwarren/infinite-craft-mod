package com.infinite_craft.ability;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Calls the helpers of items with {@link SummonComponent}, on free ground around the player */
public class SummonAbility {

	// so a summon horn can't be spammed. Kept here, not in the item cooldowns: those go by item id, and every crafted
	// item is infinite_craft:custom_crafted_item, so a horn's cooldown would lock his guns too
	private static final int COOLDOWN_TICKS = 60;

	// when each player last summoned, by world time; one map per side, like ShootAbility's
	private static final Map<PlayerEntity, Long> clientLastSummon = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<PlayerEntity, Long> serverLastSummon = Collections.synchronizedMap(new WeakHashMap<>());

	public static ActionResult use(World world, PlayerEntity user, Hand hand, ItemStack stack, SummonComponent summon) {
		Map<PlayerEntity, Long> lastSummon = world.isClient() ? clientLastSummon : serverLastSummon;
		Long last = lastSummon.get(user);
		if (last != null && world.getTime() - last >= 0 && world.getTime() - last < COOLDOWN_TICKS) {
			return ActionResult.FAIL;
		}
		if (!(world instanceof ServerWorld serverWorld)) {
			lastSummon.put(user, world.getTime());
			return ActionResult.SUCCESS;
		}
		int spawned = 0;
		for (int i = 0; i < summon.count(); i++) {
			if (spawn(serverWorld, user, summon) != null) spawned++;
		}
		if (spawned == 0) {
			return ActionResult.FAIL;
		}
		lastSummon.put(user, world.getTime());
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return ActionResult.SUCCESS;
	}

	/** For tests: lets the player summon again straight away */
	public static void resetCooldown(PlayerEntity player) {
		clientLastSummon.remove(player);
		serverLastSummon.remove(player);
	}

	private static @Nullable MobEntity spawn(ServerWorld world, PlayerEntity owner, SummonComponent summon) {
		Entity entity = summon.entity().create(world, SpawnReason.MOB_SUMMONED);
		if (!(entity instanceof MobEntity mob)) {
			return null;
		}
		mob.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), 0);
		// vanilla's own gear (a skeleton's bow), then ours on top
		mob.initialize(world, world.getLocalDifficulty(owner.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
		if (mob.getVehicle() != null) {
			// a chicken jockey's chicken would be left behind as a wild mob
			Entity vehicle = mob.getVehicle();
			mob.stopRiding();
			vehicle.discard();
		}
		mob.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(summon.size());
		// the new size only applies on the mob's first tick; place() must test the grown box
		mob.calculateDimensions();
		summon.gear().ifPresent(gear -> gear.bySlot().forEach((slot, item) -> item.ifPresent(i -> mob.equipStack(slot, new ItemStack(i)))));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setEquipmentDropChance(slot, 0);
		}
		summon.name().ifPresent(name -> {
			mob.setCustomName(Text.literal(name));
			mob.setCustomNameVisible(true);
		});
		mob.setPersistent();
		mob.setCanPickUpLoot(false);
		if (mob instanceof ZombieEntity zombie) {
			zombie.setCanBreakDoors(false);
			// on hard, a hurt zombie calls wild zombies, which would stay and hunt him
			EntityAttributeInstance reinforcements = zombie.getAttributeInstance(EntityAttributes.SPAWN_REINFORCEMENTS);
			if (reinforcements != null) {
				reinforcements.clearModifiers();
				reinforcements.setBaseValue(0);
			}
		}
		if (mob instanceof VexEntity vex) vex.setLifeTicks(summon.seconds() * 20);

		if (!place(world, owner, mob)) {
			mob.discard();
			return null;
		}
		Summons.register(mob, owner, summon.seconds() * 20, summon.color());
		SummonGoals.apply(mob, owner);
		if (!world.spawnEntity(mob)) {
			Summons.forget(mob);
			return null;
		}
		Summons.puff(mob);
		return mob;
	}

	/** Moves the mob to free, solid ground 2 to 4 blocks around the player, or onto the player's spot; false if none fits */
	private static boolean place(ServerWorld world, PlayerEntity owner, MobEntity mob) {
		for (int attempt = 0; attempt < 16; attempt++) {
			float angle = world.random.nextFloat() * MathHelper.TAU;
			double distance = 2 + world.random.nextDouble() * 2;
			double x = owner.getX() + MathHelper.cos(angle) * distance;
			double z = owner.getZ() + MathHelper.sin(angle) * distance;
			BlockPos column = BlockPos.ofFloored(x, owner.getY() + 2, z);
			for (int down = 0; down <= 6; down++) {
				BlockPos feet = column.down(down);
				BlockPos below = feet.down();
				if (world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) {
					mob.refreshPositionAndAngles(x, feet.getY(), z, owner.getYaw(), 0);
					if (world.isSpaceEmpty(mob)) return true;
					break;
				}
			}
		}
		mob.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), 0);
		return world.isSpaceEmpty(mob);
	}
}
