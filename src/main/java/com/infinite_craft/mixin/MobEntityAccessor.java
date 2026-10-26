package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;

/** A mob's goals, so a summon's can be swapped for ones that fight on the player's side (SummonGoals) */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

	@Accessor("goalSelector")
	GoalSelector infiniteCraft$goalSelector();

	@Accessor("targetSelector")
	GoalSelector infiniteCraft$targetSelector();
}
