package com.infinite_craft.test;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.SummonComponent;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

/**
 * infinite_craft:summon calls helpers that fight for the player: they appear on free ground, go after his time,
 * stay at most 12 per player, and never hurt him, other players, pets, villagers or each other
 */
public class SummonTest {

	static String summoner(String summon) {
		return "{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"infinite_craft:summon\":" + summon + "}}";
	}

	private static SummonComponent summon(TestContext context, String summon) {
		return ThrustTest.parse(context, summoner(summon)).get(Abilities.SUMMON);
	}

	@GameTest(maxTicks = 20)
	public void componentClampsAndFallsBack(TestContext context) {
		for (String mob : new String[] {"minecraft:ender_dragon", "minecraft:creeper", "minecraft:nope"}) {
			SummonComponent fallback = summon(context, "{entity:\"" + mob + "\"}");
			context.assertTrue(fallback != null && fallback.entity() == EntityType.WOLF,
				Text.literal(mob + " gave " + (fallback == null ? "no component" : fallback.entity())));
		}
		SummonComponent big = summon(context, "{entity:\"minecraft:zombie\",count:50,seconds:9999,size:9.0f}");
		context.assertTrue(big.entity() == EntityType.ZOMBIE && big.count() == 6 && big.seconds() == 180 && big.size() == 2,
			Text.literal("too big clamped to " + big));
		SummonComponent small = summon(context, "{count:0,seconds:1,size:0.1f}");
		context.assertTrue(small.count() == 1 && small.seconds() == 10 && small.size() == 0.5f, Text.literal("too small clamped to " + small));
		context.complete();
	}
}
