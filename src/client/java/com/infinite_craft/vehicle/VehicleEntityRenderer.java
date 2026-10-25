package com.infinite_craft.vehicle;

import com.infinite_craft.InfiniteCraft;
import com.infinite_craft.ability.VehicleComponent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Draws a vehicle with its body's model, tinted with its color; a tank's turret follows the rider's look */
@Environment(EnvType.CLIENT)
public class VehicleEntityRenderer extends MobEntityRenderer<VehicleEntity, VehicleRenderState, VehicleEntityModel> {

	private static final Identifier TANK_TEXTURE = Identifier.of(InfiniteCraft.MOD_ID, "textures/entity/vehicle/tank.png");
	private static final Identifier CAR_TEXTURE = Identifier.of(InfiniteCraft.MOD_ID, "textures/entity/vehicle/car.png");

	private final VehicleEntityModel tankModel;
	private final VehicleEntityModel carModel;

	public VehicleEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new VehicleEntityModel(context.getPart(VehicleEntityModel.TANK_LAYER)), 1.2f);
		tankModel = model;
		carModel = new VehicleEntityModel(context.getPart(VehicleEntityModel.CAR_LAYER));
	}

	@Override
	public void render(VehicleRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
		model = state.body == VehicleComponent.Body.CAR ? carModel : tankModel;
		super.render(state, matrices, queue, camera);
	}

	@Override
	public Identifier getTexture(VehicleRenderState state) {
		return state.body == VehicleComponent.Body.CAR ? CAR_TEXTURE : TANK_TEXTURE;
	}

	@Override
	protected int getMixColor(VehicleRenderState state) {
		return 0xFF000000 | state.color;
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new VehicleRenderState();
	}

	@Override
	public void updateRenderState(VehicleEntity vehicle, VehicleRenderState state, float tickProgress) {
		super.updateRenderState(vehicle, state, tickProgress);
		VehicleComponent settings = vehicle.vehicle();
		state.body = settings.body();
		state.color = settings.color();
		LivingEntity rider = vehicle.getControllingPassenger();
		if (rider != null) {
			state.turretYaw = MathHelper.wrapDegrees(rider.getHeadYaw() - state.bodyYaw);
			state.barrelPitch = MathHelper.clamp(rider.getPitch(tickProgress), -30, 10);
		} else {
			state.turretYaw = 0;
			state.barrelPitch = 0;
		}
	}
}
