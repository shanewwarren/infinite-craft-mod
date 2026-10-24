package com.infinite_craft.ability;

import com.infinite_craft.InfiniteCraft;

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

	public static void initialize() {
		ComponentTooltipAppenderRegistry.addBefore(DataComponentTypes.ATTRIBUTE_MODIFIERS, SHOOT);
	}
}
