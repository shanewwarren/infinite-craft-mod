package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.infinite_craft.ability.Summons;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.conversion.EntityConversionContext;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Gives every mob a place to keep whether it's a summon, and the rules that keep summons on the player's side: no
 * forbidden targets (for summons and for golems and pets near them), no despawning (even in Peaceful), no burning in
 * daylight, and no turning into a wild drowned or stray
 */
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

	// no taming, breeding, name tags, leads, saddles or chests: whatever he gives a summon would vanish with it
	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$summonsTakeNothing(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		if (Summons.isSummon((MobEntity) (Object) this)) {
			cir.setReturnValue(ActionResult.PASS);
		}
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$onlyFairTargets(LivingEntity target, CallbackInfo ci) {
		if (target != null && !Summons.mayTarget((MobEntity) (Object) this, target)) {
			ci.cancel();
		}
	}

	@Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$summonsDontBurn(CallbackInfoReturnable<Boolean> cir) {
		if (Summons.isSummon((MobEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "convertTo(Lnet/minecraft/entity/EntityType;Lnet/minecraft/entity/conversion/EntityConversionContext;Lnet/minecraft/entity/SpawnReason;Lnet/minecraft/entity/conversion/EntityConversionContext$Finalizer;)Lnet/minecraft/entity/mob/MobEntity;",
		at = @At("HEAD"), cancellable = true)
	private <T extends MobEntity> void infiniteCraft$summonsStayAsTheyAre(EntityType<T> type, EntityConversionContext context, SpawnReason reason,
			EntityConversionContext.Finalizer<T> finalizer, CallbackInfoReturnable<T> cir) {
		if (Summons.isSummon((MobEntity) (Object) this)) {
			cir.setReturnValue(null);
		}
	}
}
