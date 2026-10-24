package com.infinite_craft.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.infinite_craft.InfiniteItem;
import com.infinite_craft.ability.Abilities;
import com.infinite_craft.ability.ShootComponent;
import com.infinite_craft.ai.AiPrompt;
import com.infinite_craft.process.InfiniteCraftProcess;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ConsumeEffect;
import net.minecraft.item.consume.TeleportRandomlyConsumeEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Uses every crafted item the way a player would and checks that something happened:
 * drinks apply their effects, weapons hurt or launch a mob, armor changes the wearer's stats, gliders glide,
 * guns fire, tools mine faster. Fixtures are the prompt's own examples plus real results from the Claude eval.
 *
 * Run with `./gradlew runGameTest` (also part of `./gradlew build`). Writes per-item reports to build/gametest/.
 * The prompt's examples must all work. The eval's items are what Claude last made, so they are reported but
 * only fail the optional test: fix the prompt, then rerun the eval to refresh them
 */
public class CraftedItemUseTest {

	private record Fixture(String label, String nbt) {}

	private record Check(String kind, boolean passed, String detail) {}

	private record Hit(float damage, double knockback, boolean onFire) {}

	// Ticks of holding the trigger when counting shots, like a player holding right click for 5 seconds
	private static final int TRIGGER_TICKS = 100;

	// open sky: jetpacks fly higher than the default barrier box
	@GameTest(maxTicks = 20, skyAccess = true)
	public void promptExamplesDoSomething(TestContext context) {
		List<Fixture> fixtures = new ArrayList<>();
		for (int i = 0; i < AiPrompt.POWER_EXAMPLES.size(); i++) {
			fixtures.add(new Fixture("prompt example " + (i + 1), AiPrompt.POWER_EXAMPLES.get(i).strip()));
		}
		useAll(context, fixtures, "the prompt's examples", "prompt-examples.md");
	}

	@GameTest(maxTicks = 20, required = false, skyAccess = true)
	public void evalItemsDoSomething(TestContext context) {
		useAll(context, evalFixtures(), evalItems().map(Path::toString).orElse("the checked-in eval snapshot"), "eval-items.md");
	}

	private static void useAll(TestContext context, List<Fixture> fixtures, String source, String reportName) {
		floor(context);
		Hit fist = hit(context, ItemStack.EMPTY);
		StringBuilder report = new StringBuilder("# Crafted item use test\n\n")
			.append("Items from ").append(source).append(", ")
			.append("each used by a survival mock player in a game test world. Bare-hand punch for comparison: ")
			.append("%.1f damage, %.2f knockback.\n\n".formatted(fist.damage, fist.knockback))
			.append("| Item | Verdict | Checks |\n|---|---|---|\n");
		List<String> failures = new ArrayList<>();

		for (Fixture fixture : fixtures) {
			List<Check> checks;
			String name;
			try {
				ItemStack stack = InfiniteCraftProcess.parseItemStackFromNbt(fixture.nbt, context.getWorld().getRegistryManager());
				name = stack.getName().getString();
				checks = checks(context, stack, fist);
				if (checks.isEmpty() && stack.isOf(InfiniteItem.CUSTOM_CRAFTED_ITEM)) {
					checks = List.of(new Check("inert", false, "custom item with nothing to use"));
				}
			} catch (Exception e) {
				name = "?";
				checks = List.of(new Check("parse", false, String.valueOf(e.getMessage())));
			}
			boolean passed = checks.stream().allMatch(Check::passed);
			String verdict = checks.isEmpty() ? "plain vanilla" : passed ? "works" : "BROKEN";
			if (!passed) failures.add(fixture.label + " (" + name + ")");
			report.append("| %s: %s | %s | %s |\n".formatted(fixture.label, name, verdict, String.join("<br>",
				checks.stream().map(c -> (c.passed ? "✓ " : "✗ ") + c.kind + ": " + c.detail).toList())));
		}

		writeReport(reportName, report.toString());
		context.assertTrue(failures.isEmpty(), net.minecraft.text.Text.literal("Items that did nothing when used: " + failures));
		context.complete();
	}

	/** Every way the item can be used, each with what happened */
	private static List<Check> checks(TestContext context, ItemStack stack, Hit fist) {
		List<Check> checks = new ArrayList<>();
		if (stack.contains(DataComponentTypes.CONSUMABLE)) checks.add(consume(context, stack));
		if (isMelee(stack)) checks.add(melee(context, stack, fist));
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		if (equippable != null && equippable.slot().isArmorSlot() && !modifiedAttributes(stack, equippable.slot()).isEmpty()) {
			checks.add(wear(context, stack, equippable.slot()));
		}
		if (stack.contains(DataComponentTypes.GLIDER)) checks.add(glide(context, stack));
		if (stack.contains(Abilities.THRUST)) checks.add(fly(context, stack));
		if (stack.contains(Abilities.SHOOT) || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof BowItem) {
			checks.add(shoot(context, stack));
		}
		// a sword's built-in cobweb rule is vanilla behavior, only test mining rules the crafted item brings
		ToolComponent tool = stack.get(DataComponentTypes.TOOL);
		if (tool != null && !tool.rules().isEmpty() && !tool.equals(stack.getItem().getComponents().get(DataComponentTypes.TOOL))) {
			checks.add(mine(context, stack, tool));
		}
		if (stack.contains(DataComponentTypes.DEATH_PROTECTION)) checks.add(saveFromDeath(context, stack));
		return checks;
	}

	// --- consumables: effects applied, player teleported, hunger filled

	private static Check consume(TestContext context, ItemStack stack) {
		ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
		PlayerEntity player = player(context);
		player.getHungerManager().setFoodLevel(4);
		player.setStackInHand(Hand.MAIN_HAND, stack.copy());
		Vec3d start = player.getEntityPos();
		int countBefore = stack.getCount();

		use(player, Hand.MAIN_HAND);
		for (int t = 0; t < consumable.consumeSeconds() * 20 + 10 && player.isUsingItem(); t++) {
			tick(player);
		}

		List<String> missing = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		for (ConsumeEffect effect : consumable.onConsumeEffects()) {
			if (effect instanceof ApplyEffectsConsumeEffect apply) {
				for (StatusEffectInstance instance : apply.effects()) {
					String id = instance.getEffectType().getIdAsString();
					if (player.hasStatusEffect(instance.getEffectType())) {
						seen.add(id);
					} else if (apply.probability() >= 1) {
						missing.add(id);
					} else {
						// a chance effect (like a sugar crash) is allowed not to happen
						seen.add("%s didn't roll (%.0f%% chance)".formatted(id, apply.probability() * 100));
					}
				}
			} else if (effect instanceof TeleportRandomlyConsumeEffect) {
				double moved = player.getEntityPos().distanceTo(start);
				(moved > 1 ? seen : missing).add("teleport (moved %.1f)".formatted(moved));
			}
		}
		if (stack.contains(DataComponentTypes.FOOD)) {
			(player.getHungerManager().getFoodLevel() > 4 ? seen : missing).add("food");
		}
		boolean used = player.getMainHandStack().getCount() < countBefore || !player.getMainHandStack().isOf(stack.getItem());
		if (!used) missing.add("item was not consumed");
		return new Check("consume", missing.isEmpty() && (used && !seen.isEmpty() || consumable.onConsumeEffects().isEmpty() && used),
			"got " + seen + (missing.isEmpty() ? "" : ", missing " + missing));
	}

	// --- melee: hits harder or launches further than a bare fist

	private static boolean isMelee(ItemStack stack) {
		if (stack.contains(DataComponentTypes.WEAPON)) return true;
		boolean[] attacks = {false};
		stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(EntityAttributes.ATTACK_DAMAGE) || attribute.equals(EntityAttributes.ATTACK_KNOCKBACK)) attacks[0] = true;
		});
		return attacks[0];
	}

	private static Check melee(TestContext context, ItemStack stack, Hit fist) {
		Hit hit = hit(context, stack.copy());
		double knockbackModifier = modifierTotal(stack, EquipmentSlot.MAINHAND, EntityAttributes.ATTACK_KNOCKBACK);
		boolean harder = hit.damage > fist.damage + 0.01;
		boolean launches = hit.knockback > fist.knockback + 0.01;
		boolean passed = (harder || launches) && (knockbackModifier <= 0 || launches);
		return new Check("melee", passed, "%.1f damage, %.2f knockback%s".formatted(hit.damage, hit.knockback, hit.onFire ? ", set on fire" : ""));
	}

	/** Attacks a fresh dummy mob with a fully charged swing */
	private static Hit hit(TestContext context, ItemStack weapon) {
		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, weapon);
		// equipment modifiers apply and the attack cooldown recharges as the player ticks
		for (int t = 0; t < 60; t++) tick(player);

		PigEntity dummy = dummy(context);
		float before = dummy.getHealth();
		player.attack(dummy);
		Hit hit = new Hit(before - dummy.getHealth(), dummy.getVelocity().horizontalLength(), dummy.isOnFire());
		dummy.discard();
		return hit;
	}

	static PigEntity dummy(TestContext context) {
		PigEntity dummy = context.spawnEntity(EntityType.PIG, new Vec3d(4.5, 1, 6.5));
		dummy.setAiDisabled(true);
		dummy.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1000);
		dummy.setHealth(1000);
		return dummy;
	}

	// --- wearables: equipping changes the wearer's attributes

	private static Check wear(TestContext context, ItemStack stack, EquipmentSlot slot) {
		PlayerEntity player = player(context);
		Map<RegistryEntry<EntityAttribute>, Double> before = new LinkedHashMap<>();
		for (RegistryEntry<EntityAttribute> attribute : modifiedAttributes(stack, slot)) {
			before.put(attribute, player.getAttributeValue(attribute));
		}
		player.equipStack(slot, stack.copy());
		tick(player);

		List<String> changed = new ArrayList<>();
		List<String> unchanged = new ArrayList<>();
		before.forEach((attribute, value) -> {
			double after = player.getAttributeValue(attribute);
			String id = attribute.getIdAsString().replace("minecraft:", "");
			if (Math.abs(after - value) > 1e-6) changed.add("%s %.2f→%.2f".formatted(id, value, after));
			else unchanged.add(id);
		});

		// Modifiers replace the base item's own stats, so crafted armor can end up weaker than the plain item
		PlayerEntity plain = player(context);
		plain.equipStack(slot, new ItemStack(stack.getItem()));
		tick(plain);
		List<String> weaker = new ArrayList<>();
		for (RegistryEntry<EntityAttribute> attribute : List.of(EntityAttributes.ARMOR, EntityAttributes.ARMOR_TOUGHNESS)) {
			double crafted = player.getAttributeValue(attribute);
			double base = plain.getAttributeValue(attribute);
			if (crafted < base - 1e-6) {
				weaker.add("%s %.0f, plain %s has %.0f".formatted(attribute.getIdAsString().replace("minecraft:", ""), crafted,
					stack.getItem().getName().getString(), base));
			}
		}
		return new Check("wear", !changed.isEmpty() && weaker.isEmpty(), String.join(", ", changed)
			+ (unchanged.isEmpty() ? "" : "; unchanged " + unchanged)
			+ (weaker.isEmpty() ? "" : "; WEAKER THAN BASE: " + String.join(", ", weaker)));
	}

	private static List<RegistryEntry<EntityAttribute>> modifiedAttributes(ItemStack stack, EquipmentSlot slot) {
		List<RegistryEntry<EntityAttribute>> attributes = new ArrayList<>();
		stack.applyAttributeModifiers(slot, (attribute, modifier) -> {
			if (!attributes.contains(attribute)) attributes.add(attribute);
		});
		return attributes;
	}

	private static double modifierTotal(ItemStack stack, EquipmentSlot slot, RegistryEntry<EntityAttribute> attribute) {
		double[] total = {0};
		stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
			.applyModifiers(slot, (a, modifier) -> { if (a.equals(attribute)) total[0] += modifier.value(); });
		return total[0];
	}

	// --- gliders: a falling player wearing it starts gliding

	private static Check glide(TestContext context, ItemStack stack) {
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		if (equippable == null) return new Check("glide", false, "has a glider but can't be worn");
		PlayerEntity player = player(context);
		player.equipStack(equippable.slot(), stack.copy());
		player.setPosition(player.getEntityPos().add(0, 4, 0));
		player.setOnGround(false);
		boolean gliding = player.checkGliding();
		return new Check("glide", gliding, gliding ? "glides when worn on " + equippable.slot().getName() : "won't glide");
	}

	// --- jetpacks: worn (or held), holding jump flies up

	private static Check fly(TestContext context, ItemStack stack) {
		PlayerEntity player = player(context);
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		player.equipStack(equippable != null ? equippable.slot() : EquipmentSlot.MAINHAND, stack.copy());
		double start = player.getY();
		double climbed = ThrustTest.holdJump(player, 40) - start;
		return new Check("fly", climbed > 6, "climbed %.1f blocks in 2s holding jump".formatted(climbed));
	}

	// --- ranged: holding the trigger at a mob fires, with no ammo in the inventory.
	// Runs within one tick, so beams land but projectiles are only counted, not flown (ShootTest flies them)

	private static Check shoot(TestContext context, ItemStack stack) {
		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, stack.copy());
		PigEntity dummy = dummy(context);
		aim(player, dummy);
		Box around = player.getBoundingBox().expand(8);
		List<ProjectileEntity> before = context.getWorld().getEntitiesByClass(ProjectileEntity.class, around, e -> true);

		for (int t = 0; t < TRIGGER_TICKS; t++) {
			holdTrigger(player, t);
			tick(player);
		}

		List<ProjectileEntity> fired = context.getWorld().getEntitiesByClass(ProjectileEntity.class, around, e -> !before.contains(e));
		fired.forEach(ProjectileEntity::discard);
		float damage = dummy.getMaxHealth() - dummy.getHealth();
		dummy.discard();

		ShootComponent shoot = stack.get(Abilities.SHOOT);
		// a no-ammo gun keeps firing; a vanilla crossbow fires what it was loaded with
		boolean passed = shoot == null
			? !fired.isEmpty()
			: fired.size() >= 2 || damage >= 2 * shoot.damage() - 0.01;
		return new Check("shoot", passed, "%d projectiles%s, %.0f beam damage to a mob in %ds, no ammo".formatted(
			fired.size(), fired.isEmpty() ? "" : " (" + EntityType.getId(fired.get(0).getType()).getPath() + ")", damage, TRIGGER_TICKS / 20));
	}

	/** One tick of a player holding right click: the client repeats use every 4 ticks, and a drawn bow or crossbow is released */
	static void holdTrigger(PlayerEntity player, int tick) {
		if (!player.isUsingItem()) {
			if (tick % 4 == 0) use(player, Hand.MAIN_HAND);
		} else if (player.getItemUseTime() >= 25) {
			player.stopUsingItem();
		}
	}

	static void aim(PlayerEntity player, LivingEntity target) {
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getBoundingBox().getCenter());
	}

	// --- tools: mines its blocks faster than a bare hand, and gets their drops if it says so

	private static Check mine(TestContext context, ItemStack stack, ToolComponent tool) {
		PlayerEntity player = player(context);
		List<String> results = new ArrayList<>();
		boolean passed = true;
		for (ToolComponent.Rule rule : tool.rules()) {
			Optional<BlockState> block = rule.blocks().stream().map(RegistryEntry::value).map(Block::getDefaultState)
				.filter(state -> state.getHardness(context.getWorld(), BlockPos.ORIGIN) > 0).findFirst();
			if (block.isEmpty()) continue;
			BlockState state = block.get();
			player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
			float hand = player.getBlockBreakingSpeed(state);
			player.setStackInHand(Hand.MAIN_HAND, stack.copy());
			float withTool = player.getBlockBreakingSpeed(state);
			boolean faster = rule.speed().isEmpty() || withTool > hand;
			boolean drops = !rule.correctForDrops().orElse(false) || player.canHarvest(state);
			passed &= faster && drops;
			results.add("%s %.0fx faster%s".formatted(state.getBlock().getTranslationKey().replace("block.minecraft.", ""),
				withTool / hand, drops ? "" : " but no drops"));
		}
		return new Check("mine", passed && !results.isEmpty(), results.isEmpty() ? "no minable blocks in its rules" : String.join(", ", results));
	}

	// --- totems: held in a hand, survives a lethal hit

	private static Check saveFromDeath(TestContext context, ItemStack stack) {
		PlayerEntity player = player(context);
		player.setStackInHand(Hand.MAIN_HAND, stack.copy());
		player.damage(context.getWorld(), player.getDamageSources().generic(), 1000);
		return new Check("death protection", player.isAlive(), player.isAlive() ? "survived a lethal hit" : "died");
	}

	// --- helpers

	static void floor(TestContext context) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A survival player standing on the test floor, looking at where dummies spawn */
	static PlayerEntity player(TestContext context) {
		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		Vec3d pos = context.getAbsolute(new Vec3d(4.5, 1, 4.5));
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	/** What the world does each tick for an entity in it; mock players aren't in the world */
	static void tick(PlayerEntity player) {
		player.age++;
		player.tick();
	}

	/** What the server does when a player right-clicks with an item */
	static ActionResult use(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (player.getItemCooldownManager().isCoolingDown(stack)) return ActionResult.PASS;
		ActionResult result = stack.use(player.getEntityWorld(), player, hand);
		if (result instanceof ActionResult.Success success && success.getNewHandStack() != null) {
			player.setStackInHand(hand, success.getNewHandStack());
		}
		return result;
	}

	private static List<Fixture> evalFixtures() {
		List<Fixture> fixtures = new ArrayList<>();
		try (BufferedReader reader = evalItems().isPresent()
				? Files.newBufferedReader(evalItems().get())
				: new BufferedReader(new InputStreamReader(
					CraftedItemUseTest.class.getResourceAsStream("/infinite_craft_test/crafted_items.txt"), StandardCharsets.UTF_8))) {
			reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).forEach(line -> {
				String[] parts = line.split("\t", 2);
				fixtures.add(new Fixture(parts[0], parts[1]));
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return fixtures;
	}

	/** Items from the last eval run (build/claude-eval/crafted_items.txt), which replace the checked-in snapshot */
	private static Optional<Path> evalItems() {
		return Optional.ofNullable(System.getProperty("infiniteCraft.evalItems")).map(Path::of).filter(Files::exists);
	}

	private static void writeReport(String name, String report) {
		Path out = Path.of(System.getProperty("infiniteCraft.reportDir", "."), name);
		try {
			Files.createDirectories(out.toAbsolutePath().getParent());
			Files.writeString(out, report);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println(report);
		System.out.println("Item use report written to " + out.toAbsolutePath());
	}
}
