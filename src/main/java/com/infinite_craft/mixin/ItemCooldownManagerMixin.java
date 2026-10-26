package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.infinite_craft.playtest.CraftId;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Vanilla keys a cooldown by item id, and every crafted item is infinite_craft:custom_crafted_item, so one item's
 * cooldown would lock all the others. Each craft gets its own (see {@link CraftId#cooldownGroup})
 */
@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownManagerMixin {

	@Inject(method = "getGroup", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$ownCooldown(ItemStack stack, CallbackInfoReturnable<Identifier> cir) {
		CraftId.cooldownGroup(stack).ifPresent(cir::setReturnValue);
	}
}
