package com.infinite_craft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.vehicle.FireVehicleC2SPayload;
import com.infinite_craft.vehicle.VehicleEntity;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Right click while driving a vehicle fires its gun, instead of using the item in hand or the block looked at */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

	@Shadow
	public ClientPlayerEntity player;

	@Shadow
	private int itemUseCooldown;

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void infiniteCraft$fireVehicle(CallbackInfo ci) {
		if (player != null && player.getVehicle() instanceof VehicleEntity vehicle && vehicle.getControllingPassenger() == player) {
			ClientPlayNetworking.send(FireVehicleC2SPayload.INSTANCE);
			// held right click repeats every 4 ticks, as for hand-held guns; the gun's own interval paces the shots
			itemUseCooldown = 4;
			ci.cancel();
		}
	}
}
