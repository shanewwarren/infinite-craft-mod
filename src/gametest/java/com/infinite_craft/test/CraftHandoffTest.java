package com.infinite_craft.test;

import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.playtest.CraftHandoff;
import com.infinite_craft.playtest.CraftId;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

/**
 * A finished craft goes into the player's hand: the selected slot if it's free, else the first free hotbar slot
 * (which gets selected), else the inventory, else at their feet. It never replaces what's already in a slot
 */
public class CraftHandoffTest {

	static ItemStack rayGun(TestContext context) {
		return ThrustTest.parse(context, AiPrompt.POWER_EXAMPLES.stream().filter(e -> e.contains("Laser Blaster")).findFirst().orElseThrow());
	}

	static ItemStack energyDrink(TestContext context) {
		return ThrustTest.parse(context, AiPrompt.POWER_EXAMPLES.stream().filter(e -> e.contains("Energy Drink")).findFirst().orElseThrow());
	}

	private static void fill(PlayerEntity player, int from, int to) {
		for (int slot = from; slot < to; slot++) {
			player.getInventory().setStack(slot, new ItemStack(Items.DIRT, 7));
		}
	}

	private static void assertDirt(TestContext context, PlayerEntity player, int from, int to) {
		for (int slot = from; slot < to; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			context.assertTrue(stack.isOf(Items.DIRT) && stack.getCount() == 7,
				Text.literal("slot %d was overwritten: %s".formatted(slot, stack)));
		}
	}

	@GameTest
	public void emptyHandGetsIt(TestContext context) {
		PlayerEntity player = CraftedItemUseTest.player(context);
		CraftHandoff.Placed placed = CraftHandoff.give(player, rayGun(context));
		context.assertEquals(new CraftHandoff.Placed(CraftHandoff.Where.SELECTED, 0), placed, Text.literal("placed"));
		context.assertTrue(player.getInventory().getStack(0).isOf(rayGun(context).getItem()), Text.literal("the gun isn't in slot 0"));
		context.complete();
	}

	@GameTest
	public void busyHandGoesToFirstEmptyHotbarSlot(TestContext context) {
		PlayerEntity player = CraftedItemUseTest.player(context);
		fill(player, 0, 3);
		CraftHandoff.Placed placed = CraftHandoff.give(player, rayGun(context));
		context.assertEquals(new CraftHandoff.Placed(CraftHandoff.Where.HOTBAR, 3), placed, Text.literal("placed"));
		context.assertEquals(3, player.getInventory().getSelectedSlot(), Text.literal("selected slot"));
		assertDirt(context, player, 0, 3);
		context.complete();
	}

	@GameTest
	public void fullHotbarGoesToInventory(TestContext context) {
		PlayerEntity player = CraftedItemUseTest.player(context);
		fill(player, 0, PlayerInventory.getHotbarSize());
		player.getInventory().setSelectedSlot(4);
		CraftHandoff.Placed placed = CraftHandoff.give(player, rayGun(context));
		context.assertEquals(CraftHandoff.Where.INVENTORY, placed.where(), Text.literal("placed"));
		context.assertEquals(4, player.getInventory().getSelectedSlot(), Text.literal("selected slot"));
		assertDirt(context, player, 0, PlayerInventory.getHotbarSize());
		context.assertTrue(player.getInventory().getStack(placed.slot()).isOf(rayGun(context).getItem()),
			Text.literal("the gun isn't in slot " + placed.slot()));
		context.complete();
	}

	@GameTest
	public void fullInventoryDrops(TestContext context) {
		PlayerEntity player = CraftedItemUseTest.player(context);
		fill(player, 0, PlayerInventory.MAIN_SIZE);
		player.getInventory().setStack(PlayerInventory.OFF_HAND_SLOT, new ItemStack(Items.DIRT, 7));
		CraftHandoff.Placed placed = CraftHandoff.give(player, rayGun(context));
		context.assertEquals(new CraftHandoff.Placed(CraftHandoff.Where.DROPPED, -1), placed, Text.literal("placed"));
		assertDirt(context, player, 0, PlayerInventory.MAIN_SIZE);
		boolean dropped = !context.getWorld().getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(3),
			e -> e.getStack().isOf(rayGun(context).getItem())).isEmpty();
		context.assertTrue(dropped, Text.literal("the gun wasn't dropped"));
		context.complete();
	}

	@GameTest
	public void secondCopyStacks(TestContext context) {
		PlayerEntity player = CraftedItemUseTest.player(context);
		ItemStack drink = energyDrink(context);
		CraftId.stampIfCustom(drink);
		CraftHandoff.give(player, drink.copy());
		CraftHandoff.Placed second = CraftHandoff.give(player, drink.copy());
		context.assertEquals(new CraftHandoff.Placed(CraftHandoff.Where.SELECTED, 0), second, Text.literal("second copy"));
		context.assertEquals(2, player.getInventory().getStack(0).getCount(), Text.literal("drinks in slot 0"));
		context.assertTrue(player.getInventory().getStack(1).isEmpty(), Text.literal("the second drink took its own slot"));
		context.complete();
	}

	@GameTest
	public void cursorStackIsUntouched(TestContext context) {
		PlayerEntity player = CraftedItemUseTest.player(context);
		player.currentScreenHandler.setCursorStack(new ItemStack(Items.DIRT, 3));
		CraftHandoff.give(player, rayGun(context));
		ItemStack cursor = player.currentScreenHandler.getCursorStack();
		context.assertTrue(cursor.isOf(Items.DIRT) && cursor.getCount() == 3, Text.literal("cursor changed: " + cursor));
		context.complete();
	}
}
