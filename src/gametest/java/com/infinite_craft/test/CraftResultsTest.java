package com.infinite_craft.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.playtest.CraftId;
import com.infinite_craft.playtest.CraftResults;
import com.infinite_craft.playtest.PlaytestLog;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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

	/** What the server runs when a player right-clicks a block with the stack in hand */
	private static List<JsonObject> rightClickBlock(TestContext context, ItemStack stack) {
		List<String> lines = new ArrayList<>();
		Consumer<String> original = PlaytestLog.sink;
		PlaytestLog.sink = lines::add;
		PlaytestLog.reset();
		try {
			PlayerEntity player = CraftedItemUseTest.player(context);
			player.setStackInHand(Hand.MAIN_HAND, stack);
			BlockPos floor = context.getAbsolutePos(new BlockPos(4, 0, 4));
			UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
				new BlockHitResult(Vec3d.ofCenter(floor), Direction.UP, floor, false));
		} finally {
			PlaytestLog.sink = original;
		}
		return lines.stream().map(l -> JsonParser.parseString(l.substring(PlaytestLog.TAG.length())).getAsJsonObject()).toList();
	}

	@GameTest
	public void openingADoorIsNotUsingTheSword(TestContext context) {
		CraftedItemUseTest.floor(context);
		ItemStack sword = ThrustTest.parse(context, "{id:\"minecraft:iron_sword\",count:1i,components:{\"minecraft:item_name\":\"Big Sword\"}}");
		CraftId.stampIfCustom(sword);
		context.assertTrue(rightClickBlock(context, sword).isEmpty(), Text.literal("right-clicking a block counted as using a sword"));
		context.complete();
	}

	@GameTest
	public void placingAVehicleIsUsingIt(TestContext context) {
		CraftedItemUseTest.floor(context);
		ItemStack tank = ThrustTest.parse(context, AiPrompt.POWER_EXAMPLES.stream().filter(e -> e.contains("infinite_craft:vehicle")).findFirst().orElseThrow());
		String id = CraftId.stampIfCustom(tank).orElseThrow();
		List<JsonObject> lines = rightClickBlock(context, tank);
		context.assertTrue(lines.size() == 1 && lines.get(0).get("id").getAsString().equals(id) && lines.get(0).get("kind").getAsString().equals("use"),
			Text.literal("placing a tank logged " + lines));
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

	/** Delivers one craft to the table and picks its copies back up */
	private static List<ItemStack> craftCopies(TestContext context, String nbt, int copies) {
		deliver(context, ThrustTest.parse(context, nbt), copies);
		List<ItemEntity> items = atTable(context);
		items.forEach(ItemEntity::discard);
		return items.stream().map(ItemEntity::getStack).toList();
	}

	// vanilla keys a cooldown by item id, and every crafted item is infinite_craft:custom_crafted_item
	@GameTest
	public void cooldownLocksOnlyItsOwnCraft(TestContext context) {
		CraftedItemUseTest.floor(context);
		String bell = "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Golem Bell\",\"minecraft:use_cooldown\":{seconds:10.0f}}}";
		String whistle = "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Wolf Whistle\",\"minecraft:use_cooldown\":{seconds:10.0f}}}";
		String gun = "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Ray Gun\"}}";
		List<ItemStack> bells = craftCopies(context, bell, 2);
		ItemStack otherWhistle = craftCopies(context, whistle, 1).get(0);
		ItemStack rayGun = craftCopies(context, gun, 1).get(0);
		PlayerEntity player = CraftedItemUseTest.player(context);
		bells.get(0).get(DataComponentTypes.USE_COOLDOWN).set(bells.get(0), player);
		ItemCooldownManager cooldowns = player.getItemCooldownManager();
		context.assertTrue(cooldowns.isCoolingDown(bells.get(1)), Text.literal("the other copy of the same craft isn't on cooldown"));
		context.assertTrue(!cooldowns.isCoolingDown(otherWhistle), Text.literal("ringing the bell put the whistle on cooldown"));
		context.assertTrue(!cooldowns.isCoolingDown(rayGun), Text.literal("ringing the bell put the ray gun on cooldown"));
		context.assertTrue(ItemStack.areItemsAndComponentsEqual(bells.get(0), bells.get(1)), Text.literal("copies of one craft no longer stack"));
		context.complete();
	}

	// crafted before cooldowns had their own group: stamped with a craft id, no group
	@GameTest
	public void cooldownOnAnOlderCraftLocksOnlyItself(TestContext context) {
		ItemStack bell = ThrustTest.parse(context, "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:use_cooldown\":{seconds:10.0f},\"infinite_craft:craft_id\":\"0000beef\"}}");
		ItemStack gun = ThrustTest.parse(context, "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Ray Gun\",\"infinite_craft:craft_id\":\"0000cafe\"}}");
		PlayerEntity player = CraftedItemUseTest.player(context);
		bell.get(DataComponentTypes.USE_COOLDOWN).set(bell, player);
		context.assertTrue(player.getItemCooldownManager().isCoolingDown(bell), Text.literal("the bell isn't on cooldown"));
		context.assertTrue(!player.getItemCooldownManager().isCoolingDown(gun), Text.literal("an older bell put the ray gun on cooldown"));
		context.complete();
	}

	// Claude copies an ingredient's cooldown, group and all, onto an upgraded item: the two must not lock each other
	@GameTest
	public void cooldownCopiedFromAnIngredientIsTheNewCraftsOwn(TestContext context) {
		CraftedItemUseTest.floor(context);
		ItemStack oldBell = craftCopies(context, "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:use_cooldown\":{seconds:10.0f}}}", 1).get(0);
		Identifier oldGroup = player(context).getItemCooldownManager().getGroup(oldBell);
		ItemStack newBell = craftCopies(context, "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Mega Bell\",\"minecraft:use_cooldown\":{seconds:10.0f,cooldown_group:\"" + oldGroup + "\"}}}", 1).get(0);
		PlayerEntity player = CraftedItemUseTest.player(context);
		oldBell.get(DataComponentTypes.USE_COOLDOWN).set(oldBell, player);
		context.assertTrue(!player.getItemCooldownManager().isCoolingDown(newBell), Text.literal("the upgraded bell shares the old one's cooldown " + oldGroup));
		context.complete();
	}

	private static PlayerEntity player(TestContext context) {
		return CraftedItemUseTest.player(context);
	}

	// a group Claude chose on purpose is kept
	@GameTest
	public void cooldownGroupClaudeChoseIsKept(TestContext context) {
		CraftedItemUseTest.floor(context);
		ItemStack wand = craftCopies(context, "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:use_cooldown\":{seconds:3.0f,cooldown_group:\"minecraft:wands\"}}}", 1).get(0);
		context.assertEquals(Identifier.of("minecraft", "wands"), player(context).getItemCooldownManager().getGroup(wand), Text.literal("cooldown group"));
		context.complete();
	}
}
