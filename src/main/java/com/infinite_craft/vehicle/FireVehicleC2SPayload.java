package com.infinite_craft.vehicle;

import com.infinite_craft.InfiniteCraft;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent while the driver holds right click: fire the vehicle's gun */
public record FireVehicleC2SPayload() implements CustomPayload {

	public static final FireVehicleC2SPayload INSTANCE = new FireVehicleC2SPayload();
	public static final Id<FireVehicleC2SPayload> ID = new Id<>(Identifier.of(InfiniteCraft.MOD_ID, "fire_vehicle"));
	public static final PacketCodec<RegistryByteBuf, FireVehicleC2SPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
