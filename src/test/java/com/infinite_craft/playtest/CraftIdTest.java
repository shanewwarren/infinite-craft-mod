package com.infinite_craft.playtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.infinite_craft.TestBootstrap;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.element.ElementItems;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;

/** Crafted custom items carry a hidden id, so the playtest log can tie uses back to the craft */
public class CraftIdTest {

	private static RegistryWrapper.WrapperLookup registries;

	@BeforeAll
	static void bootstrap() {
		registries = TestBootstrap.registries();
	}

	@Test
	void customItemIsStamped() throws Exception {
		String rayGun = AiPrompt.POWER_EXAMPLES.stream().filter(e -> e.contains("Laser Blaster")).findFirst().orElseThrow().strip();
		ItemStack stack = ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), StringNbtReader.readCompound(rayGun)).getOrThrow();
		Optional<String> id = CraftId.stampIfCustom(stack);
		assertTrue(id.isPresent());
		assertTrue(id.get().matches("[0-9a-f]{8}"), id.get());
		assertEquals(id, CraftId.of(stack));
		ItemStack copy = stack.copy();
		assertEquals(id, CraftId.of(copy));
		assertTrue(ItemStack.areItemsAndComponentsEqual(stack, copy));
	}

	@Test
	void plainVanillaIsNotStamped() {
		ItemStack gunpowder = new ItemStack(Items.GUNPOWDER, 5);
		assertEquals(Optional.empty(), CraftId.stampIfCustom(gunpowder));
		assertTrue(ItemStack.areItemsAndComponentsEqual(gunpowder, new ItemStack(Items.GUNPOWDER)));
	}

	@Test
	void elementsAreNotStamped() {
		ItemStack fire = ElementItems.ELEMENT_FIRE.getDefaultStack();
		assertEquals(Optional.empty(), CraftId.stampIfCustom(fire));
		assertEquals(Optional.empty(), CraftId.of(fire));
	}
}
