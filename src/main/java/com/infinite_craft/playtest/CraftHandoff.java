package com.infinite_craft.playtest;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Puts a finished craft in the player's hand, so they don't have to hunt for it in the inventory:
 * the selected slot if it's free, else the first free hotbar slot (which gets selected), else the inventory,
 * else at their feet. Never replaces what's in a slot, and never touches the stack on the cursor
 */
public class CraftHandoff {

	public enum Where { SELECTED, HOTBAR, INVENTORY, DROPPED }

	/** Where the stack went first; slot is -1 when it was dropped */
	public record Placed(Where where, int slot) {}

	/** Several copies of one result, e.g. from two of each ingredient; returns where the first went */
	public static Placed give(PlayerEntity player, ItemStack stack, int copies) {
		Placed first = null;
		for (int i = 0; i < copies; i++) {
			// only the first copy may change the selected slot: announce tells the client that one
			Placed placed = give(player, stack.copy(), i == 0);
			if (first == null) first = placed;
		}
		return first;
	}

	public static Placed give(PlayerEntity player, ItemStack stack) {
		return give(player, stack, true);
	}

	private static Placed give(PlayerEntity player, ItemStack stack, boolean select) {
		PlayerInventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		Placed placed = null;
		if (fits(inventory.getStack(selected), stack)) {
			merge(inventory, selected, stack);
			placed = new Placed(Where.SELECTED, selected);
		}
		// another hotbar slot already holding the same thing, then the first free one
		for (boolean wantEmpty : new boolean[] {false, true}) {
			for (int slot = 0; slot < PlayerInventory.getHotbarSize() && !stack.isEmpty(); slot++) {
				ItemStack there = inventory.getStack(slot);
				if (slot != selected && there.isEmpty() == wantEmpty && fits(there, stack)) {
					merge(inventory, slot, stack);
					if (placed == null) placed = new Placed(Where.HOTBAR, slot);
				}
			}
		}
		if (select && placed != null && placed.where() == Where.HOTBAR) {
			inventory.setSelectedSlot(placed.slot());
		}
		if (!stack.isEmpty()) {
			ItemStack inserted = stack.copy();
			inventory.insertStack(stack);
			if (placed == null && stack.getCount() < inserted.getCount()) {
				placed = new Placed(Where.INVENTORY, inventory.getSlotWithStack(inserted));
			}
		}
		if (!stack.isEmpty()) {
			player.dropItem(stack, false);
			if (placed == null) placed = new Placed(Where.DROPPED, -1);
		}
		return placed;
	}

	/** Tells the player what they made and, if it isn't in their hand, where it is */
	public static void announce(ServerPlayerEntity player, ItemStack stack, Placed placed) {
		if (placed.where() == Where.SELECTED || placed.where() == Where.HOTBAR) {
			player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(placed.slot()));
		}
		Text subtitle = switch (placed.where()) {
			case SELECTED, HOTBAR -> Text.translatableWithFallback("title.infinite_craft.you_made", "You made");
			case INVENTORY -> Text.translatableWithFallback("title.infinite_craft.in_inventory", "You made (it's in your inventory)");
			case DROPPED -> Text.translatableWithFallback("title.infinite_craft.dropped", "You made (it's at your feet)");
		};
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
		player.networkHandler.sendPacket(new TitleS2CPacket(stack.getName()));
		player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4f, 1.4f);
	}

	private static boolean fits(ItemStack there, ItemStack stack) {
		return there.isEmpty() || ItemStack.areItemsAndComponentsEqual(there, stack) && there.getCount() < there.getMaxCount();
	}

	private static void merge(PlayerInventory inventory, int slot, ItemStack stack) {
		ItemStack there = inventory.getStack(slot);
		if (there.isEmpty()) {
			inventory.setStack(slot, stack.split(Math.min(stack.getCount(), stack.getMaxCount())));
		} else {
			int moved = Math.min(stack.getCount(), there.getMaxCount() - there.getCount());
			there.increment(moved);
			stack.decrement(moved);
		}
	}
}
