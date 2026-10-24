package com.infinite_craft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.ThrustAbility;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/** Servers without allow-flight kick players who float for 4 seconds; a jetpack in use isn't floating */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

	@Shadow
	public ServerPlayerEntity player;

	@Shadow
	private int floatingTicks;

	@Inject(method = "tick", at = @At("HEAD"))
	private void infiniteCraft$jetpackIsNotFloating(CallbackInfo ci) {
		if (ThrustAbility.isThrusting(player)) {
			floatingTicks = 0;
		}
	}
}
