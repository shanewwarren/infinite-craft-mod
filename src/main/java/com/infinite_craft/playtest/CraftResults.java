package com.infinite_craft.playtest;

import java.util.ArrayList;
import java.util.List;

import com.infinite_craft.element.ElementComponentType;
import com.infinite_craft.element.ElementData;
import com.infinite_craft.element.PlayerEntityExt;
import com.infinite_craft.element.globaldata.GlobalDiscoveringDataManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.BlockPos;

/** What happens when a craft comes back: stamp it, hand it over (or drop it at the table), and log it once */
public class CraftResults {

	/** About the craft, for the log. verdict: clean, element, or "partial: <what Minecraft dropped>" */
	public record Meta(List<PlaytestLog.Input> inputs, long latencyMs, double costUsd, String verdict) {}

	/** Runs on the server thread. player is null, or dead, when they left before the result came back */
	public static void deliver(ServerPlayerEntity player, ServerWorld world, BlockPos table, ItemStack result, int copies, Meta meta) {
		ItemStack stamped = result.copy();
		String id = CraftId.stampIfCustom(stamped).orElse(null);
		boolean present = player != null && player.isAlive();
		for (int i = 0; i < copies; i++) {
			ItemStack copy = stamped.copy();
			if (present) {
				player.sendMessage(Text.translatableWithFallback("chat.message.infinite_craft.craft.success", "Crafted: ")
					.setStyle(Style.EMPTY.withColor(TextColor.parse("lime").result().orElse(TextColor.fromRgb(0xbfff00))))
					.append(describe(copy).setStyle(Style.EMPTY.withColor(TextColor.parse("white").result().orElse(TextColor.fromRgb(0xffffff))))),
					false);
				if (ElementData.isElement(copy)) {
					ElementComponentType element = ElementData.fromItem(copy).get().generateElementComponent();
					((PlayerEntityExt) player).getDiscoveringData().add(element);
					GlobalDiscoveringDataManager.get(world.getServer()).getDiscovered().add(element);
				}
			} else {
				world.spawnEntity(new ItemEntity(world, table.getX() + 0.5, table.getY() + 1, table.getZ() + 0.5, copy));
			}
		}
		if (present) {
			CraftHandoff.Placed first = CraftHandoff.give(player, stamped, copies);
			CraftHandoff.announce(player, stamped, first);
		}

		List<String> components = new ArrayList<>();
		String model = null;
		NbtElement nbt = ItemStack.CODEC.encodeStart(world.getRegistryManager().getOps(NbtOps.INSTANCE), stamped).result().orElse(null);
		if (nbt instanceof NbtCompound item) {
			NbtCompound encoded = item.getCompound("components").orElse(new NbtCompound());
			encoded.getKeys().stream().filter(k -> !k.equals("infinite_craft:craft_id")).sorted().forEach(components::add);
			model = encoded.getString("minecraft:item_model").orElse(null);
		}
		PlaytestLog.craft(new PlaytestLog.Craft(id, player != null ? player.getGameProfile().name() : "?", meta.inputs(),
			stamped.getName().getString(), Registries.ITEM.getId(stamped.getItem()).toString(), model, components,
			BehaviorSignature.of(stamped, world.getRegistryManager()), meta.verdict(), meta.latencyMs(), meta.costUsd(), copies));
	}

	/** The ingredients, with any custom name: a renamed ingredient is the player's wish */
	public static List<PlaytestLog.Input> inputs(List<ItemStack> used) {
		return used.stream().map(stack -> new PlaytestLog.Input(Registries.ITEM.getId(stack.getItem()).toString(),
			stack.contains(DataComponentTypes.CUSTOM_NAME) ? stack.get(DataComponentTypes.CUSTOM_NAME).getString() : null,
			stack.getCount())).toList();
	}

	/** "N * name", showing the item on hover */
	public static MutableText describe(ItemStack stack) {
		return Text.literal(stack.getCount() + " * ")
			.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowItem(stack)))
			.append(stack.getName());
	}
}
