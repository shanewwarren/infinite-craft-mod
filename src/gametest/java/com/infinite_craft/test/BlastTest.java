package com.infinite_craft.test;

import static com.infinite_craft.test.CraftedItemUseTest.dummy;
import static com.infinite_craft.test.CraftedItemUseTest.floor;
import static com.infinite_craft.test.CraftedItemUseTest.holdTrigger;
import static com.infinite_craft.test.CraftedItemUseTest.player;
import static com.infinite_craft.test.CraftedItemUseTest.tick;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.infinite_craft.process.InfiniteCraftProcess;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Explosive and lightning shots (infinite_craft:shoot explode / lightning) hurt mobs around where they land.
 * With destroy:false (the default for beams and bullets) they leave the player's build alone: every block in a wall
 * of glass, planks, wool and torches is still there. With destroy:true (the default for rockets) they blow holes in it,
 * unless the world's infiniteCraftBlastsBreakBlocks gamerule is off. Either way no fire appears, and the shooter's pet,
 * dropped items and armor stands are untouched.
 * Mock players aren't in the world, so a blast can't reach the shooter here; BlastClientGameTest checks that
 */
public class BlastTest {

	private static final String ROCKET_LAUNCHER = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:rocket_launcher","infinite_craft:shoot":{kind:"rocket",damage:6.0f,explode:2.0f,interval:20,destroy:false}}}""";

	// rockets break blocks unless told not to
	private static final String WRECKING_ROCKET_LAUNCHER = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:rocket_launcher","infinite_craft:shoot":{kind:"rocket",damage:6.0f,explode:2.0f,interval:20}}}""";

	private static final String WRECKING_TNT_GUN = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:iron_ingot","infinite_craft:shoot":{kind:"bullet",damage:4.0f,explode:2.0f,interval:10,destroy:true}}}""";

	private static final String TNT_GUN = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:iron_ingot","infinite_craft:shoot":{kind:"bullet",damage:4.0f,explode:2.0f,interval:10}}}""";

	private static final String BLAST_RAY = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"minecraft:blaze_rod","infinite_craft:shoot":{kind:"beam",damage:4.0f,explode:2.0f,interval:10}}}""";

	private static final String SMITE_WAND = """
		{id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:magic_wand","infinite_craft:shoot":{kind:"beam",damage:4.0f,lightning:true,color:"yellow",interval:20}}}""";

	// the impact point: the middle of the wall the shooter aims at
	private static final Vec3d TARGET = new Vec3d(4.5, 2.5, 7);
	private static final int FIRING_TICKS = 20;

	@GameTest(maxTicks = 80)
	public void rocketBlastHurtsMobsAroundButNotTheBuild(TestContext context) {
		blastAtWall(context, ROCKET_LAUNCHER, false);
	}

	@GameTest(maxTicks = 80)
	public void explosiveBulletHurtsMobsAroundButNotTheBuild(TestContext context) {
		blastAtWall(context, TNT_GUN, false);
	}

	@GameTest(maxTicks = 80)
	public void explosiveBeamHurtsMobsAroundButNotTheBuild(TestContext context) {
		blastAtWall(context, BLAST_RAY, false);
	}

	@GameTest(maxTicks = 80)
	public void lightningHurtsMobsAroundButStartsNoFire(TestContext context) {
		blastAtWall(context, SMITE_WAND, false);
	}

	@GameTest(maxTicks = 80)
	public void rocketBlowsHolesInTheBuild(TestContext context) {
		blastAtWall(context, WRECKING_ROCKET_LAUNCHER, true);
	}

	@GameTest(maxTicks = 80)
	public void destroyingBulletBlowsHolesInTheBuild(TestContext context) {
		blastAtWall(context, WRECKING_TNT_GUN, true);
	}

	/** The parent's off switch: /gamerule infiniteCraftBlastsBreakBlocks false keeps every block, even for rockets */
	@GameTest(maxTicks = 80, environment = "infinite-craft-test:blasts_keep_blocks")
	public void gameruleOffKeepsTheBuild(TestContext context) {
		blastAtWall(context, WRECKING_ROCKET_LAUNCHER, false);
	}

	@GameTest(maxTicks = 80)
	public void rocketHittingAMobHurtsIt(TestContext context) {
		floor(context);
		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, parse(context, ROCKET_LAUNCHER));
		PigEntity dummy = dummy(context);
		int[] tick = {0};
		context.runAtEveryTick(() -> {
			if (tick[0] < FIRING_TICKS) {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, dummy.getBoundingBox().getCenter());
				holdTrigger(player, tick[0]++);
				tick(player);
			}
		});
		context.runAtTick(FIRING_TICKS + 20, () -> {
			float damage = dummy.getMaxHealth() - dummy.getHealth();
			// the direct hit plus the blast: more than either alone
			context.assertTrue(damage > 6 + 1, Text.literal("rocket did only %.1f damage to the mob it hit".formatted(damage)));
			context.complete();
		});
	}

	/**
	 * A wall of fragile blocks, mobs beside the impact point (not in the line of fire), and the shooter's own pet,
	 * a dropped item and an armor stand just as close. The shooter fires at the wall for a second.
	 * breaksBuild: the wall must have holes in it afterwards; otherwise every block must be unchanged
	 */
	private static void blastAtWall(TestContext context, String nbt, boolean breaksBuild) {
		floor(context);
		for (int x = 2; x <= 6; x++) {
			for (int y = 1; y <= 4; y++) {
				context.setBlockState(new BlockPos(x, y, 7), switch ((x + y) % 3) {
					case 0 -> Blocks.GLASS;
					case 1 -> Blocks.OAK_PLANKS;
					default -> Blocks.WHITE_WOOL;
				});
			}
		}
		// torches and flowers break in any explosion that touches blocks
		context.setBlockState(new BlockPos(3, 1, 6), Blocks.TORCH);
		context.setBlockState(new BlockPos(5, 1, 6), Blocks.POPPY);
		context.setBlockState(new BlockPos(1, 0, 6), Blocks.OAK_PLANKS);
		context.setBlockState(new BlockPos(7, 0, 6), Blocks.HAY_BLOCK);
		Map<BlockPos, BlockState> before = blocks(context);

		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, parse(context, nbt));

		List<LivingEntity> mobs = List.of(mob(context, EntityType.PIG, 2.5, 6.0), mob(context, EntityType.PIG, 6.5, 6.0));
		CreeperEntity creeper = mob(context, EntityType.CREEPER, 3.5, 5.5);
		WolfEntity pet = mob(context, EntityType.WOLF, 5.5, 6.3);
		pet.setTamed(true, true);
		pet.setOwner(player);
		ArmorStandEntity stand = context.spawnEntity(EntityType.ARMOR_STAND, new Vec3d(1.5, 1, 6.5));
		ItemEntity drop = context.spawnItem(Items.DIAMOND, new Vec3d(6.5, 1, 5.5));
		drop.setPickupDelayInfinite();

		Vec3d target = context.getAbsolute(TARGET);
		int[] tick = {0};
		context.runAtEveryTick(() -> {
			if (tick[0] < FIRING_TICKS) {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target);
				holdTrigger(player, tick[0]++);
				tick(player);
			}
		});
		// lightning lingers and could light fires for a few ticks after the strike
		context.runAtTick(FIRING_TICKS + 40, () -> {
			List<String> problems = new ArrayList<>();
			for (LivingEntity mob : mobs) {
				boolean hurt = mob.isRemoved() || mob.getHealth() < mob.getMaxHealth();
				if (!hurt) problems.add("pig at %.1f blocks from the impact wasn't hurt".formatted(mob.getEntityPos().distanceTo(target)));
			}
			if (pet.isRemoved() || pet.getHealth() < pet.getMaxHealth()) problems.add("the shooter's pet wolf was hurt");
			if (stand.isRemoved()) problems.add("the armor stand broke");
			if (drop.isRemoved() || drop.getStack().getCount() != 1) problems.add("the dropped diamond was destroyed");
			if (!creeper.isRemoved() && creeper.isCharged()) problems.add("a creeper got charged (its blast would break blocks)");

			Map<BlockPos, BlockState> after = blocks(context);
			after.forEach((pos, state) -> {
				if (state.isIn(BlockTags.FIRE)) problems.add("fire at " + pos.toShortString());
			});
			if (breaksBuild) {
				long holes = before.keySet().stream()
					.filter(pos -> pos.getZ() == 7 && !before.get(pos).isAir() && after.get(pos).isAir())
					.count();
				System.out.printf("[BlastTest] %s blew %d of 20 blocks out of the wall%n", nbt.contains("rocket") ? "rocket" : "bullet", holes);
				if (holes < 3) problems.add("only %d of the wall's 20 blocks were broken".formatted(holes));
			} else {
				before.forEach((pos, state) -> {
					if (!after.get(pos).equals(state)) problems.add("block at %s changed from %s to %s".formatted(pos.toShortString(),
						state.getBlock().getName().getString(), after.get(pos).getBlock().getName().getString()));
				});
			}
			context.assertTrue(problems.isEmpty(), Text.literal(String.join("; ", problems)));
			context.complete();
		});
	}

	/** Every block in and around the test area, including any fire that appears on top of the floor */
	private static Map<BlockPos, BlockState> blocks(TestContext context) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		for (BlockPos pos : BlockPos.iterate(0, 0, 0, 7, 5, 7)) {
			blocks.put(pos.toImmutable(), context.getBlockState(pos));
		}
		return blocks;
	}

	private static <T extends LivingEntity> T mob(TestContext context, EntityType<T> type, double x, double z) {
		T mob = context.spawnEntity(type, new Vec3d(x, 1, z));
		if (mob instanceof net.minecraft.entity.mob.MobEntity ai) ai.setAiDisabled(true);
		return mob;
	}

	private static ItemStack parse(TestContext context, String nbt) {
		try {
			return InfiniteCraftProcess.parseItemStackFromNbt(nbt.strip(), context.getWorld().getRegistryManager());
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		}
	}
}
