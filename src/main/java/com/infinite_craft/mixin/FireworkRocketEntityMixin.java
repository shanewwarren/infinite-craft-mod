package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.Blast;
import com.infinite_craft.ability.ShootAbility;
import com.infinite_craft.ability.ShootComponent;

import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Rockets fired by infinite_craft:shoot are fireworks whose item carries the gun's shoot settings.
 * They blast wherever they land: on a mob, on a block, or in the air at the end of their flight
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

	@Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$rocketHitsMob(EntityHitResult entityHitResult, CallbackInfo ci) {
		FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
		ShootComponent shoot = self.getStack().get(Abilities.SHOOT);
		if (shoot != null && self.getEntityWorld() instanceof ServerWorld world) {
			ShootAbility.onRocketHitEntity(world, self, shoot, entityHitResult.getEntity());
			self.discard();
			ci.cancel();
		}
	}

	// a firework without firework stars would slide along the block instead of stopping
	@Inject(method = "onBlockHit", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$rocketHitsBlock(BlockHitResult blockHitResult, CallbackInfo ci) {
		FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
		ShootComponent shoot = self.getStack().get(Abilities.SHOOT);
		if (shoot != null && self.getEntityWorld() instanceof ServerWorld world) {
			Blast.at(world, blockHitResult.getPos(), shoot, self, self.getOwner(), null);
			self.discard();
			ci.cancel();
		}
	}

	// end of flight: vanilla's own burst, which a firework without stars makes silently
	@Inject(method = "explodeAndRemove", at = @At("HEAD"))
	private void infiniteCraft$rocketBurstsInTheAir(ServerWorld world, CallbackInfo ci) {
		FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
		ShootComponent shoot = self.getStack().get(Abilities.SHOOT);
		if (shoot != null) {
			Blast.at(world, self.getEntityPos(), shoot, self, self.getOwner(), null);
		}
	}
}
