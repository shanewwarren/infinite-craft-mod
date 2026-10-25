package com.infinite_craft.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infinite_craft.playtest.CraftId;
import com.infinite_craft.playtest.CraftResults;
import com.infinite_craft.playtest.PlaytestLog;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Every finished craft goes through CraftResults: it's stamped, handed over (or dropped at the table when the
 * player is gone, as before) and logged once for the playtest report
 */
public class CraftResultsTest {

	private static final CraftResults.Meta RAY_GUN_WISH = new CraftResults.Meta(List.of(
		new PlaytestLog.Input("minecraft:iron_ingot", "Ray Gun", 1),
		new PlaytestLog.Input("minecraft:redstone", null, 1),
		new PlaytestLog.Input("minecraft:emerald", null, 1)), 4200, 0.04, "clean");

	/** Runs deliver with the log captured; returns the playtest lines as JSON */
	private static List<JsonObject> deliver(TestContext context, ItemStack result, int copies) {
		List<String> lines = new ArrayList<>();
		Consumer<String> original = PlaytestLog.sink;
		PlaytestLog.sink = lines::add;
		try {
			CraftResults.deliver(null, context.getWorld(), table(context), result, copies, RAY_GUN_WISH);
		} finally {
			PlaytestLog.sink = original;
		}
		return lines.stream().map(l -> JsonParser.parseString(l.substring(PlaytestLog.TAG.length())).getAsJsonObject()).toList();
	}

	private static BlockPos table(TestContext context) {
		return context.getAbsolutePos(new BlockPos(4, 1, 4));
	}

	private static List<ItemEntity> atTable(TestContext context) {
		return context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(table(context)).expand(2), e -> true);
	}

	private static List<JsonObject> crafts(List<JsonObject> lines) {
		return lines.stream().filter(j -> j.get("event").getAsString().equals("craft")).toList();
	}

	@GameTest
	public void deliverLogsOneCraftLine(TestContext context) {
		List<JsonObject> crafts = crafts(deliver(context, CraftHandoffTest.rayGun(context), 1));
		context.assertEquals(1, crafts.size(), Text.literal("craft lines"));
		JsonObject craft = crafts.get(0);
		context.assertEquals("shoot(beam)", craft.get("signature").getAsString(), Text.literal("signature"));
		context.assertEquals("Ray Gun", craft.getAsJsonArray("inputs").get(0).getAsJsonObject().get("name").getAsString(), Text.literal("wish"));
		context.assertTrue(craft.get("id").getAsString().matches("[0-9a-f]{8}"), Text.literal("id " + craft.get("id")));
		context.assertEquals(0.04, craft.get("costUsd").getAsDouble(), Text.literal("cost"));
		context.assertEquals("clean", craft.get("verdict").getAsString(), Text.literal("verdict"));
		context.complete();
	}

	@GameTest
	public void deadPlayerGetsResultAtTable(TestContext context) {
		List<JsonObject> crafts = crafts(deliver(context, CraftHandoffTest.rayGun(context), 2));
		context.assertEquals(1, crafts.size(), Text.literal("craft lines for 2 copies"));
		List<ItemEntity> items = atTable(context);
		context.assertEquals(2, items.size(), Text.literal("items at the table"));
		context.assertTrue(ItemStack.areItemsAndComponentsEqual(items.get(0).getStack(), items.get(1).getStack()),
			Text.literal("the copies differ"));
		context.assertEquals(crafts.get(0).get("id").getAsString(), CraftId.of(items.get(0).getStack()).orElse("none"),
			Text.literal("the dropped copy's id"));
		context.complete();
	}

	@GameTest
	public void plainResultIsLoggedButNotStamped(TestContext context) {
		List<JsonObject> crafts = crafts(deliver(context, new ItemStack(Items.GUNPOWDER, 5), 1));
		context.assertEquals(1, crafts.size(), Text.literal("craft lines"));
		context.assertTrue(!crafts.get(0).has("id"), Text.literal("a plain result got an id"));
		context.assertEquals("", crafts.get(0).get("signature").getAsString(), Text.literal("signature"));
		List<ItemEntity> items = atTable(context);
		context.assertTrue(items.size() == 1 && CraftId.of(items.get(0).getStack()).isEmpty(), Text.literal("the dropped gunpowder"));
		context.complete();
	}
}
