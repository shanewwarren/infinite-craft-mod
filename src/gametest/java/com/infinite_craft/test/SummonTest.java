package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.player;
import static com.infinite_craft.test.CraftedItemUseTest.use;

import java.util.List;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.SummonComponent;
import com.infinite_craft.ability.Summons;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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

	/** Gives the player the summoner in hand and right-clicks it once, the cooldown cleared first */
	static ActionResult summonOnce(PlayerEntity player, ItemStack summoner) {
		player.setStackInHand(Hand.MAIN_HAND, summoner);
		player.getItemCooldownManager().remove(player.getItemCooldownManager().getGroup(summoner));
		return use(player, Hand.MAIN_HAND);
	}

	@GameTest(maxTicks = 20)
	public void summonsCountOfType(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\",count:3}")));
		List<MobEntity> summons = Summons.of(player);
		context.assertTrue(summons.size() == 3, Text.literal("summoned " + summons.size() + " of 3"));
		for (MobEntity mob : summons) {
			context.assertTrue(mob.getType() == EntityType.ZOMBIE && Summons.isSummon(mob) && Summons.ownerOf(mob) == player,
				Text.literal("not a zombie of the player's: " + mob));
			boolean free = context.getWorld().isSpaceEmpty(mob);
			context.assertTrue(mob.isPersistent() && !mob.canPickUpLoot() && free,
				Text.literal("persistent %s, picks up loot %s, in free space %s".formatted(mob.isPersistent(), mob.canPickUpLoot(), free)));
		}
		context.complete();
	}

	@GameTest(maxTicks = 260)
	public void goneAfterSeconds(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:husk\",seconds:10}")));
		context.assertTrue(Summons.of(player).size() == 1, Text.literal("nothing summoned"));
		MobEntity husk = Summons.of(player).getFirst();
		context.runAtTick(195, () -> context.assertTrue(!husk.isRemoved(), Text.literal("gone before its 10 s")));
		context.runAtTick(210, () -> {
			context.assertTrue(husk.isRemoved() && Summons.of(player).isEmpty(), Text.literal("still here after 10 s"));
			context.complete();
		});
	}

	@GameTest(maxTicks = 20)
	public void thirteenthRemovesOldest(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		ItemStack summoner = ThrustTest.parse(context, summoner("{entity:\"minecraft:husk\"}"));
		summonOnce(player, summoner);
		MobEntity first = Summons.of(player).getFirst();
		for (int i = 0; i < 12; i++) summonOnce(player, summoner);
		context.assertTrue(Summons.of(player).size() == Summons.MAX_PER_PLAYER && first.isRemoved(),
			Text.literal("%d summons, first removed %s".formatted(Summons.of(player).size(), first.isRemoved())));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void spamClickingStaysAtTwelve(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		ItemStack summoner = ThrustTest.parse(context, summoner("{entity:\"minecraft:husk\",count:6}"));
		player.setStackInHand(Hand.MAIN_HAND, summoner);
		for (int i = 0; i < 10; i++) use(player, Hand.MAIN_HAND);
		context.assertTrue(Summons.of(player).size() == 6 && player.getItemCooldownManager().isCoolingDown(player.getMainHandStack()),
			Text.literal("%d summons after 10 clicks, cooling down %s".formatted(Summons.of(player).size(),
				player.getItemCooldownManager().isCoolingDown(player.getMainHandStack()))));
		context.complete();
	}

	@GameTest(maxTicks = 60)
	public void golemInATunnelNeverSuffocates(TestContext context) {
		floor(context);
		// a 1-wide, 2-high tunnel along x, at z = 4
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 1; y <= 3; y++) {
					boolean tunnel = z == 4 && y <= 2;
					if (!tunnel) context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		PlayerEntity player = player(context);
		ActionResult result = summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:iron_golem\"}")));
		List<MobEntity> golems = Summons.of(player);
		context.assertTrue(golems.isEmpty() == (result == ActionResult.FAIL), Text.literal("result " + result + " with " + golems.size() + " golems"));
		for (MobEntity golem : golems) {
			// its whole body, not just its eyes (isInsideWall): a golem with its feet in stone is stuck
			context.assertTrue(context.getWorld().isSpaceEmpty(golem), Text.literal("golem summoned inside the tunnel wall at " + golem.getBlockPos()));
		}
		context.runAtTick(40, () -> {
			for (MobEntity golem : golems) {
				context.assertTrue(golem.getHealth() == golem.getMaxHealth(), Text.literal("golem hurt: " + golem.getHealth()));
			}
			context.complete();
		});
	}

	@GameTest(maxTicks = 20)
	public void ownerLeavingRemovesSummons(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:wolf\",count:4}")));
		List<MobEntity> summons = List.copyOf(Summons.of(player));
		Summons.removeAll(player);
		context.assertTrue(!summons.isEmpty() && summons.stream().allMatch(MobEntity::isRemoved) && Summons.of(player).isEmpty(),
			Text.literal("after leaving: " + summons.stream().filter(m -> !m.isRemoved()).count() + " still here"));
		context.complete();
	}

	// a summon saved with its chunk comes back with the tag but no owner
	@GameTest(maxTicks = 20)
	public void leftoverSummonVanishesOnLoad(TestContext context) {
		floor(context);
		ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new Vec3d(2.5, 1, 2.5));
		zombie.addCommandTag(Summons.TAG);
		context.runAtTick(3, () -> {
			context.assertTrue(zombie.isRemoved(), Text.literal("a summon left over from before a restart came back"));
			context.complete();
		});
	}

	@GameTest(maxTicks = 20)
	public void summonsDropNoLootOrXp(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\",gear:{mainhand:\"minecraft:iron_sword\"}}")));
		MobEntity zombie = Summons.of(player).getFirst();
		Vec3d at = zombie.getEntityPos();
		// killed by another player, so a normal zombie would drop xp too (its owner can't hurt it)
		PlayerEntity other = player(context);
		zombie.damage(context.getWorld(), other.getDamageSources().playerAttack(other), 1000);
		Box around = new Box(at, at).expand(4);
		List<ItemEntity> items = context.getWorld().getEntitiesByClass(ItemEntity.class, around, e -> true);
		List<ExperienceOrbEntity> orbs = context.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, around, e -> true);
		context.assertTrue(zombie.isDead() && items.isEmpty() && orbs.isEmpty(),
			Text.literal("dead %s, dropped %s and %d xp orbs".formatted(zombie.isDead(), items, orbs.size())));
		context.complete();
	}
}
