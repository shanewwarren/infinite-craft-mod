# Part A: the playtest judge and the hand-off — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new craft lands in the player's hand with a "You made" title; every craft and use is logged to `latest.log`; a script turns that log into a playtest report; and the eval reports a variety score built on the same behavior signature.

**Architecture:** A new package `com.infinite_craft.playtest` holds four small pieces: `BehaviorSignature` (what an item does, from its NBT), `CraftId` (the hidden `infinite_craft:craft_id` component), `PlaytestLog` (structured log lines, use counting) and `CraftHandoff` (where a result goes, plus the title). `CraftResults` ties them together and is what `InfiniteCraftProcess` calls when a craft finishes. `Playtest.initialize()` registers the hooks that log uses, wears, drops and snapshots. `tools/playtest/report.py` reads the log.

**Tech Stack:** Java 21, Fabric 1.21.10 (yarn), Fabric API events, Gson (already on the classpath), JUnit 5, Fabric game tests and client game tests, Python 3 stdlib only.

**Spec:** `docs/superpowers/specs/2026-10-25-variety-and-playtest-design.md` (Part A, plus the "Shipping order" constraint).

## Global Constraints

- Minecraft 1.21.10, Fabric, yarn mappings; builds use `-Werror`, so no deprecated Fabric API.
- `README.md` and `AiPrompt.java` are CRLF: edit them byte-safe in Python (read bytes, normalize, write `\r\n`). New files are LF; `TASKS.md` is LF.
- JDK: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`; run Gradle as `sh ./gradlew ...`.
- Iterate with targeted runs (`sh ./gradlew test --tests '*BehaviorSignature*'`, `sh ./gradlew runGameTest` with a filter where possible, `sh ./gradlew runClientGameTest -PclientTests=Playtest`). Full `runClientGameTest` (about 2 min 15 s; use a 600000 ms tool timeout) and `sh ./gradlew build` only before the PR.
- Never spend API credits. Nothing in this plan calls Claude.
- The log stays on this machine; nothing is sent anywhere.
- Every log line starts with exactly `[InfiniteCraft/playtest] ` followed by one JSON object on one line.
- The playtest log must write at most a few lines a minute per item, however fast it's used.
- The hand-off never overwrites or loses an item, and never touches the cursor stack.
- The signature bucketing fixed in Task 1 is the ruler: later parts may add tokens for new fields only, never change existing ones. Task 2's pinned-baseline test enforces it.
- Commits end with the Co-Authored-By and Claude-Session lines from the system reminder. Work on branch `variety-playtest`. One PR for Part A.

## Review Focus

1. **The craft finishes while the crafting screen is open with an item on the cursor.** The hand-off must not touch the cursor stack. Test in Task 4: `cursorStackIsUntouched`.
2. **The player died or left before the result came back.** The result still drops at the crafting table, as today, and the craft is still logged. Test in Task 5: `deadPlayerGetsResultAtTable`.
3. **Names with quotes, newlines, emoji or `§` codes** (Claude writes all of these) must stay one log line and round-trip through the report. Tests in Task 3 (`oddNamesStayOneLine`) and Task 6 (`test_odd_names`).
4. **A session across a game restart** spans `latest.log` plus a rotated `*.log.gz`. The report must take several files, `.gz` included, and skip lines that aren't JSON. Test in Task 6: `test_gz_and_multiple_files`, `test_ignores_garbage`.
5. **Plain vanilla results** (a breakdown's gunpowder) must not be stamped, so they still stack with mined gunpowder. Test in Task 3: `plainVanillaIsNotStamped`.

---

### Task 1: BehaviorSignature

**Files:**
- Create: `src/main/java/com/infinite_craft/playtest/BehaviorSignature.java`
- Test: `src/test/java/com/infinite_craft/playtest/BehaviorSignatureTest.java`

**Interfaces:**
- Produces:
  - `public static String BehaviorSignature.of(NbtCompound item)`: the item compound as the eval and the codec write it (`{id, count, components}`);
  - `public static String BehaviorSignature.of(String nbt) throws CommandSyntaxException`;
  - `public static String BehaviorSignature.of(ItemStack stack, RegistryWrapper.WrapperLookup registries)`, which encodes with `ItemStack.CODEC` and `registries.getOps(NbtOps.INSTANCE)`.
  - Returns `""` when the item has no components (plain vanilla) or is an element.

**The ruler** (an algorithm the tests don't fully determine, so it's spelled out). Build a set of tokens, sort them alphabetically, and join them with `" + "`. Ids are written without the `minecraft:` prefix; other namespaces keep theirs.
- `base(<path>)` when the id isn't `infinite_craft:custom_crafted_item`. The path has a leading material prefix stripped (`wooden_`, `stone_`, `iron_`, `golden_`, `diamond_`, `netherite_`, `copper_`, `leather_`, `chainmail_`), so `diamond_sword` becomes `sword`.
- `shoot(<kind>[,explode][,lightning][,fire])`. The kind defaults to `beam`. `explode` is present when `explode` > 0 or the kind is `rocket`.
- `vehicle(<body>)`, with the body defaulting to `tank`.
- `launch`, `dash`, `thrust`.
- `aura(<sorted effect ids>)`, or `aura` when there are no effects.
- `consumable(<sorted parts>)`. For `apply_effects` the parts are its effect ids; `teleport_randomly` gives `teleport`, `clear_all_effects` gives `clear_effects`, `remove_effects` gives `remove_effects`, and `play_sound` is ignored. `minecraft:food` adds a separate `food` token.
- `attributes(<sorted attribute ids>)`.
- `enchantments(<sorted enchantment ids>)`, ignoring levels.
- `wear(<slot>)` from `minecraft:equippable`.
- `tool`, `glider`, `death_protection`, `blocks_attacks`, `damage_resistant`.
- Ignored: names, lore, models, colors, all numbers, `use_cooldown`, `max_stack_size`, `unbreakable`, `enchantment_glint_override`, `custom_model_data`, `dyed_color`, `infinite_craft:craft_id`.

- [ ] **Step 1: Write the failing tests** (`BehaviorSignatureTest`, a `@BeforeAll` that bootstraps like `ClaudeCraftEval.bootstrap`)

```java
@Test void sameGunDifferentNameAndNumbersMatch() {
	assertEquals("shoot(beam)", of("{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Plasma Blaster\",\"infinite_craft:shoot\":{kind:\"beam\",damage:6.0f}}}"));
	assertEquals(of(PLASMA_BLASTER), of("{id:\"infinite_craft:custom_crafted_item\",count:1i,components:{\"minecraft:item_name\":\"Laser Pistol\",\"infinite_craft:shoot\":{damage:9.0f,color:\"red\",interval:6}}}"));
}
@Test void rocketDiffersFromBeam() { assertEquals("shoot(rocket,explode)", of(ROCKET_LAUNCHER_EXAMPLE)); }   // AiPrompt.POWER_EXAMPLES entry containing kind:"rocket"
@Test void heldSpeedBeamDiffersFromPlainBeam() {
	assertEquals("attributes(movement_speed) + shoot(beam)", of(/* beam + a mainhand movement_speed modifier */));
}
@Test void swordsOfDifferentMaterialMatch() {
	assertEquals("base(sword) + enchantments(sharpness)", of("{id:\"minecraft:diamond_sword\",count:1i,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":3}}}"));
	assertEquals("base(sword) + enchantments(sharpness)", of("{id:\"minecraft:iron_sword\",count:1i,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":1}}}"));
}
@Test void drinkListsItsEffects() { assertEquals("consumable(jump_boost,speed)", of(/* consumable apply_effects speed + jump_boost */)); }
@Test void blinkOrbIsTeleport() { assertEquals("consumable(teleport)", of(BLINK_ORB_EXAMPLE)); }
@Test void plainVanillaHasNoSignature() { assertEquals("", of("{id:\"minecraft:gunpowder\",count:5i}")); }
@Test void everyPromptExampleHasASignature() { AiPrompt.POWER_EXAMPLES.forEach(e -> assertFalse(of(e.strip()).isEmpty(), e)); }
@Test void stackAndNbtAgree() { /* parse the Tank example to an ItemStack with BuiltinRegistries.createWrapperLookup(), assert of(stack, registries).equals(of(nbt)) */ }
```

- [ ] **Step 2: Run them and watch them fail**

Run: `sh ./gradlew test --tests '*BehaviorSignatureTest*'`
Expected: a compile failure, because `BehaviorSignature` doesn't exist.

- [ ] **Step 3: Implement `BehaviorSignature`** following the ruler above. It reads the NBT compound only (no codec), so it can't fail on fields Minecraft would drop. Comment the class with the rule that later parts add tokens and never change these.

- [ ] **Step 4: Run the tests and see them pass**

Run: `sh ./gradlew test --tests '*BehaviorSignatureTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**: "Describe what an item does, ignoring its name". Say why: the eval and the playtest log need one ruler for sameness.

### Task 2: The variety score in the eval, and the pinned baseline

**Files:**
- Modify: `src/test/java/com/infinite_craft/process/ClaudeCraftEval.java`: summary table and report.
- Create: `src/test/resources/variety/eval-2026-10-25-opus.txt` (a byte copy of the current `build/claude-eval/crafted_items.txt`, from the 25-recipe Opus eval after T9).
- Modify: `TASKS.md`.

**Interfaces:**
- Consumes: `BehaviorSignature.of(String)`.
- Produces: `record Variety(int distinct, int total, Map<String, List<String>> repeats)` and `static Variety variety(List<Map.Entry<String, String>> labeledNbts)` in `ClaudeCraftEval`. The key is the recipe label and the value is the NBT. A result counts only if its label doesn't contain `(break down)` or `vanillaify` and its signature isn't `""`. `repeats` maps each signature shared by two or more results to their labels (with `wish: ` stripped).

- [ ] **Step 1: Write the failing test** `baselineVarietyIsPinned`. It reads the resource file (it has the same `label<TAB>nbt` format as `crafted_items.txt`, with the model prefix `opus-5-5: ` stripped from labels) and prints `distinct/total` and the repeats. First assert only `total > 0`, then run it and read the printed numbers.
- [ ] **Step 2: Run it**: `sh ./gradlew test --tests '*ClaudeCraftEval.baselineVarietyIsPinned*'`. It fails until `variety` exists. Implement `variety`, run it again, and note the printed `distinct/total`.
- [ ] **Step 3: Pin it.** Change the assertion to the exact printed `distinct` and `total`. This test is the ruler guard: a later change to the signature that moves the baseline fails here.
- [ ] **Step 4: Add the eval column.** In `compareModels`, add a `Distinct behaviors` column to the summary, `"%d/%d"` plus the repeated signatures as `sig: A, B` lines joined with `<br>`. Under each model's results table, add a "Repeated behaviors" list. Re-run `buildsPromptAndRequestForEveryRecipe` and `baselineVarietyIsPinned`; both pass.
- [ ] **Step 5: Record the baseline** in `TASKS.md` under a new open task: "**Variety + playtest (Part A)**: …baseline distinct behaviors on the 2026-10-25 Opus eval: D of T (repeats: …)", with the real numbers.
- [ ] **Step 6: Commit**: "Measure how many eval results behave differently". Include the baseline numbers in the message.

### Task 3: CraftId and PlaytestLog

**Files:**
- Create: `src/main/java/com/infinite_craft/playtest/CraftId.java`, `src/main/java/com/infinite_craft/playtest/PlaytestLog.java`
- Modify: `src/main/java/com/infinite_craft/ability/Abilities.java`: register `CraftId.TYPE` next to the other components (no tooltip appender: it shows nothing).
- Test: `src/test/java/com/infinite_craft/playtest/PlaytestLogTest.java`, `src/test/java/com/infinite_craft/playtest/CraftIdTest.java`

**Interfaces:**
- Produces:
  - `CraftId.TYPE`: `ComponentType<String>` registered as `infinite_craft:craft_id`, `Codec.STRING`.
  - `static Optional<String> CraftId.stampIfCustom(ItemStack stack)`: if the stack isn't an element (`ElementData.isElement`) and `!stack.getComponentChanges().isEmpty()`, set a new 8-hex-character random id on it and return it. Otherwise return empty and leave the stack untouched.
  - `static Optional<String> CraftId.of(ItemStack stack)`.
  - `record PlaytestLog.Input(String item, String name /* nullable: the custom name, i.e. the wish */, int count)`.
  - `record PlaytestLog.Craft(String id /* nullable */, String player, List<Input> inputs, String name, String base, String model /* nullable */, List<String> components, String signature, String verdict, long latencyMs, double costUsd, int count)`.
  - Static methods on `PlaytestLog`, each writing a single line:
    - `craft(Craft c)`, which writes event `craft`;
    - `craftFailed(String player, List<Input> inputs, String error)`, which writes `craft_failed`;
    - `use(String player, String id, String kind)`, which writes a `use` line only the first time an `(id, kind)` pair is seen, and always counts;
    - `flushUses()`, which writes one `uses` line `{id, player, counts:{kind:n}}` per id used since the last flush, then clears the counts;
    - `drop(String player, String id, int count)`;
    - `snapshot(String player, Map<String,Integer> held, String reason)`, where the reason is `periodic`, `left` or `stopping`.
  - Every line has `event` and `t` (epoch ms). It's written via `static Consumer<String> sink`, by default `InfiniteCraft.LOGGER::info`, and `static void reset()` clears state for tests. Each line is `"[InfiniteCraft/playtest] " + gson.toJson(obj)` with plain `new Gson()`, which escapes newlines and never pretty-prints. Count state is guarded for the craft thread and the server thread.

- [ ] **Step 1: Write the failing tests**
  - `PlaytestLogTest.minigunDoesNotFloodTheLog`: call `use("Sam","ab12cd34","use")` 600 times and `flushUses()` once. The sink captured exactly 2 lines: one `use` and one `uses` with `counts.use == 600`.
  - `PlaytestLogTest.eachKindLogsItsFirstUse`: `use` then `hit` then `use` gives 2 `use` lines.
  - `PlaytestLogTest.flushWithNothingUsedWritesNothing`.
  - `PlaytestLogTest.oddNamesStayOneLine`: a `Craft` named `"Bob's \"Mega\"\nGun §c🔥"` gives one line with no raw `\n`. Its JSON after the tag parses back to that exact name.
  - `PlaytestLogTest.everyLineHasTheTag`: every captured line starts with `[InfiniteCraft/playtest] {`.
  - `CraftIdTest.customItemIsStamped`: the Ray Gun example gets an 8-hex id. Two copies made from it with `copy()` have equal ids and `ItemStack.areItemsAndComponentsEqual`.
  - `CraftIdTest.plainVanillaIsNotStamped`: `new ItemStack(Items.GUNPOWDER, 5)` returns empty. It still `areItemsAndComponentsEqual` to a fresh gunpowder stack.
  - `CraftIdTest.elementsAreNotStamped`: `ElementItems.ELEMENT_FIRE.getDefaultStack()` returns empty.
- [ ] **Step 2: Run them and watch them fail**: `sh ./gradlew test --tests '*PlaytestLogTest*' --tests '*CraftIdTest*'` gives a compile failure.
- [ ] **Step 3: Implement** `CraftId` and `PlaytestLog`. Register `CraftId.TYPE` in `Abilities`.
- [ ] **Step 4: Run them and see them pass.** Also run `BehaviorSignatureTest`, and confirm `craft_id` doesn't change a signature.
- [ ] **Step 5: Commit**: "Log crafts and uses, a line a minute at most".

### Task 4: CraftHandoff

**Files:**
- Create: `src/main/java/com/infinite_craft/playtest/CraftHandoff.java`
- Modify: `src/main/resources/assets/infinite_craft/lang/en_us.json` (the code uses `translatableWithFallback`, so other languages fall back to English): add `title.infinite_craft.you_made` "You made", `title.infinite_craft.in_inventory` "You made (it's in your inventory)", and `title.infinite_craft.dropped` "You made (it's at your feet)".
- Test: `src/gametest/java/com/infinite_craft/test/CraftHandoffTest.java`, registered in `src/gametest/resources/fabric.mod.json`.

**Interfaces:**
- Produces:
  - `enum CraftHandoff.Where { SELECTED, HOTBAR, INVENTORY, DROPPED }`;
  - `record CraftHandoff.Placed(Where where, int slot /* -1 when dropped */)`;
  - `static Placed give(PlayerEntity player, ItemStack stack)`;
  - `static void announce(ServerPlayerEntity player, ItemStack stack, Placed placed)`.

**`give` order** (for each call, the whole stack): (1) the selected slot, if it's empty or holds an equal stack (`areItemsAndComponentsEqual`) with room: put or merge there, giving `SELECTED`; (2) another hotbar slot holding an equal stack with room: merge, select it, giving `HOTBAR`; (3) the first empty hotbar slot: put, select it, giving `HOTBAR`; (4) `insertStack`, giving `INVENTORY`; (5) whatever is left goes to `dropItem(stack, false)`, giving `DROPPED`. A partial merge carries the rest on to the next rule. It never writes to a slot holding a different item, and never touches `currentScreenHandler.getCursorStack()`.

**`announce`:**
- When `HOTBAR` or `SELECTED`, send `UpdateSelectedSlotS2CPacket(slot)`.
- Send title fade (5, 40, 10), title = `stack.getName()`, and subtitle = the `you_made` text, or `in_inventory` or `dropped` to match `where`.
- Play `SoundEvents.ENTITY_PLAYER_LEVELUP` at volume 0.4, pitch 1.4, to that player only.

- [ ] **Step 1: Write the failing game tests** (use `CraftedItemUseTest.player(context)` for the mock player and `ThrustTest.parse` for the Ray Gun example). Each one completes with `context.complete()`.
  - `emptyHandGetsIt`: selected slot 0 empty, so `SELECTED` slot 0, and slot 0 holds the gun.
  - `busyHandGoesToFirstEmptyHotbarSlot`: slots 0–2 hold dirt ×7, so `HOTBAR` slot 3 is selected, and slots 0–2 are still dirt ×7.
  - `fullHotbarGoesToInventory`: hotbar full of dirt, so `INVENTORY`, the selected slot is unchanged, the hotbar is unchanged, and the main inventory contains the gun.
  - `fullInventoryDrops`: every slot full, so `DROPPED`, and an `ItemEntity` holding the gun exists within 3 blocks.
  - `secondCopyStacks`: give the Energy Drink example (max stack 16) twice from one stamped stack, so both land in slot 0 with count 2.
  - `cursorStackIsUntouched`: `player.currentScreenHandler.setCursorStack(dirt ×3)`, give, and the cursor is still dirt ×3.
- [ ] **Step 2: Run and watch them fail**: `sh ./gradlew runGameTest` gives a compile failure.
- [ ] **Step 3: Implement `CraftHandoff`.**
- [ ] **Step 4: Run and see them pass**: `sh ./gradlew runGameTest`; all `CraftHandoffTest` tests pass and the existing 58 still pass.
- [ ] **Step 5: Negative control.** Temporarily drop the "is empty" condition from rule (1), run, and see `busyHandGoesToFirstEmptyHotbarSlot` fail (the dirt is overwritten). Put it back and run again to see it pass. Record both results for the commit message.
- [ ] **Step 6: Commit**: "Put a new craft in the player's hand". Include the negative control.

### Task 5: CraftResults, the process wiring and the hooks

**Files:**
- Create: `src/main/java/com/infinite_craft/playtest/CraftResults.java`, `src/main/java/com/infinite_craft/playtest/Playtest.java`, `src/main/java/com/infinite_craft/mixin/ServerPlayerEntityMixin.java` (add it to `infinite-craft.mixins.json`).
- Modify:
  - `src/main/java/com/infinite_craft/process/InfiniteCraftProcess.java`: the success and failure branches, `postWithRetry`, and a new `parseItemStackFromNbt` overload;
  - `src/main/java/com/infinite_craft/process/AiApi.java`: `doPostClaude` adds `"costUsd"` to the returned JSON;
  - `src/main/java/com/infinite_craft/ai/Claude.java`: `logUsage` returns the cost as a `double`;
  - `src/main/java/com/infinite_craft/vehicle/VehicleFire.java`: log `fire` on a successful shot;
  - `src/main/java/com/infinite_craft/InfiniteCraft.java`: call `Playtest.initialize()`.
- Test: `src/gametest/java/com/infinite_craft/test/CraftResultsTest.java`, registered in the gametest `fabric.mod.json`.

**Interfaces:**
- Consumes: Tasks 1, 3 and 4.
- Produces:
  - `record CraftResults.Meta(List<PlaytestLog.Input> inputs, long latencyMs, double costUsd, String verdict)`. The verdict is `clean`, `element`, or `partial: <codec error>`.
  - `static void CraftResults.deliver(ServerPlayerEntity player /* nullable */, ServerWorld world, BlockPos table, ItemStack result, int copies, Meta meta)`, which runs on the server thread:
    1. stamp once with `CraftId.stampIfCustom`;
    2. if the player is alive, send the existing "Crafted:" chat line per copy (as today), `CraftHandoff.give` each copy, and `announce` once with the first copy's `Placed`;
    3. otherwise spawn each copy as an `ItemEntity` at the table (as today);
    4. log one `PlaytestLog.craft` (player name, or `"?"` without a player) with `components` = the component keys of the encoded NBT and `signature` = `BehaviorSignature.of(result, registries)`;
    5. keep the element discovery bookkeeping exactly as `InfiniteCraftProcess` does now.
  - `static List<PlaytestLog.Input> CraftResults.inputs(List<ItemStack> used)`: the item id, the custom name if any, and the count.
  - `public static ItemStack InfiniteCraftProcess.parseItemStackFromNbt(String nbt, RegistryWrapper.WrapperLookup registries, Consumer<String> onDropped)`. The existing 2-arg overload delegates with a no-op.
  - `postWithRetry` returns `record CraftReply(ItemStack stack, double costUsd, String verdict)` (null on failure, as now), with cost summed over attempts. A failure calls `PlaytestLog.craftFailed` with the last error message.
  - `Playtest.initialize()` registers:
    - `UseItemCallback` and `UseBlockCallback` (kind `use`) and `AttackEntityCallback` (kind `hit`), each logging when the hand's stack has a craft id and returning `ActionResult.PASS`;
    - `ServerEntityEvents.EQUIPMENT_CHANGE` (kind `wear`, when a server player's new stack in a `HUMANOID_ARMOR` slot has an id);
    - `ServerTickEvents.END_SERVER_TICK`: `flushUses` every 1200 ticks, and `snapshot(..., "periodic")` for every player every 6000 ticks;
    - `ServerPlayConnectionEvents.DISCONNECT`: `flushUses` then `snapshot(..., "left")`;
    - `ServerLifecycleEvents.SERVER_STOPPING`: `flushUses` then a `stopping` snapshot for each player.
    - Snapshots count craft ids over every inventory slot, armor and offhand included.
  - `ServerPlayerEntityMixin` injects at HEAD of `dropItem(ItemStack, boolean, boolean)`: when `retainOwnership` (a player throw) and the stack has an id, it calls `PlaytestLog.drop`.

- [ ] **Step 1: Write the failing game tests** (`CraftResultsTest`). They capture `PlaytestLog.sink` into a list and restore it in `finally`:
  - `deliverLogsOneCraftLine`: deliver the Ray Gun to `null` player, with a fake `Meta` (inputs: iron ingot named "Ray Gun", redstone, emerald; verdict `clean`; cost 0.04). Exactly one `craft` line has `signature == "shoot(beam)"`, `inputs[0].name == "Ray Gun"`, an 8-hex `id`, and `costUsd == 0.04`.
  - `deadPlayerGetsResultAtTable`: same, with `player == null` and 2 copies. Two item entities sit within 2 blocks of the table pos, both holding equal stamped stacks, and still exactly one `craft` line.
  - `plainResultIsLoggedButNotStamped`: gunpowder ×5 gives a `craft` line with no `id` and `signature == ""`, and the dropped stack has no craft id.
- [ ] **Step 2: Run and watch them fail**: `sh ./gradlew runGameTest` gives a compile failure.
- [ ] **Step 3: Implement** `CraftResults`, the `Claude`/`AiApi`/`postWithRetry` cost and verdict plumbing, and replace both branches of `requestCraftResult` with `CraftResults.deliver` / `craftFailed`. The returned-ingredients path keeps its own `insertStack`. Latency is measured from the thread start to the reply. Then add `Playtest` and the mixin.
- [ ] **Step 4: Run and see them pass**: `sh ./gradlew runGameTest` (new and existing all pass) and `sh ./gradlew test` (unit tests, the eval skipped).
- [ ] **Step 5: Commit**: "Every craft goes through the hand-off and the playtest log".

### Task 6: The report script

**Files:**
- Create: `tools/playtest/report.py`, `tools/playtest/test_report.py`, `tools/playtest/testdata/session.log`, `tools/playtest/testdata/session.expected.md`
- Modify: `build.gradle`: add an `Exec` task `testPlaytestReport` running `python3 -m unittest discover -s tools/playtest -p 'test_*.py'`, and make `check` depend on it, so CI's `./gradlew build` runs it.

**Interfaces:**
- Consumes: the line format and fields from Task 3 (the tag, then JSON with `event`, `t`, and the per-event fields).
- Produces: the CLI `python3 tools/playtest/report.py LOG [LOG ...] [-o OUT.md]`. It reads plain or `.gz` files in the order given, and prints Markdown when there's no `-o`. Also `def build_report(lines: Iterable[str]) -> str` for tests.

**The report** (the spec's four sections; the layout within them is the builder's):
- **Session**: start and end clock times from the first and last `t`, length, crafts, failures, total cost (`$%.2f`).
- **Crafts**: a table with one row per `craft`, in order:
  - `#`;
  - `at` (mm:ss from the start);
  - `wish or recipe` (inputs, with named ones first as `"Ray Gun" (iron_ingot)`, then the others' paths joined by ` + `);
  - `result`;
  - `does` (the signature, or `—`);
  - `used` (`no`, or `use ×12, hit ×3`, adding up `uses` counts; a kind seen only in a first `use` line counts 1);
  - `held` (from craft to its `drop`, or to the end of the session);
  - `kept` (`yes` or `no` from the player's last snapshot holding that id; `—` for unstamped).
- **Variety**:
  - `distinct behaviors: D of T`, over crafts with an id and a non-empty signature;
  - each repeated signature with the result names sharing it;
  - "abilities never used" from `ABILITIES = ["shoot", "thrust", "launch", "dash", "aura", "vehicle"]` (a module constant; later parts append to it), meaning abilities that appear in no craft's signature.
- **Flags**: crafts never used, crafts dropped within 60 s, crafts whose verdict starts with `partial`, and failures with their error.

- [ ] **Step 1: Write the test data and the failing tests.**
  - `session.log` holds realistic Minecraft lines (`[14:02:11] [Server thread/INFO]: [InfiniteCraft/playtest] {...}`) mixed with unrelated lines. It contains:
    - 5 crafts, two of them sharing `shoot(beam)` (Plasma Blaster, Laser Pistol), one Jetpack (`thrust + wear(chest)`), one gunpowder breakdown (no id) and one Wolf-named oddity whose name has a quote and an emoji;
    - one `craft_failed`;
    - `use` and `uses` lines;
    - a `drop` 30 s after its craft;
    - `periodic` and `left` snapshots.
  - `session.expected.md` is hand-written to match the rules above.
  - Tests:
    - `test_golden`: `build_report(open(session.log))` equals the expected file;
    - `test_odd_names`: the oddity's name appears intact in the table, with `|` escaped as `\|` if present;
    - `test_gz_and_multiple_files`: split `session.log` into two files in a temp dir, gzip the first, run the CLI on both, and the output equals the golden;
    - `test_ignores_garbage`: a tagged line with broken JSON is skipped, not fatal;
    - `test_empty_log`: a log with no tagged lines prints a report saying "no crafts in this log".
- [ ] **Step 2: Run them and watch them fail**: `python3 -m unittest discover -s tools/playtest -p 'test_*.py'` fails with `ModuleNotFoundError: report`.
- [ ] **Step 3: Implement `report.py`** (stdlib only: `argparse`, `gzip`, `json`).
- [ ] **Step 4: Run them and see them pass**, then `sh ./gradlew testPlaytestReport`.
- [ ] **Step 5: Commit**: "Turn a playtest log into a report".

### Task 7: A real client end to end, and the docs

**Files:**
- Create: `src/gametest/java/com/infinite_craft/test/PlaytestClientGameTest.java`, registered in the gametest `fabric.mod.json` under `fabric-client-gametest`.
- Modify: `README.md` (CRLF, byte-safe): a "Playtest report" section on how to run a session, where `latest.log` is, the report command, and what each section means. `TASKS.md`: fill in Part A's entry with the results.

**Interfaces:**
- Consumes: `CraftResults.deliver`, `CraftId.of`, `report.py`.

- [ ] **Step 1: Write the client test** (pattern: `AuraClientGameTest`, starting with `ClientTestFilter.skip`). In singleplayer, survival:
  1. Fill hotbar slot 0 with dirt.
  2. On the server, `CraftResults.deliver(player, ..., the Ray Gun example, 1, fake Meta)`.
  3. Wait 5 ticks and take the screenshot `craft-handoff-title`.
  4. Assert on the client that the selected slot is 1 and holds a stack with a craft id, and that slot 0 is still dirt.
  5. Hold the use key for 20 ticks (with a no-AI husk 6 blocks ahead so the gun has a target).
  6. Run `item replace entity @a armor.chest with minecraft:iron_chestplate[infinite_craft:craft_id="wear-test"]`.
  7. Press the drop key once with the gun selected.
  8. Leave the world.
  9. Read `logs/latest.log` in the client test's run directory (wait up to 40 ticks for it to contain the `left` snapshot). Assert it has, for the gun's id:
     - one `craft` line;
     - a `use` line with kind `use`;
     - a `drop` line;
     - a `left` snapshot that no longer holds it;
     - and a `use` line `{id:"wear-test", kind:"wear"}`.
- [ ] **Step 2: Run it**: `sh ./gradlew runClientGameTest -PclientTests=Playtest`. Look at `build/run/clientGameTest/screenshots/` (`/bin/ls -t`) and check the screenshot shows the gun's name as a title with "You made" under it, and the hotbar highlight on slot 2 (index 1). If anything fails, use superpowers:systematic-debugging and read the screenshot first.
- [ ] **Step 3: Run the report on that real log**: `python3 tools/playtest/report.py build/run/clientGameTest/logs/latest.log`. The Ray Gun row says `use ×N`, `kept no`, and `dropped` in held; variety says 1 of 1. Paste the output into the PR body.
- [ ] **Step 4: Write the README section and the TASKS.md entry.**
- [ ] **Step 5: Full verification before the PR**:
  - `sh ./gradlew build`, which includes unit tests, `testPlaytestReport`, and game tests (all pass);
  - `sh ./gradlew runClientGameTest`, all client tests (8/8 pass, timeout 600000 ms).
- [ ] **Step 6: Commit**: "Check the hand-off and the log in a real client". Then use superpowers:requesting-code-review for a whole-branch review on Opus, fix what it finds, push `variety-playtest` to origin, and open the PR on the fork with the `pr` skill template. Wait for "merge it".
- [ ] **Step 7: Tell the user the playtest is ready.**
  - The playtest: play 30 minutes with their son, then run `python3 tools/playtest/report.py logs/latest.log`.
  - A follow-up eval run (about $0.95) would only add the new column against the same 25 recipes. Offer it, don't run it.
