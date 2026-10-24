package com.infinite_craft;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.ShootComponent;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.render.item.tint.TintSource;
import net.minecraft.client.render.item.tint.TintSourceTypes;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.ColorHelper;

import org.jetbrains.annotations.Nullable;

/**
 * `infinite_craft:glow_color` tint for the glowing parts of the mod's crafted-item models: the first
 * minecraft:custom_model_data color if Claude set one, else the color the item shoots, else the model's default
 */
public record GlowColorTintSource(int defaultColor) implements TintSource {

	public static final MapCodec<GlowColorTintSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codecs.RGB.fieldOf("default").forGetter(GlowColorTintSource::defaultColor)
	).apply(instance, GlowColorTintSource::new));

	public static void register() {
		TintSourceTypes.ID_MAPPER.put(Identifier.of(InfiniteCraft.MOD_ID, "glow_color"), CODEC);
	}

	@Override
	public int getTint(ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity user) {
		CustomModelDataComponent customModelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (customModelData != null && customModelData.getColor(0) != null) {
			return ColorHelper.fullAlpha(customModelData.getColor(0));
		}
		ShootComponent shoot = stack.get(Abilities.SHOOT);
		if (shoot != null) {
			return ColorHelper.fullAlpha(shoot.color());
		}
		return ColorHelper.fullAlpha(defaultColor);
	}

	@Override
	public MapCodec<GlowColorTintSource> getCodec() {
		return CODEC;
	}
}
