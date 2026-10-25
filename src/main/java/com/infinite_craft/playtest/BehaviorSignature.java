package com.infinite_craft.playtest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;

/**
 * What an item does, ignoring its name, lore, looks and numbers: two items with the same signature play the same.
 * E.g. `shoot(beam)` for any plain beam gun, `base(sword) + enchantments(sharpness)` for any sharp sword.
 * The eval's variety score, the playtest log and its report all use it, so it's the ruler for sameness:
 * new abilities add tokens for their own fields, but the tokens below never change, or old scores stop comparing
 * (ClaudeCraftEval.baselineVarietyIsPinned checks that)
 */
public class BehaviorSignature {

	private static final List<String> MATERIALS = List.of("wooden_", "stone_", "iron_", "golden_", "diamond_", "netherite_",
		"copper_", "leather_", "chainmail_");

	private static final Set<String> FLAGS = Set.of("tool", "glider", "death_protection", "blocks_attacks", "damage_resistant");

	/** The item as NBT: {id, count, components}. "" for a plain vanilla item, which only does what that item does */
	public static String of(NbtCompound item) {
		NbtCompound components = item.getCompound("components").orElse(new NbtCompound());
		if (components.isEmpty()) return "";
		Set<String> tokens = new TreeSet<>();
		String id = item.getString("id").orElse("");
		if (!id.equals("infinite_craft:custom_crafted_item")) {
			tokens.add("base(" + material(shortId(id)) + ")");
		}
		components.getCompound("infinite_craft:shoot").ifPresent(shoot -> {
			String kind = shoot.getString("kind").orElse("beam");
			StringBuilder token = new StringBuilder("shoot(").append(kind);
			if (kind.equals("rocket") || shoot.getFloat("explode").orElse(0f) > 0) token.append(",explode");
			if (shoot.getBoolean("lightning").orElse(false)) token.append(",lightning");
			if (shoot.getBoolean("fire").orElse(false)) token.append(",fire");
			tokens.add(token.append(")").toString());
		});
		components.getCompound("infinite_craft:vehicle").ifPresent(vehicle -> tokens.add("vehicle(" + vehicle.getString("body").orElse("tank") + ")"));
		for (String plain : List.of("launch", "dash", "thrust")) {
			if (components.contains("infinite_craft:" + plain)) tokens.add(plain);
		}
		components.getCompound("infinite_craft:aura").ifPresent(aura -> tokens.add(group("aura", ids(aura.getList("effects")))));
		components.getCompound("minecraft:consumable").ifPresent(consumable -> {
			Set<String> parts = new TreeSet<>();
			for (NbtCompound effect : compounds(consumable.getList("on_consume_effects"))) {
				switch (shortId(effect.getString("type").orElse(""))) {
					case "apply_effects" -> parts.addAll(ids(effect.getList("effects")));
					case "teleport_randomly" -> parts.add("teleport");
					case "clear_all_effects" -> parts.add("clear_effects");
					case "remove_effects" -> parts.add("remove_effects");
					default -> {}
				}
			}
			tokens.add(group("consumable", parts));
		});
		if (components.contains("minecraft:food")) tokens.add("food");
		// a plain list, or the older {modifiers:[...]}
		Optional<NbtList> modifiers = components.getList("minecraft:attribute_modifiers")
			.or(() -> components.getCompound("minecraft:attribute_modifiers").flatMap(c -> c.getList("modifiers")));
		modifiers.ifPresent(list -> tokens.add(group("attributes",
			compounds(Optional.of(list)).stream().map(m -> shortId(m.getString("type").orElse(""))).collect(Collectors.toCollection(TreeSet::new)))));
		// ids straight to levels, or the older {levels:{...}}
		components.getCompound("minecraft:enchantments").ifPresent(enchantments -> {
			NbtCompound levels = enchantments.getCompound("levels").orElse(enchantments);
			if (!levels.isEmpty()) {
				tokens.add(group("enchantments", levels.getKeys().stream().map(BehaviorSignature::shortId).collect(Collectors.toCollection(TreeSet::new))));
			}
		});
		components.getCompound("minecraft:equippable").ifPresent(equippable -> tokens.add("wear(" + equippable.getString("slot").orElse("head") + ")"));
		for (String flag : FLAGS) {
			if (components.contains("minecraft:" + flag)) tokens.add(flag);
		}
		return String.join(" + ", tokens);
	}

	public static String of(String nbt) throws CommandSyntaxException {
		return of(StringNbtReader.readCompound(nbt));
	}

	public static String of(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
		if (stack.isEmpty()) return "";
		NbtElement nbt = ItemStack.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), stack).result().orElse(null);
		return nbt instanceof NbtCompound item ? of(item) : "";
	}

	private static String group(String name, Set<String> parts) {
		return parts.isEmpty() ? name : name + "(" + String.join(",", parts) + ")";
	}

	private static Set<String> ids(Optional<NbtList> list) {
		return compounds(list).stream().map(e -> e.getString("id").map(BehaviorSignature::shortId).orElse(""))
			.filter(s -> !s.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
	}

	private static List<NbtCompound> compounds(Optional<NbtList> list) {
		return list.map(l -> l.stream().filter(NbtCompound.class::isInstance).map(NbtCompound.class::cast).toList()).orElse(List.of());
	}

	private static String shortId(String id) {
		return id.startsWith("minecraft:") ? id.substring(10) : id;
	}

	private static String material(String path) {
		for (String material : MATERIALS) {
			if (path.startsWith(material)) return path.substring(material.length());
		}
		return path;
	}
}
