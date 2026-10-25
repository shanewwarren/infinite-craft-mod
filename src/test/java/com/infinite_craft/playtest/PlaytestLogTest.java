package com.infinite_craft.playtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The playtest log: one tagged JSON line per event, and never a line per shot */
public class PlaytestLogTest {

	private final List<String> lines = new ArrayList<>();
	private Consumer<String> original;

	@BeforeEach
	void capture() {
		PlaytestLog.reset();
		original = PlaytestLog.sink;
		PlaytestLog.sink = lines::add;
	}

	@AfterEach
	void restore() {
		PlaytestLog.sink = original;
		PlaytestLog.reset();
	}

	private static JsonObject json(String line) {
		return JsonParser.parseString(line.substring(PlaytestLog.TAG.length())).getAsJsonObject();
	}

	private List<JsonObject> events(String event) {
		return lines.stream().map(PlaytestLogTest::json).filter(j -> j.get("event").getAsString().equals(event)).toList();
	}

	@Test
	void minigunDoesNotFloodTheLog() {
		for (int i = 0; i < 600; i++) PlaytestLog.use("Sam", "ab12cd34", "use");
		PlaytestLog.flushUses();
		assertEquals(2, lines.size(), String.join("\n", lines));
		assertEquals(1, events("use").size());
		JsonObject uses = events("uses").get(0);
		assertEquals("ab12cd34", uses.get("id").getAsString());
		assertEquals(600, uses.getAsJsonObject("counts").get("use").getAsInt());
	}

	@Test
	void eachKindLogsItsFirstUse() {
		PlaytestLog.use("Sam", "ab12cd34", "use");
		PlaytestLog.use("Sam", "ab12cd34", "hit");
		PlaytestLog.use("Sam", "ab12cd34", "use");
		assertEquals(2, events("use").size());
	}

	@Test
	void flushWithNothingUsedWritesNothing() {
		PlaytestLog.flushUses();
		PlaytestLog.use("Sam", "ab12cd34", "use");
		PlaytestLog.flushUses();
		lines.clear();
		PlaytestLog.flushUses();
		assertTrue(lines.isEmpty(), String.join("\n", lines));
	}

	@Test
	void oddNamesStayOneLine() {
		String name = "Bob's \"Mega\"\nGun §c🔥";
		PlaytestLog.craft(new PlaytestLog.Craft("ab12cd34", "Sam", List.of(new PlaytestLog.Input("minecraft:stick", name, 1)),
			name, "infinite_craft:custom_crafted_item", null, List.of("infinite_craft:shoot"), "shoot(beam)", "clean", 4200, 0.04, 1));
		assertEquals(1, lines.size());
		assertFalse(lines.get(0).contains("\n"));
		JsonObject craft = json(lines.get(0));
		assertEquals(name, craft.get("name").getAsString());
		assertEquals(name, craft.getAsJsonArray("inputs").get(0).getAsJsonObject().get("name").getAsString());
	}

	@Test
	void everyLineHasTheTag() {
		PlaytestLog.craftFailed("Sam", List.of(new PlaytestLog.Input("minecraft:dirt", null, 1)), "Claude declined to craft this");
		PlaytestLog.use("Sam", "ab12cd34", "wear");
		PlaytestLog.flushUses();
		PlaytestLog.drop("Sam", "ab12cd34", 1);
		PlaytestLog.snapshot("Sam", java.util.Map.of("ab12cd34", 1), "left");
		assertEquals(5, lines.size());
		for (String line : lines) {
			assertTrue(line.startsWith("[InfiniteCraft/playtest] {"), line);
			assertTrue(json(line).has("t"), line);
		}
	}
}
