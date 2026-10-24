package com.infinite_craft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.infinite_craft.networking.InfiniteCraftClientNetworking;

import net.fabricmc.api.ClientModInitializer;

public class InfiniteCraftClient implements ClientModInitializer {
	
		public static final Logger LOGGER = LoggerFactory.getLogger(InfiniteCraft.MOD_ID);

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		InfiniteCraftClientNetworking.register();
		GlowColorTintSource.register();
	}
}