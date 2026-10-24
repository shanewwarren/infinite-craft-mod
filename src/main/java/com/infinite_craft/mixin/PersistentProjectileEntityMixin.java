package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.ShootAbility;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;

/** Bullets fired by infinite_craft:shoot are arrows tagged with ShootAbility.BULLET_TAG */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin {

	@Inject(method = "onEntityHit", at = @At("HEAD"))
	private void infiniteCraft$bulletHitsEveryTime(EntityHitResult entityHitResult, CallbackInfo ci) {
		if (((PersistentProjectileEntity) (Object) this).getCommandTags().contains(ShootAbility.BULLET_TAG)) {
			ShootAbility.onBulletHitEntity(entityHitResult.getEntity());
		}
	}

	// a chain gun would otherwise leave hundreds of arrows stuck in the walls
	@Inject(method = "onBlockHit", at = @At("TAIL"))
	private void infiniteCraft$bulletVanishesInBlocks(BlockHitResult blockHitResult, CallbackInfo ci) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		if (self.getCommandTags().contains(ShootAbility.BULLET_TAG)) {
			self.discard();
		}
	}
}
