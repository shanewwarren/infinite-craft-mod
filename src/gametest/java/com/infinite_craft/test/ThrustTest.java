package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.player;

import com.infinite_craft.process.InfiniteCraftProcess;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

/**
 * Wearing an item with infinite_craft:thrust and holding jump flies up; letting go falls back down.
 * The jetpack's own client-side flight is in ThrustClientGameTest
 */
public class ThrustTest {

	static final String JETPACK = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:jetpack","minecraft:equippable":{slot:"chest"},"infinite_craft:thrust":{power:1.0f}}}""";

	@GameTest(maxTicks = 20, skyAccess = true)
	public void holdingJumpWithAJetpackFliesUp(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		player.equipStack(EquipmentSlot.CHEST, parse(context, JETPACK));
		double start = player.getY();

		double climbed = holdJump(player, 40) - start;
		context.assertTrue(climbed > 6, Text.literal("climbed only %.1f blocks in 2s".formatted(climbed)));

		player.setJumping(false);
		for (int t = 0; t < 80; t++) CraftedItemUseTest.tick(player);
		context.assertTrue(player.getY() < start + 1, Text.literal("didn't come back down after letting go: %.1f above the floor".formatted(player.getY() - start)));
		context.complete();
	}

	@GameTest(maxTicks = 20, skyAccess = true)
	public void holdingJumpWithoutAJetpackOnlyHops(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		double start = player.getY();
		double highest = start;
		for (int t = 0; t < 40; t++) {
			player.setJumping(true);
			CraftedItemUseTest.tick(player);
			highest = Math.max(highest, player.getY());
		}
		context.assertTrue(highest - start < 1.5, Text.literal("rose %.1f blocks without a jetpack".formatted(highest - start)));
		context.complete();
	}

	/** Holds jump for some ticks and returns the height reached */
	static double holdJump(PlayerEntity player, int ticks) {
		for (int t = 0; t < ticks; t++) {
			player.setJumping(true);
			CraftedItemUseTest.tick(player);
		}
		return player.getY();
	}

	static ItemStack parse(TestContext context, String nbt) {
		try {
			return InfiniteCraftProcess.parseItemStackFromNbt(nbt.strip(), context.getWorld().getRegistryManager());
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}
