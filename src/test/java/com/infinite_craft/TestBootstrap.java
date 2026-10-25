package com.infinite_craft;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.element.ElementItems;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryWrapper;

/** Starts Minecraft's registries once per test JVM: registering the mod's items and components twice throws */
public class TestBootstrap {

	private static RegistryWrapper.WrapperLookup registries;

	/** Stands in for a world's registries, which enchantments, sounds etc. are parsed against */
	public static synchronized RegistryWrapper.WrapperLookup registries() {
		if (registries == null) {
			SharedConstants.createGameVersion();
			Bootstrap.initialize();
			Abilities.initialize();
			InfiniteItem.initialize();
			ElementItems.initialize();
			registries = BuiltinRegistries.createWrapperLookup();
		}
		return registries;
	}
}
