package com.infinite_craft.vehicle;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.ShootAbility;
import com.infinite_craft.ability.ShootComponent;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

/**
 * Fires a vehicle's gun (its item's infinite_craft:shoot) for its driver, from the barrel tip where the driver looks.
 * The client asks with {@link FireVehicleC2SPayload}; the server checks the sender is driving
 */
public class VehicleFire {

	static void initialize() {
		PayloadTypeRegistry.playC2S().register(FireVehicleC2SPayload.ID, FireVehicleC2SPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FireVehicleC2SPayload.ID, (payload, context) -> fire(context.player()));
	}

	/**
	 * Whether the player's right click fires a vehicle's gun instead of its usual use: they're driving one that has a gun.
	 * A gunless car leaves right click alone, so its driver can still eat and drink
	 */
	public static boolean takesRightClick(PlayerEntity player) {
		return player.getVehicle() instanceof VehicleEntity vehicle && vehicle.getControllingPassenger() == player
			&& vehicle.getItem().contains(Abilities.SHOOT);
	}

	/** True if a shot went off: not when the rider isn't driving, the vehicle has no gun, or it's reloading */
	public static boolean fire(PlayerEntity rider) {
		if (!(rider.getVehicle() instanceof VehicleEntity vehicle) || vehicle.getControllingPassenger() != rider) {
			return false;
		}
		ItemStack gun = vehicle.getItem();
		ShootComponent shoot = gun.get(Abilities.SHOOT);
		if (shoot == null) {
			return false;
		}
		return ShootAbility.fire(rider.getEntityWorld(), rider, gun, shoot, vehicle.muzzle(rider.getPitch(), rider.getYaw())) == ActionResult.SUCCESS;
	}
}
