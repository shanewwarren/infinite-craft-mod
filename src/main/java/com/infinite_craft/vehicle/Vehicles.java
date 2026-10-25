package com.infinite_craft.vehicle;

import com.infinite_craft.InfiniteCraft;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** The rideable vehicle that items with infinite_craft:vehicle place */
public class Vehicles {

	private static final RegistryKey<EntityType<?>> VEHICLE_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(InfiniteCraft.MOD_ID, "vehicle"));

	// size 1; the scale attribute grows or shrinks it, rider seat included
	public static final EntityType<VehicleEntity> VEHICLE = Registry.register(Registries.ENTITY_TYPE, VEHICLE_KEY,
		EntityType.Builder.create(VehicleEntity::new, SpawnGroup.MISC)
			.dimensions(2.0f, 1.4f)
			.passengerAttachments(1.25f)
			.maxTrackingRange(10)
			.build(VEHICLE_KEY));

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(VEHICLE, VehicleEntity.createVehicleAttributes());
	}
}
