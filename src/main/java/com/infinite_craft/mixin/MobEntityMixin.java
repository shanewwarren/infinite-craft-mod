package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.Summons;

import net.minecraft.entity.mob.MobEntity;

/** Gives every mob a place to keep whether it's a summon, and keeps summons from despawning (even in Peaceful) */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin implements Summons.Holder {

	@Unique
	private final Summons.State infiniteCraft$summon = new Summons.State();

	@Override
	public Summons.State infiniteCraft$summon() {
		return infiniteCraft$summon;
	}

	@Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$summonsStay(CallbackInfo ci) {
		if (Summons.isSummon((MobEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
