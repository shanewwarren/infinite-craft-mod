package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.TimedAttributes;

import net.minecraft.entity.LivingEntity;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void infiniteCraft$tickTimedAttributes(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getEntityWorld().isClient()) {
			TimedAttributes.tick(self);
		}
	}
}
