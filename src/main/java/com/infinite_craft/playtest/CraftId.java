package com.infinite_craft.playtest;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.element.ElementData;
import com.mojang.serialization.Codec;

import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * `infinite_craft:craft_id`: a hidden id on each crafted custom item, so the playtest log can tie uses, drops and
 * snapshots back to the craft. Copies from one craft share it and still stack. Plain vanilla results and elements
 * aren't stamped, so they keep stacking with the same items found in the world
 */
public class CraftId {

	public static final ComponentType<String> TYPE = Registry.register(
		Registries.DATA_COMPONENT_TYPE,
		Identifier.of(InfiniteCraft.MOD_ID, "craft_id"),
		ComponentType.<String>builder().codec(Codec.STRING).build()
	);

	/**
	 * Stamps a new id on a custom result (anything beyond its base item's defaults) and returns it.
	 * An id already on the stack, e.g. copied by Claude from a crafted ingredient, is removed first
	 */
	public static Optional<String> stampIfCustom(ItemStack stack) {
		stack.remove(TYPE);
		if (stack.isEmpty() || ElementData.isElement(stack) || stack.getComponentChanges().isEmpty()) return Optional.empty();
		String id = "%08x".formatted(ThreadLocalRandom.current().nextInt());
		stack.set(TYPE, id);
		return Optional.of(id);
	}

	public static Optional<String> of(ItemStack stack) {
		return Optional.ofNullable(stack.get(TYPE));
	}

	public static void initialize() {
		// registered by loading the class
	}
}
