package com.infinite_craft.vehicle;

import com.infinite_craft.ability.VehicleComponent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;

@Environment(EnvType.CLIENT)
public class VehicleRenderState extends LivingEntityRenderState {
	public VehicleComponent.Body body = VehicleComponent.Body.TANK;
	/** Hull or paint color, RGB */
	public int color = 0xFFFFFF;
	/** Where the turret points, in degrees relative to the hull */
	public float turretYaw;
	/** Barrel elevation in degrees, positive down */
	public float barrelPitch;
}
