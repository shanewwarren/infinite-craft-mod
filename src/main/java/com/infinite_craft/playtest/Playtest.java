package com.infinite_craft.playtest;

import java.util.LinkedHashMap;
import java.util.Map;

import com.infinite_craft.ability.Abilities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Hooks that feed the playtest log: uses, hits and wearing crafted items, plus what's still in each inventory */
public class Playtest {

	private static final int FLUSH_TICKS = 20 * 60;
	private static final int SNAPSHOT_TICKS = 20 * 60 * 5;

	public static void initialize() {
		UseItemCallback.EVENT.register((player, world, hand) -> log(player, world, hand, "use"));
		// only vehicles act on a block (placing them); anything else right-clicked at a block either opens the door or
		// chest there, or falls through to UseItemCallback, which counts it
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
			player.getStackInHand(hand).contains(Abilities.VEHICLE) ? log(player, world, hand, "use") : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> log(player, world, hand, "hit"));
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, next) -> {
			// put on, not just worn down: armor taking damage changes the stack too
			if (entity instanceof ServerPlayerEntity player && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				CraftId.of(next).filter(id -> !CraftId.of(previous).equals(CraftId.of(next)))
					.ifPresent(id -> PlaytestLog.use(name(player), id, "wear"));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int ticks = server.getTicks();
			if (ticks % FLUSH_TICKS == 0) PlaytestLog.flushUses();
			if (ticks % SNAPSHOT_TICKS == 0) server.getPlayerManager().getPlayerList().forEach(p -> snapshot(p, "periodic"));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PlaytestLog.flushUses();
			snapshot(handler.player, "left");
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PlaytestLog.flushUses();
			server.getPlayerManager().getPlayerList().forEach(p -> snapshot(p, "stopping"));
		});
	}

	/** A player threw a crafted item out (ServerPlayerEntityMixin) */
	public static void dropped(ServerPlayerEntity player, ItemStack stack) {
		CraftId.of(stack).ifPresent(id -> PlaytestLog.drop(name(player), id, stack.getCount()));
	}

	/** Vehicles fire from the seat, not the hand (VehicleFire) */
	public static void fired(PlayerEntity rider, ItemStack vehicleItem) {
		CraftId.of(vehicleItem).ifPresent(id -> PlaytestLog.use(name(rider), id, "fire"));
	}

	private static ActionResult log(PlayerEntity player, World world, Hand hand, String kind) {
		if (!world.isClient()) {
			CraftId.of(player.getStackInHand(hand)).ifPresent(id -> PlaytestLog.use(name(player), id, kind));
		}
		return ActionResult.PASS;
	}

	private static void snapshot(ServerPlayerEntity player, String reason) {
		Map<String, Integer> held = new LinkedHashMap<>();
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			CraftId.of(stack).ifPresent(id -> held.merge(id, stack.getCount(), Integer::sum));
		}
		PlaytestLog.snapshot(name(player), held, reason);
	}

	private static String name(PlayerEntity player) {
		return player.getGameProfile().name();
	}
}
