package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.Blast;
import com.infinite_craft.ability.ShootAbility;
import com.infinite_craft.ability.ShootComponent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
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

	@Inject(method = "onEntityHit", at = @At("RETURN"))
	private void infiniteCraft$bulletBlastsOnMobs(EntityHitResult entityHitResult, CallbackInfo ci) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		ShootComponent shoot = ShootAbility.bulletShoot(self);
		if (self.getCommandTags().contains(ShootAbility.BULLET_TAG) && shoot != null && self.getEntityWorld() instanceof ServerWorld world
				&& (shoot.explode() > 0 || shoot.lightning())) {
			Entity target = entityHitResult.getEntity();
			Blast.at(world, target.getEntityPos(), shoot, self, self.getOwner(), target);
			// a bullet that bounced off (a shield, say) would blast again where it lands
			self.discard();
		}
	}

	// a chain gun would otherwise leave hundreds of arrows stuck in the walls
	@Inject(method = "onBlockHit", at = @At("TAIL"))
	private void infiniteCraft$bulletVanishesInBlocks(BlockHitResult blockHitResult, CallbackInfo ci) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		if (self.getCommandTags().contains(ShootAbility.BULLET_TAG)) {
			ShootComponent shoot = ShootAbility.bulletShoot(self);
			if (shoot != null && self.getEntityWorld() instanceof ServerWorld world) {
				Blast.at(world, blockHitResult.getPos(), shoot, self, self.getOwner(), null);
			}
			self.discard();
		}
	}
}
