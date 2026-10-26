package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.player;
import static com.infinite_craft.test.CraftedItemUseTest.use;

import java.util.ArrayList;
import java.util.List;

import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.Blast;
import com.infinite_craft.ability.ShootComponent;
import com.infinite_craft.ability.SummonAbility;
import com.infinite_craft.ability.SummonComponent;
import com.infinite_craft.ability.Summons;
import com.infinite_craft.mixin.MobEntityAccessor;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.conversion.EntityConversionContext;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WolfEntity;
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
		SummonAbility.resetCooldown(player);
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
		context.assertTrue(Summons.of(player).size() == 6, Text.literal("%d summons after 10 clicks".formatted(Summons.of(player).size())));
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

	// --- on his side

	private static WolfEntity pet(TestContext context, PlayerEntity owner, double x, double z) {
		WolfEntity wolf = context.spawnEntity(EntityType.WOLF, new Vec3d(x, 1, z));
		wolf.setTamed(true, true);
		wolf.setOwner(owner);
		return wolf;
	}

	private static boolean hurt(LivingEntity entity) {
		return entity.isRemoved() || entity.getHealth() < entity.getMaxHealth();
	}

	@GameTest(maxTicks = 300)
	public void zombieAttacksHusk(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 1, 1.5));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\"}")));
		context.runAtEveryTick(() -> {
			if (hurt(husk)) context.complete();
		});
		context.runAtTick(250, () -> context.assertTrue(false, Text.literal("the summoned zombie never hurt the husk")));
	}

	@GameTest(maxTicks = 600)
	public void skeletonShotsNeverHurtOwnerOrPet(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		WolfEntity wolf = pet(context, player, 4.5, 5.5);
		// a target dummy straight behind the player, so the skeleton shoots past him
		HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(7.5, 1, 4.5));
		husk.setAiDisabled(true);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:skeleton\",gear:{mainhand:\"minecraft:bow\"}}")));
		MobEntity skeleton = Summons.of(player).getFirst();
		// close behind him, so its arrows pass through where he stands
		skeleton.refreshPositionAndAngles(context.getAbsolute(new Vec3d(2.5, 1, 4.5)), 0, 0);
		// shots past him every tick until one lands on the husk; a skeleton may take a while to line up
		context.runAtEveryTick(() -> {
			context.assertTrue(!hurt(player) && !hurt(wolf),
				Text.literal("player health %s, wolf health %s".formatted(player.getHealth(), wolf.getHealth())));
			if (hurt(husk)) context.complete();
		});
		context.runAtTick(560, () -> context.assertTrue(false, Text.literal("the skeleton never hit the husk")));
	}

	@GameTest(maxTicks = 300)
	public void ironGolemAndTamedWolfLeaveSummonAlone(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		WolfEntity wolf = pet(context, player, 5.5, 5.5);
		IronGolemEntity golem = context.spawnEntity(EntityType.IRON_GOLEM, new Vec3d(1.5, 1, 1.5));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\"}")));
		MobEntity zombie = Summons.of(player).getFirst();
		// he swings at his own summon by accident: his wolf joins in on what he attacks
		player.attack(zombie);
		// his wolf joining in (AttackWithOwnerGoal) ends in setTarget; the mock player isn't in the world, so ask it directly
		wolf.setTarget(zombie);
		context.runAtEveryTick(() -> context.assertTrue(golem.getTarget() != zombie && wolf.getTarget() != zombie,
			Text.literal("golem target %s, wolf target %s".formatted(golem.getTarget(), wolf.getTarget()))));
		context.runAtTick(200, () -> {
			context.assertTrue(!hurt(zombie), Text.literal("the summon was hurt: " + zombie.getHealth()));
			context.complete();
		});
	}

	@GameTest(maxTicks = 300, skyAccess = true)
	public void summonsDontBurnInDaylight(TestContext context) {
		floor(context);
		context.getWorld().setTimeOfDay(6000);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\"}")));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:skeleton\"}")));
		List<MobEntity> summons = Summons.of(player);
		// no helmet to hide under
		summons.forEach(mob -> mob.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, ItemStack.EMPTY));
		context.runAtEveryTick(() -> summons.forEach(mob -> context.assertTrue(!mob.isOnFire(), Text.literal(mob.getType().getName().getString() + " caught fire in daylight"))));
		context.runAtTick(200, context::complete);
	}

	@GameTest(maxTicks = 20)
	public void blastSparesOwnSummons(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\"}")));
		MobEntity zombie = Summons.of(player).getFirst();
		zombie.setVelocity(Vec3d.ZERO);
		ShootComponent rocket = ThrustTest.parse(context,
			"{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"infinite_craft:shoot\":{kind:\"rocket\",explode:3.0f}}}").get(Abilities.SHOOT);
		Blast.at(context.getWorld(), zombie.getEntityPos().add(1, 0, 0), rocket, null, player, null);
		context.assertTrue(!hurt(zombie) && zombie.getVelocity().length() < 0.05,
			Text.literal("his own blast hit his summon: health %s, pushed %.2f".formatted(zombie.getHealth(), zombie.getVelocity().length())));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void summonNeverConverts(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\"}")));
		MobEntity zombie = Summons.of(player).getFirst();
		MobEntity drowned = zombie.convertTo(EntityType.DROWNED, EntityConversionContext.create(zombie, true, true), d -> {});
		context.assertTrue(drowned == null && !zombie.isRemoved() && Summons.isSummon(zombie),
			Text.literal("the summoned zombie turned into " + drowned));
		context.complete();
	}

	@GameTest(maxTicks = 400)
	public void beeKeepsItsStinger(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(1.5, 1, 1.5));
		husk.setAiDisabled(true);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:bee\"}")));
		BeeEntity bee = (BeeEntity) Summons.of(player).getFirst();
		int[] stungAt = {-1};
		int[] tick = {0};
		context.runAtEveryTick(() -> {
			tick[0]++;
			// a sting only poisons on normal or hard, but it always hurts
			if (stungAt[0] < 0 && hurt(husk)) stungAt[0] = tick[0];
			if (stungAt[0] >= 0 && tick[0] == stungAt[0] + 2) {
				context.assertTrue(!bee.hasStung() && bee.isAlive(), Text.literal("the bee lost its stinger"));
				context.complete();
			}
		});
		context.runAtTick(350, () -> context.assertTrue(false, Text.literal("the bee never stung the husk")));
	}

	/**
	 * The stress target: a minute of melee and crossfire around him, his wolf and a villager. Wild husks fight his 12
	 * summons (they're set to go only for summons, so any harm to the three below is the summons' doing)
	 */
	@GameTest(maxTicks = 1300, skyAccess = true)
	public void brawl(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		WolfEntity wolf = pet(context, player, 5.5, 4.5);
		VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, new Vec3d(3.5, 1, 4.5));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\",count:6}")));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:skeleton\",count:3}")));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:pillager\",count:3}")));
		int summoned = Summons.of(player).size();
		List<HuskEntity> husks = new ArrayList<>();
		double[][] corners = {{0.5, 0.5}, {7.5, 0.5}, {0.5, 7.5}, {7.5, 7.5}};
		for (int wave = 0; wave < 12; wave++) {
			double[] corner = corners[wave % 4];
			context.runAtTick(20 + wave * 100L, () -> {
				HuskEntity husk = context.spawnEntity(EntityType.HUSK, new Vec3d(corner[0], 1, corner[1]));
				MobEntityAccessor accessor = (MobEntityAccessor) husk;
				accessor.infiniteCraft$targetSelector().clear(goal -> true);
				accessor.infiniteCraft$targetSelector().add(1, new ActiveTargetGoal<>(husk, MobEntity.class, false, (e, w) -> Summons.isSummon(e)));
				husks.add(husk);
			});
		}
		context.runAtTick(1220, () -> {
			context.assertTrue(summoned == 12, Text.literal("only " + summoned + " summoned"));
			context.assertTrue(!hurt(player) && !hurt(wolf) && !hurt(villager),
				Text.literal("player %s, wolf %s, villager %s".formatted(player.getHealth(), wolf.getHealth(), villager.getHealth())));
			long fought = husks.stream().filter(SummonTest::hurt).count();
			context.assertTrue(fought >= 8, Text.literal("only " + fought + " of " + husks.size() + " husks were fought"));
			context.complete();
		});
	}

	// --- from the branch review

	@GameTest(maxTicks = 20)
	public void cooldownDoesNotLockOtherCraftedItems(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:wolf\"}")));
		ItemStack gun = ThrustTest.parse(context,
			"{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"infinite_craft:shoot\":{kind:\"beam\"}}}");
		context.assertTrue(!player.getItemCooldownManager().isCoolingDown(gun), Text.literal("summoning put his gun on cooldown too"));
		player.setStackInHand(Hand.MAIN_HAND, ThrustTest.parse(context, summoner("{entity:\"minecraft:wolf\"}")));
		use(player, Hand.MAIN_HAND);
		context.assertTrue(Summons.of(player).size() == 1, Text.literal("the summon cooldown didn't hold: " + Summons.of(player).size()));
		context.complete();
	}

	// a 3-high room: a normal golem (2.7) fits, a size-2 one (5.4) doesn't
	@GameTest(maxTicks = 40)
	public void bigSummonNeverGrowsIntoTheCeiling(TestContext context) {
		floor(context);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlockState(new BlockPos(x, 4, z), Blocks.STONE);
			}
		}
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:iron_golem\",size:2.0f}")));
		List<MobEntity> golems = Summons.of(player);
		context.runAtTick(5, () -> {
			for (MobEntity golem : golems) {
				context.assertTrue(context.getWorld().isSpaceEmpty(golem),
					Text.literal("a %.1f-tall golem is stuck in the ceiling".formatted(golem.getHeight())));
			}
			context.complete();
		});
	}

	@GameTest(maxTicks = 20)
	public void summonedZombiesCallNoReinforcements(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\",count:6}")));
		for (MobEntity zombie : Summons.of(player)) {
			double chance = zombie.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.SPAWN_REINFORCEMENTS);
			context.assertTrue(chance == 0, Text.literal("a summoned zombie calls wild reinforcements with chance " + chance));
		}
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void summonsCantBeTamedOrBred(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:wolf\"}")));
		WolfEntity wolf = (WolfEntity) Summons.of(player).getFirst();
		for (int i = 0; i < 20; i++) {
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(net.minecraft.item.Items.BONE, 64));
			wolf.interact(player, Hand.MAIN_HAND);
		}
		context.assertTrue(!wolf.isTamed() && player.getMainHandStack().getCount() == 64,
			Text.literal("tamed %s, bones left %d".formatted(wolf.isTamed(), player.getMainHandStack().getCount())));
		context.complete();
	}

	// the skeleton's time runs out while its arrow flies: the arrow is its own attacker then
	@GameTest(maxTicks = 20)
	public void arrowFromAGoneSummonHurtsNoOne(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		WolfEntity wolf = pet(context, player, 5.5, 5.5);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:skeleton\"}")));
		MobEntity skeleton = Summons.of(player).getFirst();
		net.minecraft.entity.projectile.ArrowEntity arrow = new net.minecraft.entity.projectile.ArrowEntity(context.getWorld(), skeleton,
			new ItemStack(net.minecraft.item.Items.ARROW), null);
		context.getWorld().spawnEntity(arrow);
		Summons.removeAll(player);
		net.minecraft.entity.damage.DamageSource source = context.getWorld().getDamageSources().arrow(arrow,
			arrow.getOwner() == null ? arrow : arrow.getOwner());
		player.damage(context.getWorld(), source, 4);
		wolf.damage(context.getWorld(), source, 4);
		context.assertTrue(!hurt(player) && !hurt(wolf), Text.literal("player %s, wolf %s".formatted(player.getHealth(), wolf.getHealth())));
		context.complete();
	}

	// the pet branch of the damage guard: a summon's arrow at his wolf
	@GameTest(maxTicks = 20)
	public void summonArrowSparesHisPet(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		WolfEntity wolf = pet(context, player, 5.5, 5.5);
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:skeleton\"}")));
		MobEntity skeleton = Summons.of(player).getFirst();
		net.minecraft.entity.projectile.ArrowEntity arrow = new net.minecraft.entity.projectile.ArrowEntity(context.getWorld(), skeleton,
			new ItemStack(net.minecraft.item.Items.ARROW), null);
		wolf.damage(context.getWorld(), context.getWorld().getDamageSources().arrow(arrow, skeleton), 4);
		context.assertTrue(!hurt(wolf), Text.literal("his summon's arrow hurt his wolf: " + wolf.getHealth()));
		context.complete();
	}

	@GameTest(maxTicks = 20)
	public void summonsLeaveCreepersAlone(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		net.minecraft.entity.mob.CreeperEntity creeper = context.spawnEntity(EntityType.CREEPER, new Vec3d(1.5, 1, 1.5));
		summonOnce(player, ThrustTest.parse(context, summoner("{entity:\"minecraft:zombie\"}")));
		MobEntity zombie = Summons.of(player).getFirst();
		zombie.setTarget(creeper);
		context.assertTrue(zombie.getTarget() != creeper, Text.literal("a summon went for a creeper beside him"));
		context.complete();
	}
}
