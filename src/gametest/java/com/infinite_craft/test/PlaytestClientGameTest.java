package com.infinite_craft.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.playtest.CraftId;
import com.infinite_craft.playtest.CraftResults;
import com.infinite_craft.playtest.PlaytestLog;
import com.infinite_craft.process.InfiniteCraftProcess;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * A real client gets a crafted Ray Gun through the same code a craft uses: it lands in the first free hotbar
 * slot with a "You made" title, and firing it, wearing a crafted chestplate, throwing the gun away and leaving
 * the world all end up in latest.log for tools/playtest/report.py. No Claude call: the result is the prompt's example.
 * Screenshots land in build/run/clientGameTest/screenshots
 */
@SuppressWarnings("UnstableApiUsage")
public class PlaytestClientGameTest implements FabricClientGameTest {

	private static final String RAY_GUN = AiPrompt.POWER_EXAMPLES.stream().filter(e -> e.contains("Laser Blaster")).findFirst().orElseThrow().strip();

	private static final CraftResults.Meta WISH = new CraftResults.Meta(List.of(
		new PlaytestLog.Input("minecraft:iron_ingot", "Ray Gun", 1), new PlaytestLog.Input("minecraft:redstone", null, 1)), 4200, 0.04, "clean");

	@Override
	public void runTest(ClientGameTestContext context) {
		if (ClientTestFilter.skip(getClass())) return;
		String gunId;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set noon");
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 7");
			server.runCommand("execute as @a at @s run fill ~-4 ~ ~-4 ~4 ~4 ~10 minecraft:air");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(10);

			server.runOnServer(s -> {
				ServerPlayerEntity player = s.getPlayerManager().getPlayerList().get(0);
				try {
					ItemStack gun = InfiniteCraftProcess.parseItemStackFromNbt(RAY_GUN, s.getRegistryManager());
					CraftResults.deliver(player, player.getEntityWorld(), player.getBlockPos(), gun, 1, WISH);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			context.waitTicks(5);
			context.takeScreenshot("craft-handoff-title");

			int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
			gunId = context.computeOnClient(client -> CraftId.of(client.player.getInventory().getStack(1)).orElse(null));
			boolean dirtKept = context.computeOnClient(client -> {
				ItemStack first = client.player.getInventory().getStack(0);
				return first.isOf(Items.DIRT) && first.getCount() == 7;
			});
			System.out.printf("[PlaytestClientGameTest] selected slot %d, gun id %s in slot 1, dirt kept: %s%n", selected, gunId, dirtKept);
			if (selected != 1 || gunId == null) throw new AssertionError("the crafted gun isn't in the selected hotbar slot");
			if (!dirtKept) throw new AssertionError("the hand-off changed the dirt in slot 0");

			// a target, so the gun fires at something
			server.runCommand("execute as @a at @s rotated ~ 0 run summon minecraft:husk ^ ^ ^6 {NoAI:1b}");
			context.waitTicks(5);
			context.getInput().holdKey(options -> options.useKey);
			context.waitTicks(20);
			context.getInput().releaseKey(options -> options.useKey);
			server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate[infinite_craft:craft_id=\"wear-test\"]");
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.dropKey);
			context.waitTicks(10);
		}

		// leaving the world wrote the "left" snapshot
		Path log = Path.of("logs", "latest.log");
		List<JsonObject> events = List.of();
		for (int tries = 0; tries < 40; tries++) {
			events = playtestEvents(log);
			if (events.stream().anyMatch(e -> e.get("event").getAsString().equals("snapshot") && e.get("reason").getAsString().equals("left"))) break;
			context.waitTick();
		}
		String id = gunId;
		boolean crafted = events.stream().anyMatch(e -> is(e, "craft", id) && e.get("signature").getAsString().equals("shoot(beam)"));
		boolean used = events.stream().anyMatch(e -> is(e, "use", id) && e.get("kind").getAsString().equals("use"));
		boolean dropped = events.stream().anyMatch(e -> is(e, "drop", id));
		boolean worn = events.stream().anyMatch(e -> is(e, "use", "wear-test") && e.get("kind").getAsString().equals("wear"));
		JsonObject left = events.stream().filter(e -> e.get("event").getAsString().equals("snapshot") && e.get("reason").getAsString().equals("left"))
			.reduce((a, b) -> b).orElse(null);
		boolean goneAtEnd = left != null && !left.getAsJsonObject("held").has(id);
		System.out.printf("[PlaytestClientGameTest] log: crafted %s, used %s, worn %s, dropped %s, left snapshot without it %s%n",
			crafted, used, worn, dropped, goneAtEnd);
		if (!crafted || !used || !worn || !dropped || !goneAtEnd) {
			throw new AssertionError("latest.log is missing playtest events for the gun " + id);
		}
	}

	private static boolean is(JsonObject event, String name, String id) {
		return event.get("event").getAsString().equals(name) && event.has("id") && event.get("id").getAsString().equals(id);
	}

	private static List<JsonObject> playtestEvents(Path log) {
		try {
			return Files.readAllLines(log, StandardCharsets.UTF_8).stream()
				.filter(l -> l.contains(PlaytestLog.TAG))
				.map(l -> JsonParser.parseString(l.substring(l.indexOf(PlaytestLog.TAG) + PlaytestLog.TAG.length())).getAsJsonObject())
				.toList();
		} catch (IOException e) {
			return List.of();
		}
	}
}
