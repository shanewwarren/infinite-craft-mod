package com.infinite_craft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.infinite_craft.networking.InfiniteCraftClientNetworking;

import com.infinite_craft.vehicle.VehicleEntityModel;
import com.infinite_craft.vehicle.VehicleEntityRenderer;
import com.infinite_craft.vehicle.Vehicles;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.render.entity.EntityRendererFactories;

public class InfiniteCraftClient implements ClientModInitializer {
	
		public static final Logger LOGGER = LoggerFactory.getLogger(InfiniteCraft.MOD_ID);

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		InfiniteCraftClientNetworking.register();
		GlowColorTintSource.register();
		EntityModelLayerRegistry.registerModelLayer(VehicleEntityModel.TANK_LAYER, VehicleEntityModel::getTankModelData);
		EntityModelLayerRegistry.registerModelLayer(VehicleEntityModel.CAR_LAYER, VehicleEntityModel::getCarModelData);
		EntityRendererFactories.register(Vehicles.VEHICLE, VehicleEntityRenderer::new);
	}
}