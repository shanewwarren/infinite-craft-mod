package com.infinite_craft.playtest;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinite_craft.InfiniteCraft;

/**
 * Structured lines in latest.log for tools/playtest/report.py: `[InfiniteCraft/playtest] {"event":...,"t":...}`.
 * Uses are counted in memory: the first use of each kind per craft gets a line, the rest go out once a minute
 * as one `uses` line, so a minigun held down doesn't write a line per shot
 */
public class PlaytestLog {

	public static final String TAG = "[InfiniteCraft/playtest] ";

	/** Where lines go; tests swap it */
	static Consumer<String> sink = InfiniteCraft.LOGGER::info;

	private static final Gson GSON = new Gson();

	/** One ingredient; name is its custom name, i.e. what the player wished for, or null */
	public record Input(String item, String name, int count) {}

	/** id is null for results that aren't stamped (plain vanilla items, elements) */
	public record Craft(String id, String player, List<Input> inputs, String name, String base, String model,
			List<String> components, String signature, String verdict, long latencyMs, double costUsd, int count) {}

	private record Use(String player, String id) {}

	private static final Set<String> firstUses = new HashSet<>();
	private static final Map<Use, Map<String, Integer>> counts = new LinkedHashMap<>();

	public static void craft(Craft craft) {
		write("craft", GSON.toJsonTree(craft).getAsJsonObject());
	}

	public static void craftFailed(String player, List<Input> inputs, String error) {
		JsonObject json = new JsonObject();
		json.addProperty("player", player);
		json.add("inputs", GSON.toJsonTree(inputs));
		json.addProperty("error", error);
		write("craft_failed", json);
	}

	/** kind: use, hit, wear, fire */
	public static void use(String player, String id, String kind) {
		boolean first;
		synchronized (PlaytestLog.class) {
			counts.computeIfAbsent(new Use(player, id), u -> new LinkedHashMap<>()).merge(kind, 1, Integer::sum);
			first = firstUses.add(id + "\0" + kind);
		}
		if (first) {
			JsonObject json = new JsonObject();
			json.addProperty("player", player);
			json.addProperty("id", id);
			json.addProperty("kind", kind);
			write("use", json);
		}
	}

	/** Writes how often each craft was used since the last flush */
	public static void flushUses() {
		Map<Use, Map<String, Integer>> flushed;
		synchronized (PlaytestLog.class) {
			flushed = new LinkedHashMap<>(counts);
			counts.clear();
		}
		flushed.forEach((use, kinds) -> {
			JsonObject json = new JsonObject();
			json.addProperty("player", use.player());
			json.addProperty("id", use.id());
			json.add("counts", GSON.toJsonTree(kinds));
			write("uses", json);
		});
	}

	public static void drop(String player, String id, int count) {
		JsonObject json = new JsonObject();
		json.addProperty("player", player);
		json.addProperty("id", id);
		json.addProperty("count", count);
		write("drop", json);
	}

	/** held: craft id to how many the player has. reason: periodic, left or stopping */
	public static void snapshot(String player, Map<String, Integer> held, String reason) {
		JsonObject json = new JsonObject();
		json.addProperty("player", player);
		json.add("held", GSON.toJsonTree(held));
		json.addProperty("reason", reason);
		write("snapshot", json);
	}

	static synchronized void reset() {
		firstUses.clear();
		counts.clear();
	}

	private static void write(String event, JsonObject fields) {
		JsonObject json = new JsonObject();
		json.addProperty("event", event);
		json.addProperty("t", System.currentTimeMillis());
		for (Map.Entry<String, JsonElement> field : fields.entrySet()) {
			json.add(field.getKey(), field.getValue());
		}
		sink.accept(TAG + GSON.toJson(json));
	}
}
