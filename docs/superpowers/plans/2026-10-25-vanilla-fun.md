# Part B: vanilla fun Claude ignores — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Claude can make items that change the player's body and the world's rules (shrink, grow, low gravity, wall-stepping, long arms), potions can do that for a while (Shrink Potion), and guns can put effects on what they hit (Freeze Ray). The eval gets enough wishes per category to show whether items in a category are really different.

**Architecture:** A new consume effect type `infinite_craft:attributes` (`TimedAttributesConsumeEffect`) hands its modifiers to `TimedAttributes`, a server-side tracker that adds them as *temporary* attribute modifiers (never saved, so logging out clears them) and takes them off when their time runs out or the entity dies. `ShootComponent` gets an `effects` list that `ShootAbility` applies on every hit path (beam, bullet, rocket), gated by `Blast.isFairGame`. `BehaviorSignature` gains tokens for these two new fields only. The prompt gets a "Fun without fighting" toolkit section and three examples. `ClaudeCraftEval` gets new wishes and a per-category sameness check.

**Tech Stack:** Java 21, Fabric 1.21.10 (yarn), Fabric API events (`ServerTickEvents`, `ServerLivingEntityEvents`), Mojang codecs, JUnit 5, Fabric game tests and client game tests.

**Spec:** `docs/superpowers/specs/2026-10-25-variety-and-playtest-design.md` (Part B, judge 6, and the Part B half of eval judge 11).

## Global Constraints

- Minecraft 1.21.10, Fabric, yarn mappings; builds use `-Werror`, so no deprecated Fabric API.
- `README.md` and `AiPrompt.java` are CRLF: edit them byte-safe in Python (read with `newline=''`, normalize, write `\r\n`). Prompt text blocks contain literal `\t` escapes. New files are LF; `TASKS.md` is LF.
- JDK: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; run Gradle as `sh ./gradlew ...`. No `timeout` command.
- New unit test classes use `TestBootstrap.registries()` (bootstrapping twice throws).
- Item NBT parsing is lenient: a bad field falls back to defaults, so every default must give a working item. Every constructor clamps.
- **The ruler:** `BehaviorSignature` adds tokens for the new fields only. `ClaudeCraftEval.baselineVarietyIsPinned` must still pass unchanged after every task.
- Never spend API credits. Nothing in Tasks 1 to 3 calls Claude. The eval (Task 4) is run by the user, after asking: `read -s ANTHROPIC_API_KEY && export ANTHROPIC_API_KEY; sh ./gradlew test --tests '*ClaudeCraftEval*' -DclaudeEval.models=claude-opus-5-5`, then `sh ./gradlew runGameTest`.
- Iterate with targeted runs (`sh ./gradlew test --tests '*BehaviorSignatureTest*'`, `sh ./gradlew runClientGameTest -PclientTests=<Name>`). Full `runClientGameTest` (about 2 min 20 s; 600000 ms tool timeout) and `sh ./gradlew build` only before the PR.
- Negative-control every safety claim: switch the guard off once, see its test fail, switch it back (toggle with `perl -pi -e` or Python and `grep` that it landed).
- Branch `vanilla-fun`. Commits end with the Co-Authored-By and Claude-Session lines from the system reminder. One PR for Part B.

## Review Focus

1. **Drinking a second Shrink Potion while still shrunk.** It must refresh the time, not stack to scale 0.0625 or below. Test in Task 1: `secondDrinkRefreshesInsteadOfStacking`.
2. **Logging out (or the server stopping) while shrunk.** The player must come back at normal size. Temporary modifiers aren't saved; Task 1's `timedModifierIsNeverPersistent` pins that the modifier isn't among the persistent ones.
3. **Growing back, or growing, inside a low tunnel.** A kid who shrinks to walk through a 1-block gap must not suffocate when the potion ends. Task 1 cancels in-wall damage while a timed attribute is on and for 3 s after. Test: `growingBackInATunnelDoesNotSuffocate`.
4. **A Freeze Ray bullet or rocket hitting the shooter's own wolf**, not only a beam. Task 2's `freezeBulletSparesOwnPet` covers the bullet path; the rocket path calls the same helper.
5. **Moon Boots: a huge low-gravity jump, then landing.** He must land unhurt. Task 3's `moonBootsJumpLandsUnhurt` uses the prompt's own example.

## Not in this plan

- Shrinking mobs with a Shrink Ray. Shoot `effects` are status effects only. Timed attributes on mobs would work in `TimedAttributes` (it takes any `LivingEntity`), but no shoot field uses it yet. Revisit if the playtest asks for it.

---

### Task 1: Timed attributes (`infinite_craft:attributes` consume effect)

**Files:**
- Create: `src/main/java/com/infinite_craft/ability/TimedAttributesConsumeEffect.java`, `src/main/java/com/infinite_craft/ability/TimedAttributes.java`
- Modify: `src/main/java/com/infinite_craft/ability/Abilities.java` (call `TimedAttributes.initialize()`), `src/main/java/com/infinite_craft/playtest/BehaviorSignature.java`, `src/gametest/java/com/infinite_craft/test/CraftedItemUseTest.java` (`consume`), `src/gametest/resources/fabric.mod.json` (list `TimedAttributesTest` under `fabric-gametest`, and any new client test under `fabric-client-gametest`)
- Test: `src/gametest/java/com/infinite_craft/test/TimedAttributesTest.java`, `src/test/java/com/infinite_craft/playtest/BehaviorSignatureTest.java`

**Interfaces:**
- Produces:
  - `public record TimedAttributesConsumeEffect(List<Modifier> modifiers, float seconds) implements ConsumeEffect`;
    - `public record Modifier(RegistryEntry<EntityAttribute> type, EntityAttributeModifier modifier)`, with a codec of `EntityAttribute.CODEC.fieldOf("type")` plus `EntityAttributeModifier.MAP_CODEC`. That's the `attribute_modifiers` entry format without `slot`.
    - It clamps `seconds` to 1..600 (default 30) and keeps at most 6 modifiers.
    - `public static final ConsumeEffect.Type<TimedAttributesConsumeEffect> TYPE` is registered as `infinite_craft:attributes` in `Registries.CONSUME_EFFECT_TYPE`, with packet codec `PacketCodecs.registryCodec(CODEC.codec())`.
    - `onConsume` calls `TimedAttributes.apply(user, modifiers, Math.round(seconds * 20))` and returns true. It only runs on the server (vanilla already guards this).
  - `public final class TimedAttributes`:
    - `static void apply(LivingEntity entity, List<TimedAttributesConsumeEffect.Modifier> modifiers, int ticks)`. Each one goes on with `addTemporaryModifier`. A modifier with the same id that's already on the entity is replaced and its timer restarted, never stacked.
    - `static boolean isActive(LivingEntity entity)`.
    - `static void removeAll(LivingEntity entity)`.
    - `static void initialize()`:
      - registers `TYPE`;
      - adds `END_WORLD_TICK` expiry (by `world.getTime()`, over a weak map of tracked entities, so mock players that the test ticks by hand expire too);
      - adds `ServerLivingEntityEvents.AFTER_DEATH` → `removeAll`;
      - adds `ServerLivingEntityEvents.ALLOW_DAMAGE`, which returns false for `DamageTypes.IN_WALL` while active or within 60 ticks after the last modifier comes off.
- Consumes: nothing new.

The example the tests use is the prompt's Shrink Potion (Task 3 adds it to `AiPrompt.POWER_EXAMPLES`). Until then, define it as a constant in `TimedAttributesTest` and switch that constant to the prompt lookup in Task 3:

```
{id:"infinite_craft:custom_crafted_item",count:3i,components:{"minecraft:item_model":"minecraft:potion","minecraft:item_name":{text:"Shrink Potion",color:"light_purple"},"minecraft:max_stack_size":16,"minecraft:consumable":{consume_seconds:0.8f,animation:"drink",sound:"minecraft:entity.generic.drink",on_consume_effects:[{type:"infinite_craft:attributes",seconds:30.0f,modifiers:[{type:"minecraft:scale",id:"infinite_craft:shrink",amount:-0.75d,operation:"add_multiplied_total"}]}]}}}
```

- [ ] **Step 1: Write the failing game tests** (`TimedAttributesTest`, using `CraftedItemUseTest.player/tick/use` and parsing with `InfiniteCraftProcess.parseItemStackFromNbt`)

```java
@GameTest void shrinkPotionShrinksThenWearsOff(TestContext c)        // drink; scale == 0.25 ± 0.01; advance 30 s + 5 ticks of world time; scale == 1.0
@GameTest void shrinkWearsOffOnDeath(TestContext c)                  // drink; player.kill(world); scale == 1.0 and !TimedAttributes.isActive(player)
@GameTest void secondDrinkRefreshesInsteadOfStacking(TestContext c)  // drink, wait 20 s, drink again: scale == 0.25; 15 s later still 0.25; 30 s after the 2nd drink 1.0
@GameTest void timedModifierIsNeverPersistent(TestContext c)         // drink; getAttributeInstance(SCALE).getPersistentModifiers() has no infinite_craft:shrink
@GameTest void growingBackInATunnelDoesNotSuffocate(TestContext c)   // shrink, stand under a 1-block-high stone ceiling, let it wear off, tick 40: health == max
@GameTest void badSecondsAndTooManyModifiersAreClamped(TestContext c) // seconds:99999f → effect seconds() == 600; 9 modifiers → 6
```

Run the time forward by ticking the test (`c.waitAndRun` or `runAtTick`) so `END_WORLD_TICK` fires. Use `assertTrue`-style `c.assertTrue(...)` with the measured number in the message.

- [ ] **Step 2: Watch them fail.**
  - Run `sh ./gradlew runGameTest` (filter to `timedattributestest` if the runner allows it).
  - Expected: the item parses with the consume effect dropped (unknown type), so scale stays 1.0 and the tests fail. Or there's a compile failure on `TimedAttributes`.

- [ ] **Step 3: Implement `TimedAttributesConsumeEffect` and `TimedAttributes`** with the interfaces above. Put a class comment on `TimedAttributes` about why the modifiers are temporary (never saved, so logging out clears them) and why in-wall damage is cancelled.

- [ ] **Step 4: Run them and watch them pass.** Same command. Expected: 6 new passing tests, and the earlier server count (70) plus 6.

- [ ] **Step 5: Negative controls, each switched off once and back:**
  - replacing same-id modifiers (use `addTemporaryModifier` without the remove first: `secondDrinkRefreshes…` must fail, or vanilla throws on the duplicate id; either one counts);
  - the `AFTER_DEATH` hook (`shrinkWearsOffOnDeath` fails);
  - the in-wall guard (`growingBackInATunnel…` fails).
  - Record each result for the commit message.

- [ ] **Step 6: Signature token.** In `BehaviorSignature`, an `infinite_craft:attributes` consume effect adds `timed_<attribute short id>` to the `consumable(...)` parts for each attribute not in `STAT_ATTRIBUTES`. Add the unit test:

```java
@Test void shrinkPotionIsTimedScale() { assertEquals("consumable(timed_scale)", of(SHRINK_POTION)); }
```

  Run `sh ./gradlew test --tests '*BehaviorSignatureTest*' --tests '*ClaudeCraftEval.baselineVarietyIsPinned*'`. Expected: PASS, with the baseline still 21 of 22.

- [ ] **Step 7: `CraftedItemUseTest.consume`.** For a `TimedAttributesConsumeEffect`, compare each modifier's attribute value before and after drinking. A change goes in `seen` (`"scale 1.00 → 0.25"`), no change goes in `missing`. Run it on the Shrink Potion constant (add it to the test's built-in fixtures if there's a list of those; otherwise rely on `runGameTest` with the eval items in Task 4).

- [ ] **Step 8: Client check** (scale is synced and drawn).
  - Add `shrinkPotionShrinksInARealClient` to `src/gametest/java/com/infinite_craft/test/PlaytestClientGameTest.java` or a new `TimedAttributesClientGameTest`.
  - Drink the potion in a real client. The **client** player's `getScale()` must be about 0.25 within 20 ticks.
  - Take a screenshot (`timed-attributes-shrunk`) and read it: the camera sits low and blocks look huge.
  - Run: `sh ./gradlew runClientGameTest -PclientTests=TimedAttributes` (or whichever filter matches the new class).

- [ ] **Step 9: Commit**: "Let a potion change the player for a while". Say why (vanilla potions can't set attributes, and a Shrink Potion is the iconic one) and list the negative controls with what each did.

### Task 2: On-hit effects for `shoot`

**Files:**
- Modify: `src/main/java/com/infinite_craft/ability/ShootComponent.java`, `src/main/java/com/infinite_craft/ability/ShootAbility.java`, `src/main/java/com/infinite_craft/mixin/PersistentProjectileEntityMixin.java`, `src/main/java/com/infinite_craft/playtest/BehaviorSignature.java`
- Test: `src/gametest/java/com/infinite_craft/test/ShootTest.java`, `src/test/java/com/infinite_craft/playtest/BehaviorSignatureTest.java`

**Interfaces:**
- Produces:
  - `public record ShootComponent.HitEffect(RegistryEntry<StatusEffect> id, int amplifier, float seconds)`:
    - codec fields `id`, `amplifier` (default 0) and `seconds` (default 5);
    - clamps amplifier to 0..3 (like the aura) and seconds to 1..30.
  - `ShootComponent` gets a new last record component, `List<HitEffect> effects` (codec `optionalFieldOf("effects", List.of())`, at most 4 kept).
  - The tooltip adds one line per effect, `"Hits give: Slowness IV"`, in `Formatting.AQUA`, using the aura's amplifier wording.
  - `static void ShootAbility.applyHitEffects(ServerWorld world, Entity target, ShootComponent shoot, @Nullable Entity shooter)` does nothing unless `target` is a `LivingEntity` and `Blast.isFairGame(target, shooter)`. It adds each `StatusEffectInstance(id, round(seconds*20), amplifier)`, attributed to the shooter.
  - Call sites:
    - `ShootAbility.hit` (beams);
    - `ShootAbility.onRocketHitEntity`;
    - the mixin's `onEntityHit` RETURN injection (bullets). Move the effects call out of the `explode || lightning` condition so it runs for every bullet hit.

- [ ] **Step 1: Write the failing tests** in `ShootTest`:

```java
private static final String FREEZE_RAY = """
	{id:"infinite_craft:custom_crafted_item",count:1i,components:{"infinite_craft:shoot":{kind:"beam",damage:1.0f,color:"aqua",interval:10,effects:[{id:"minecraft:slowness",amplifier:3,seconds:6.0f}]}}}""";
private static final String FREEZE_BLASTER = /* same, kind:"bullet" */;

@GameTest void freezeRaySlowsAHusk(TestContext c)          // fire at a husk (NoAI, in line); husk has slowness amplifier 3
@GameTest void freezeRaySparesOwnPet(TestContext c)        // tamed wolf owned by the shooter in the line of fire; no slowness after 3 s of firing
@GameTest void freezeBulletSlowsAHuskAndSparesOwnPet(TestContext c)  // bullet variant of both, one test each if simpler
@GameTest void plainBeamGivesNoEffects(TestContext c)      // RAY_GUN leaves the husk with no status effects
```

Use `dummy`/`aim`/`holdTrigger` from `CraftedItemUseTest`, and the pet setup from `BlastTest` (`setOwner(player)`).

- [ ] **Step 2: Watch them fail.**
  - Run: `sh ./gradlew runGameTest`.
  - Expected: `effects` is an unknown field, so the husk gets no slowness and `freezeRaySlowsAHusk` fails.
  - The pet tests pass vacuously at this point; that's fine, because Step 5 proves them.

- [ ] **Step 3: Implement** the interfaces above.

- [ ] **Step 4: Run and watch them pass.** Run the same command again. Expected: all of `ShootTest` and `BlastTest` pass.

- [ ] **Step 5: Negative control.** Drop the `isFairGame` gate in `applyHitEffects` once, and both pet tests must fail. Restore it.

- [ ] **Step 6: Signature token.**
  - In `BehaviorSignature`, append `,hit_<short effect id>` to the shoot token for each effect, sorted, after the existing flags. For example: `shoot(beam,hit_slowness)`, `shoot(bullet,fire,hit_poison)`.
  - Add these unit tests:

```java
@Test void slownessBeamDiffersFromPlainBeam() {
	assertEquals("shoot(beam,hit_slowness)", of(FREEZE_RAY));
	assertNotEquals(of(FREEZE_RAY), of(PLASMA_BLASTER));
}
```

  - Run `sh ./gradlew test --tests '*BehaviorSignatureTest*' --tests '*ClaudeCraftEval*'`. Expected: PASS, with the baseline unchanged and `shootTakesEverydayColorNames` still passing.

- [ ] **Step 7: Commit**: "Guns can freeze, poison or slow what they hit". Name the negative control.

### Task 3: The prompt: "Fun without fighting", timed attributes, on-hit effects, three examples

**Files:**
- Modify: `src/main/java/com/infinite_craft/ai/AiPrompt.java` (CRLF), `src/test/java/com/infinite_craft/process/ClaudeCraftEval.java`, `src/gametest/java/com/infinite_craft/test/TimedAttributesTest.java` (use the prompt example), `README.md` (CRLF; the toolkit or abilities list, if it has one)
- Test: `ClaudeCraftEval.powerExamplesParseAndDoSomething` (existing), new `ClaudeCraftEval.toolkitAttributesExist`, `BehaviorSignatureTest.everyPromptExampleHasASignature` (existing), new game test `TimedAttributesTest.moonBootsJumpLandsUnhurt`

**Interfaces:**
- Consumes: `infinite_craft:attributes` (Task 1) and shoot `effects` (Task 2).
- Produces: `POWER_EXAMPLES` gains three entries that tests find by name: `"Shrink Potion"`, `"Moon Boots"` and `"Freeze Ray"`.

**Prompt text** (the exact wording is the implementer's; these facts must be in it):
- **Fun without fighting** (a new toolkit bullet after Auras): not every item is a weapon, and attribute modifiers on a worn or held item change the player's body and the rules. Each line gives an id, what it does, and an example item:
  - `minecraft:scale`: size; 0.25 tiny to 3 giant. Shrink Ring, Giant Crown.
  - `minecraft:gravity`: below 0.08 floats. Moon Boots; add safe_fall_distance.
  - `minecraft:step_height`: walk up blocks; 1 to 3. Spider Gloves.
  - `minecraft:jump_strength`.
  - `minecraft:movement_speed`.
  - `minecraft:block_interaction_range` and `minecraft:entity_interaction_range`: reach. Long Arms.
  - `minecraft:oxygen_bonus` and `minecraft:water_movement_efficiency`: Fish Gills.
  - `minecraft:burning_time`.
  - `minecraft:mining_efficiency`.
  - `minecraft:sneaking_speed`.
  - `minecraft:fall_damage_multiplier`.
  - Every id is checked by `toolkitAttributesExist`.
- **Potions that change you for a while:** `{type:"infinite_craft:attributes",seconds:30.0f,modifiers:[{type:"minecraft:scale",id:"infinite_craft:shrink",amount:-0.75d,operation:"add_multiplied_total"}]}` in `on_consume_effects`. Same modifier format as attribute_modifiers but with no slot. It wears off after `seconds` (1 to 600). Drinking another one restarts the time.
- **On-hit effects on `shoot`:** `effects:[{id:"minecraft:slowness",amplifier:3,seconds:6.0f}]` puts status effects on each mob a shot hits, never the shooter or their pets. Good for freeze rays, poison darts, sleep guns and glowing tracer rounds. Add one sentence to "Make different guns feel different": a freeze ray slows, a poison dart gun poisons.

**Examples** (add to `POWER_EXAMPLES`, each one commented like the others):
- the Shrink Potion from Task 1;
- Moon Boots:
  ```
  {id:"minecraft:leather_boots",count:1i,components:{"minecraft:item_name":{text:"Moon Boots",color:"gray"},"minecraft:dyed_color":12632256,"minecraft:attribute_modifiers":[{type:"minecraft:armor",id:"infinite_craft:moon_armor",amount:1.0d,operation:"add_value",slot:"feet"},{type:"minecraft:gravity",id:"infinite_craft:moon_gravity",amount:-0.65d,operation:"add_multiplied_base",slot:"feet"},{type:"minecraft:jump_strength",id:"infinite_craft:moon_jump",amount:0.3d,operation:"add_multiplied_base",slot:"feet"},{type:"minecraft:safe_fall_distance",id:"infinite_craft:moon_landing",amount:20.0d,operation:"add_value",slot:"feet"}]}}
  ```
- Freeze Ray:
  ```
  {id:"infinite_craft:custom_crafted_item",count:1i,components:{"minecraft:item_model":"infinite_craft:ray_gun","minecraft:item_name":{text:"Freeze Ray",color:"aqua"},"minecraft:max_stack_size":1,"infinite_craft:shoot":{kind:"beam",damage:2.0f,color:"aqua",interval:10,effects:[{id:"minecraft:slowness",amplifier:3,seconds:6.0f}]}}}
  ```
  (`infinite_craft:ray_gun` is in `crafted_models.json`.)

- [ ] **Step 1: Write the failing tests.**
  - `ClaudeCraftEval.toolkitAttributesExist`: build the prompt for one recipe and take the "Fun without fighting" section. Every `minecraft:<id>` in it must be in `Registries.ATTRIBUTE`, and the section must name all 13 ids above.
  - `ClaudeCraftEval.newExamplesArePresent`: POWER_EXAMPLES has entries containing `Shrink Potion`, `Moon Boots` and `Freeze Ray`.
  - `TimedAttributesTest.moonBootsJumpLandsUnhurt`, on the prompt's Moon Boots:
    - equip them and check that gravity is below 0.08;
    - jump from the floor and tick until on ground;
    - the peak must be at least 2 blocks (a plain jump peaks at about 1.25) and health stays at max.
  - Switch `TimedAttributesTest`'s Shrink Potion constant to the `POWER_EXAMPLES` lookup.
- [ ] **Step 2: Watch them fail.**
  - Run: `sh ./gradlew test --tests '*ClaudeCraftEval*'` and `sh ./gradlew runGameTest`.
  - Expected: the section and the examples are missing.
- [ ] **Step 3: Edit `AiPrompt.java` byte-safe** (a Python script that reads with `newline=''` and writes CRLF), adding the text and examples above. Then run `file src/main/java/com/infinite_craft/ai/AiPrompt.java`; it must still say CRLF.
- [ ] **Step 4: Run and watch them pass.**
  - Run: `sh ./gradlew test --tests '*ClaudeCraftEval*' --tests '*BehaviorSignatureTest*'`, then `sh ./gradlew runGameTest`.
  - Expected: all pass. The examples parse, do something and have signatures; the baseline is unchanged.
- [ ] **Step 5: Update `README.md`** (byte-safe) wherever it lists the toolkit or the mod's components: add the `infinite_craft:attributes` consume effect and shoot `effects`.
- [ ] **Step 6: Commit**: "Teach Claude fun that isn't fighting".

### Task 4: Eval wishes per category, and the sameness check

**Files:**
- Modify: `src/test/java/com/infinite_craft/process/ClaudeCraftEval.java`, `TASKS.md`

**Interfaces:**
- Produces:
  - `static final Map<String, List<String>> CATEGORIES` in `ClaudeCraftEval`, keyed `guns`, `wearables` and `drinks`, with the recipe labels as values;
  - `static Map<String, Map<String, List<String>>> sameWithinCategory(List<Map.Entry<String, String>> labeledNbts)`: category → (signature → labels), only for signatures that two or more results in that category share. It uses `BehaviorSignature.of`.

**New wishes** (each an ingredient renamed in an anvil, like the existing ones; pick plausible ingredients):

| Category | Already there | New |
|---|---|---|
| guns | Ray Gun, Nail Gun, Chain Gun, Rocket Launcher, Thor's Hammer | **Freeze Ray** (renamed diamond + snowball), **Poison Dart Gun** (renamed stick + spider eye) |
| wearables | Jetpack, Dash Boots, Steam Armor, God Armor | **Moon Boots** (renamed iron boots + end stone), **Spider Gloves** (renamed leather + string), **Fish Gills** (renamed leather helmet + pufferfish), **Long Arms** (renamed stick + piston) |
| drinks | Prime Energy Drink | **Shrink Potion** (renamed glass bottle + red mushroom), **Giant Potion** (renamed glass bottle + golden apple) |

That's 8 new crafts, about $0.32 more, so a run comes to about $1.27.

**Proposed pass mark** (it replaces "≥90% distinct", which the baseline already meets):
- no two results **within a category** share a signature;
- every new wish uses the field it's about. Shrink and Giant Potion use `infinite_craft:attributes` with `scale`. Moon Boots use `gravity`. Spider Gloves use `step_height`. Freeze Ray and Poison Dart Gun use shoot `effects`. Fish Gills use `oxygen_bonus` or `water_movement_efficiency`. Long Arms use an interaction range.
- zero inert results.

The known risk is Nail Gun versus Chain Gun (both `shoot(bullet)` in the baseline).

- [ ] **Step 1: Write the failing unit test** `sameWithinCategoryFindsRepeats`, with no credits spent:
  - canned entries: Nail Gun and Chain Gun both `shoot(bullet)`, the Freeze Ray as `shoot(beam,hit_slowness)`, and the Ray Gun as `shoot(beam)`;
  - expect `{guns={shoot(bullet)=[Nail Gun, Chain Gun]}}`;
  - a wearable sharing a signature with a gun is **not** reported, since categories are separate.
- [ ] **Step 2: Watch it fail.** Run `sh ./gradlew test --tests '*ClaudeCraftEval.sameWithinCategory*'`. Expected: a compile failure.
- [ ] **Step 3: Implement** the following:
  - `CATEGORIES` and `sameWithinCategory`;
  - the 8 recipes;
  - a `Same within a category` summary column (`none`, or `guns: shoot(bullet): Nail Gun, Chain Gun`, joined with `<br>`);
  - a "New wishes" column listing each new wish with its signature, so the field check can be read at a glance.
- [ ] **Step 4: Run it and watch it pass.**
  - Run `sh ./gradlew test --tests '*ClaudeCraftEval*'`.
  - Expected: PASS. `buildsPromptAndRequestForEveryRecipe` covers the new recipes and `compareModels` is skipped with no key set.
- [ ] **Step 5: TASKS.md.** Add a Part B entry under Part 3 with the spec and plan paths, the proposed pass mark and "eval pending". Commit: "Give the eval several wishes per category".
- [ ] **Step 6: Ask the user to run the eval** (about $1.27 on `claude-opus-5-5`), giving them the two commands from Global Constraints. Don't run it yourself. When they report back:
  - read `build/claude-eval/report.md` and the `runGameTest` report;
  - record the results in TASKS.md the way T4 to T9 did: distinct behaviors, same-within-category, and what each new wish became;
  - adjust the prompt only if a new wish missed its field, then ask before re-running.

### Task 5: Review and PR

- [ ] **Step 1: Full verification.**
  - `sh ./gradlew build`, then read `build/test-results/test/*.xml` for the `tests=` and `failures=` counts.
  - `sh ./gradlew runGameTest`, then `grep "required tests"` with the ANSI codes stripped.
  - `sh ./gradlew runClientGameTest` (600000 ms timeout).
  - Record the counts.
- [ ] **Step 2: Whole-branch review.**
  - Use `superpowers:requesting-code-review`, with a fresh Opus reviewer given the spec's Part B, this plan and `git diff main...`.
  - Fix what's real, with `superpowers:receiving-code-review`.
- [ ] **Step 3: Push and open the PR.**
  - `git push -u origin vanilla-fun`;
  - `gh pr create --repo shanewwarren/infinite-craft-mod` with the `pr` skill template. The body gives the evidence: test counts, the negative controls and what each did, the client screenshot, and the eval results if they're in.
  - Wait for "merge it".

---

## Self-review notes

- Spec coverage:
  - the toolkit text is Task 3;
  - timed attributes, including death, logout and refresh, are Task 1;
  - on-hit effects with the pet rule are Task 2;
  - the three examples are Task 3;
  - judge 6 is split between Tasks 1, 2 and 3;
  - the Part B wishes of judge 11 (Shrink Potion, Moon Boots, Spider Gloves, Freeze Ray), plus extras so each category has several, are Task 4;
  - the signature tokens for the new fields only are Tasks 1 and 2, guarded by `baselineVarietyIsPinned`;
  - shrinking mobs is left out, as the spec allows.
- `tools/playtest/report.py` prints signatures as they are, so the new tokens show up there with no change.
- Names used across tasks: `TimedAttributesConsumeEffect.Modifier`, `TimedAttributes.apply/isActive/removeAll`, `ShootComponent.HitEffect`, `ShootAbility.applyHitEffects`, the `timed_` and `hit_` tokens, and `sameWithinCategory`.
