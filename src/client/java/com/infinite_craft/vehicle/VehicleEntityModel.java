package com.infinite_craft.vehicle;

import com.infinite_craft.InfiniteCraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Drawn bodies for {@link VehicleEntity}, in model pixels (16 to a block, y down, the ground at y = 24, the front at -z).
 * The UVs match the texture layout in tools/textures/make_textures.py
 */
@Environment(EnvType.CLIENT)
public class VehicleEntityModel extends EntityModel<VehicleRenderState> {

	public static final EntityModelLayer TANK_LAYER = new EntityModelLayer(Identifier.of(InfiniteCraft.MOD_ID, "vehicle"), "tank");
	public static final EntityModelLayer CAR_LAYER = new EntityModelLayer(Identifier.of(InfiniteCraft.MOD_ID, "vehicle"), "car");

	private final ModelPart turret;
	private final ModelPart barrel;

	public VehicleEntityModel(ModelPart root) {
		super(root);
		turret = root.hasChild("turret") ? root.getChild("turret") : null;
		barrel = turret == null ? null : turret.getChild("barrel");
	}

	/** Two tracks, a hull with a deck over the tracks, and a turret whose barrel aims where the rider looks */
	public static TexturedModelData getTankModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("track_left", ModelPartBuilder.create().uv(0, 0).cuboid(-16, 14, -19, 6, 10, 38), ModelTransform.NONE);
		root.addChild("track_right", ModelPartBuilder.create().uv(0, 0).mirrored().cuboid(10, 14, -19, 6, 10, 38), ModelTransform.NONE);
		root.addChild("hull", ModelPartBuilder.create().uv(0, 48).cuboid(-10, 12, -17, 20, 9, 34), ModelTransform.NONE);
		root.addChild("deck", ModelPartBuilder.create().uv(88, 0).cuboid(-14, 9, -18, 28, 3, 36), ModelTransform.NONE);
		ModelPartData turret = root.addChild("turret", ModelPartBuilder.create().uv(108, 48).cuboid(-7, -7, -7, 14, 7, 14),
			ModelTransform.origin(0, 9, 0));
		turret.addChild("barrel", ModelPartBuilder.create().uv(164, 48).cuboid(-1.5f, -1.5f, -20, 3, 3, 20),
			ModelTransform.origin(0, -4, -7));
		return TexturedModelData.of(data, 256, 128);
	}

	/** A body on four wheels with a cabin */
	public static TexturedModelData getCarModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-12, 14, -20, 24, 7, 40), ModelTransform.NONE);
		root.addChild("cabin", ModelPartBuilder.create().uv(0, 47).cuboid(-10, 7, -2, 20, 7, 16), ModelTransform.NONE);
		for (int x : new int[] {-16, 12}) {
			for (int z : new int[] {-18, 10}) {
				root.addChild("wheel_%d_%d".formatted(x, z), ModelPartBuilder.create().uv(72, 47).cuboid(x, 16, z, 4, 8, 8), ModelTransform.NONE);
			}
		}
		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public void setAngles(VehicleRenderState state) {
		super.setAngles(state);
		if (turret != null) {
			turret.yaw = state.turretYaw * MathHelper.RADIANS_PER_DEGREE;
			barrel.pitch = state.barrelPitch * MathHelper.RADIANS_PER_DEGREE;
		}
	}
}
