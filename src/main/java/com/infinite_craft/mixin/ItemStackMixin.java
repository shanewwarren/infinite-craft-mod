package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.LaunchAbility;
import com.infinite_craft.ability.LaunchComponent;
import com.infinite_craft.ability.ShootAbility;
import com.infinite_craft.ability.ShootComponent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Runs abilities in place of the item's own use, for any base item. Wraps the inner Item#use call,
 * so ItemStack#use still applies minecraft:use_cooldown and the use remainder afterwards
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

	@WrapOperation(method = "use", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/item/Item;use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"))
	private ActionResult infiniteCraft$useAbilities(Item item, World world, PlayerEntity user, Hand hand, Operation<ActionResult> original) {
		ItemStack stack = (ItemStack) (Object) this;
		ShootComponent shoot = stack.get(Abilities.SHOOT);
		if (shoot != null) {
			return ShootAbility.use(world, user, hand, stack, shoot);
		}
		return original.call(item, world, user, hand);
	}

	// after a melee hit lands, on the server
	@Inject(method = "postHit", at = @At("HEAD"))
	private void infiniteCraft$launchOnHit(LivingEntity target, LivingEntity user, CallbackInfoReturnable<Boolean> cir) {
		LaunchComponent launch = ((ItemStack) (Object) this).get(Abilities.LAUNCH);
		if (launch != null && target.getEntityWorld() instanceof ServerWorld world) {
			LaunchAbility.onHit(world, target, user, launch);
		}
	}
}
