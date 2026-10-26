package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.Summons;
import com.infinite_craft.ability.TimedAttributes;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements TimedAttributes.Holder {

	@Unique
	private final TimedAttributes.State infiniteCraft$timedAttributes = new TimedAttributes.State();

	@Override
	public TimedAttributes.State infiniteCraft$timedAttributes() {
		return infiniteCraft$timedAttributes;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void infiniteCraft$tickTimedAttributes(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.getEntityWorld().isClient()) {
			TimedAttributes.tick(self);
			if (self instanceof MobEntity mob) Summons.tick(mob);
		}
	}

	// summons drop no loot, gear or xp: nothing to farm
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$summonsDropNothing(ServerWorld world, DamageSource damageSource, CallbackInfo ci) {
		if (Summons.isSummon((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
