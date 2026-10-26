package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.passive.BeeEntity;

/** A summoned bee keeps its stinger (Summons.tick) */
@Mixin(BeeEntity.class)
public interface BeeEntityAccessor {

	@Invoker("setHasStung")
	void infiniteCraft$setHasStung(boolean hasStung);
}
