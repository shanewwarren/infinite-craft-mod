package com.infinite_craft.ability;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.infinite_craft.mixin.MobEntityAccessor;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.DoorInteractGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.StepAndDestroyBlockGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * A summon's AI, from vanilla's own goals: it keeps its way of fighting (a zombie's melee, a skeleton's bow) but picks
 * targets like a guard dog: whatever attacks the player, whatever he attacks, whatever hurt it, and monsters near him.
 * Goals that grief (breaking doors, trampling eggs, raiding villages) go. {@link Summons#mayTarget} has the last word
 */
public class SummonGoals {

	// how far from the player summons look for monsters, and when they walk or teleport back to him
	private static final double GUARD_RANGE = 16;
	private static final double FOLLOW_DISTANCE = 10;
	private static final double TELEPORT_DISTANCE = 24;

	public static void apply(MobEntity mob, PlayerEntity owner) {
		MobEntityAccessor goals = (MobEntityAccessor) mob;
		goals.infiniteCraft$targetSelector().clear(goal -> true);
		goals.infiniteCraft$targetSelector().add(1, new GuardOwnerGoal(mob, owner));
		if (mob instanceof PathAwareEntity walker) goals.infiniteCraft$targetSelector().add(2, new RevengeGoal(walker));
		goals.infiniteCraft$targetSelector().add(3, new ActiveTargetGoal<>(mob, MobEntity.class, 10, true, false,
			(target, world) -> target instanceof Monster && !Summons.isSummon(target) && target.squaredDistanceTo(owner) < GUARD_RANGE * GUARD_RANGE));
		// running away comes before attacking: a skeleton near his wolf (skeletons flee wolves) would never shoot
		goals.infiniteCraft$goalSelector().clear(goal -> goal instanceof StepAndDestroyBlockGoal || goal instanceof MoveThroughVillageGoal
			|| goal instanceof DoorInteractGoal || goal instanceof FleeEntityGoal);
		goals.infiniteCraft$goalSelector().add(5, new FollowOwnerGoal(mob, owner));
	}

	/** Like a tamed wolf: goes for whatever attacks the player, then whatever he attacks */
	private static class GuardOwnerGoal extends TrackTargetGoal {

		private final PlayerEntity owner;
		private @Nullable LivingEntity chosen;

		GuardOwnerGoal(MobEntity mob, PlayerEntity owner) {
			super(mob, false);
			this.owner = owner;
			setControls(EnumSet.of(Goal.Control.TARGET));
		}

		@Override
		public boolean canStart() {
			chosen = owner.getAttacker() != null ? owner.getAttacker() : owner.getAttacking();
			return chosen != null && chosen != mob && canTrack(chosen, TargetPredicate.DEFAULT) && Summons.mayTarget(mob, chosen);
		}

		@Override
		public void start() {
			mob.setTarget(chosen);
			super.start();
		}
	}

	/** Walks back to the player when it strays, and teleports beside him when it's far behind */
	private static class FollowOwnerGoal extends Goal {

		private final MobEntity mob;
		private final PlayerEntity owner;

		FollowOwnerGoal(MobEntity mob, PlayerEntity owner) {
			this.mob = mob;
			this.owner = owner;
			setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Override
		public boolean canStart() {
			double distance = mob.squaredDistanceTo(owner);
			return distance > TELEPORT_DISTANCE * TELEPORT_DISTANCE
				|| mob.getTarget() == null && distance > FOLLOW_DISTANCE * FOLLOW_DISTANCE;
		}

		@Override
		public boolean shouldContinue() {
			return !mob.getNavigation().isIdle() && mob.squaredDistanceTo(owner) > 9;
		}

		@Override
		public void start() {
			if (mob.squaredDistanceTo(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
				double x = mob.getX(), y = mob.getY(), z = mob.getZ();
				mob.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), mob.getYaw(), mob.getPitch());
				if (!mob.getEntityWorld().isSpaceEmpty(mob)) {
					// he's somewhere it doesn't fit: walk instead
					mob.refreshPositionAndAngles(x, y, z, mob.getYaw(), mob.getPitch());
				} else {
					mob.getNavigation().stop();
					return;
				}
			}
			mob.getNavigation().startMovingTo(owner, 1.2);
		}

		@Override
		public void stop() {
			mob.getNavigation().stop();
		}
	}
}
