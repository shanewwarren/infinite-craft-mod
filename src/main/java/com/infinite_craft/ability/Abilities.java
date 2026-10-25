package com.infinite_craft.ability;

import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.vehicle.Vehicles;

import net.fabricmc.fabric.api.item.v1.ComponentTooltipAppenderRegistry;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Item components for behavior vanilla components can't express. Claude puts them in a crafted item's components
 * like any other ("infinite_craft:shoot":{...}), and the mod runs them when the item is used
 */
public class Abilities {

	public static final ComponentType<ShootComponent> SHOOT = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "shoot"),
		ComponentType.<ShootComponent>builder().codec(ShootComponent.CODEC).build()
	);

	public static final ComponentType<ThrustComponent> THRUST = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "thrust"),
		ComponentType.<ThrustComponent>builder().codec(ThrustComponent.CODEC).build()
	);

	public static final ComponentType<LaunchComponent> LAUNCH = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "launch"),
		ComponentType.<LaunchComponent>builder().codec(LaunchComponent.CODEC).build()
	);

	public static final ComponentType<DashComponent> DASH = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "dash"),
		ComponentType.<DashComponent>builder().codec(DashComponent.CODEC).build()
	);

	public static final ComponentType<AuraComponent> AURA = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "aura"),
		ComponentType.<AuraComponent>builder().codec(AuraComponent.CODEC).build()
	);

	public static final ComponentType<VehicleComponent> VEHICLE = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "vehicle"),
		ComponentType.<VehicleComponent>builder().codec(VehicleComponent.CODEC).build()
	);

	public static void initialize() {
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, SHOOT);
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, THRUST);
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, LAUNCH);
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, DASH);
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, AURA);
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, VEHICLE);
		LaunchAbility.initialize();
		Blast.initialize();
		Vehicles.initialize();
	}
}
