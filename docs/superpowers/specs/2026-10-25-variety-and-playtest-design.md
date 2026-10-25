# Variety and the instrumented playtest: design

## Intent

The user's son plays this. What the user saw while watching him (their words): first he has to find **where the new item got dropped in the inventory**, and items often have **a unique name but do essentially the same as some other weapon**. They also wondered what items that **aren't weapons** could do. What they want: **"a larger variety of fun effects"**. Explosions are already handled (T7) and stay as they are.

So this work has two halves:

1. **A judge.** Today the only evidence is the eval and the game tests, which check that items work, not that they're varied or that he enjoys them. Part A makes a real play session measurable (what was crafted, what it actually does, whether he used it, whether he kept it) and adds a variety score to the eval, so "more variety" is a number, not a feeling.
2. **More kinds of fun.** Part B teaches Claude the vanilla effects it ignores (shrinking, growing, low gravity, wall-stepping). Part C adds three new mod abilities the user picked: **summon**, **grappling hook** and **builder**, each designed so Claude can make every one different ("truly open ended", the user's words).

Plus the fix for the first complaint: a new item goes **into his hand**, with a clear "You made: …" moment.

## Goals

- A finished craft lands in the hotbar and is selected, with an on-screen title naming it and a short sound.
- Every craft and every first use is written to `logs/latest.log` as a structured line, and a script turns a log into a one-page playtest report.
- The eval reports a **variety score**: how many results behave differently from each other, judged by what the item does, not its name. The same "behavior signature" code is used in the eval and in the playtest log, so their numbers compare.
- Claude uses vanilla attributes for non-weapon fun (scale, gravity, step height and the rest), and potions can change them for a while (a Shrink Potion that shrinks you for 30 s).
- `infinite_craft:summon`: helpers that fight for the player.
- `infinite_craft:grapple`: a hook that flies to where you aim and pulls you there (or pulls a mob to you).
- `infinite_craft:build`: Claude designs a structure as a blueprint, and using the item builds it where you point, carving into hills.
- Each new ability has a prompt example, toolkit text, eval wishes, a FUNCTIONAL entry, a drawn item model, and a `CraftedItemUseTest` check.

## Non-goals

- T10 recipe memory, T11 first-discovery fanfare (the title in Part A is a plain "You made" for every craft, not the first-discovery fanfare), T13 generated textures, planes and boats.
- The deferred T9 tank issues (PR #9), unless the playtest report shows them.
- Sending playtest data anywhere. The log stays on this machine.
- Undo for the builder, a build preview outline, or saving and sharing blueprints.
- Summons that ride, breed, follow you across dimensions or stay forever.

## Anti-goals (failure even if it "works")

- **A summon hurts the player, another player or a pet**, or a summon is a boss. Helpers only (the user's answer): no Ender Dragon, Wither, Warden or Elder Guardian, and nothing that explodes on its own (creepers) or griefs (endermen picking up blocks).
- Summons pile up until the game lags, or never go away.
- **The builder destroys his stuff**: chests and other containers with their contents, beds, signs, anything with a block entity. Or it builds the player into a wall, or replaces bedrock or other unbreakable blocks.
- The builder carves through the world at a size that freezes the server.
- The grapple kills the player with fall damage, gets him kicked on a server ("flying is not enabled", "moved too quickly"), or flings him into a wall at lethal speed.
- The "You made" hand-off loses an item, or overwrites something he was holding.
- The playtest log floods `latest.log` (a minigun firing every 4 ticks must not write a line per shot), or slows the game.
- More abilities but the same sameness: if the eval's variety score doesn't go up, Part C didn't do its job.

## Constraints

- Minecraft 1.21.10, Fabric, yarn mappings. Builds use `-Werror` (no deprecated Fabric API).
- New ability code follows `src/main/java/com/infinite_craft/ability/`: a record component with a codec whose constructor clamps every field, plus a tooltip line, registered in `Abilities`. Item NBT parsing is lenient (a bad field falls back to defaults), so defaults must give a working item.
- `README.md` and `AiPrompt.java` are CRLF; edit them byte-safe. Prompt text blocks contain literal `\t` escapes. New files are LF.
- Never spend API credits without asking. The user runs the eval themselves on `claude-opus-5-5` (about $0.04 per craft, so the eval goes from about $0.95 to about $1.45 with the new wishes).
- Workflow: feature branch, PR into `main` on the fork, wait for "merge it", wait for CI, merge commit.
- **Shipping order** *(my call)*: one PR per part, A → B → C (summon, grapple, builder, in that order or as the builder sees fit), each with its own plan, tests and eval. Part A comes first because it's the judge for everything after it.

## Part A: the judge and the hand-off

### The hand-off

When a craft finishes (`InfiniteCraftProcess.requestCraftResult`, which today calls `insertStack`, so the item lands in the first free slot, often deep in the inventory):

1. If the selected hotbar slot is empty, put it there. Otherwise put it in the first empty hotbar slot and **select that slot** *(my call: he wants to try it straight away)*. If the hotbar is full, fall back to today's behavior (`insertStack`, then drop at his feet).
2. Show a title: the item's name, with "You made" as a small line above or below it, in the item's name color, for about 2 s. Play a short chime *(my call: `minecraft:entity.player.levelup` or similar, quiet)*.
3. The chat line "Crafted: N * name" stays as it is.
4. When a craft yields several copies (`finalMinInputItemStack` > 1), they stack into the same hotbar slot where they can.

The client needs no new code for this: titles and the selected slot are vanilla packets.

### The craft log

Each line in `latest.log` starts with a fixed tag and carries one JSON object, so the report script can find it among Minecraft's own output: `[InfiniteCraft/playtest] {"event":"craft",...}`.

To link uses back to crafts, each crafted **custom** item gets a hidden `infinite_craft:craft_id` component (a short random id). Copies from one craft share an id, so they still stack. It shows no tooltip. *(My call, from the builder check:)* "custom" means the result has any component beyond its base item's defaults (a name, an ability, a model, effects). Plain vanilla results (a breakdown's gunpowder, a Vanillaify's arrows) and elements are logged as crafts but not stamped, so they still stack with what he mined; their uses aren't tracked, which is fine because they aren't what we're judging.

Events:

| event | when | fields |
|---|---|---|
| `craft` | a result is given | id, time, player, inputs (item ids plus any custom names, i.e. the wish), result name, base item id, item model, the abilities and vanilla functional components it has, the behavior signature, the parse verdict (clean, or which components were dropped), latency, cost in dollars |
| `craft_failed` | the craft fails and ingredients are returned | time, player, inputs, the error |
| `use` | the **first** use of each kind per craft id: right-click, melee hit, wearing it (on equip), placing a vehicle, firing, summoning, grappling, building | id, kind, time |
| `uses` | about once a minute, per craft id that was used in that minute | id, counts per kind |
| `drop` | the player throws a crafted item out | id, time |
| `snapshot` | when the player leaves or the server stops, and every 5 minutes | the craft ids in the inventory and how many of each |

Use counts are kept in memory and flushed as `uses` lines, so the log grows by at most a few lines a minute however fast he fires. Cost comes from what `Claude.java` already calculates per craft.

### The behavior signature

A short string that says what an item **does**, ignoring its name, lore and model. Two items with the same signature feel the same to play with. It's built from:

- the mod abilities on it, each with the fields that change how it plays bucketed coarsely: `shoot` by kind, plus the explode, lightning and fire flags and its on-hit effect ids (Part B); `vehicle` by body; `summon` by mob type; `grapple` by `pull_mobs` and a short/long range bucket; `build` by size bucket (small, medium, large) and shape (tall, wide, long and flat) and whether it carves; `launch`, `dash`, `thrust` and `aura` by name plus aura effect ids;
- the vanilla functional components (consumable effect ids, attribute types, tool, glider, death protection, equippable slot).

Numbers like damage 6 vs. 7 don't change the signature. "Plasma Blaster" and "Laser Pistol", both a plain beam, get the same one; a "Freeze Ray" whose beam puts slowness on what it hits (Part B) gets a different one.

*(My call, from the builder check:)* the bucketing above is **fixed in Part A**, and the baseline is taken with it, before any prompt change. Later parts add buckets only for their own new fields and never re-bucket old ones, so the score can't be moved by changing the ruler. It lives in one class used by the eval, the craft log and the report.

### The report

`tools/playtest/report.py <log>` (plain Python 3, no dependencies; it reads `latest.log` and rotated `*.log.gz`) writes a Markdown report:

- **The session:** length, number of crafts, total cost, failures.
- **Each craft, in order:** the wish (renamed inputs) or the recipe, the result name, what it does (its signature in words), whether and how he used it, how long it stayed in the inventory, whether it was still there at the end, and whether it was dropped.
- **Variety:** the number of distinct signatures out of all crafts, the signatures that repeated (with the names that shared them), and the mod abilities that never came up.
- **Flags:** crafts never used, crafts dropped within a minute, items whose parse dropped components.

### The variety score in the eval

`ClaudeCraftEval` gets a new column: **distinct behaviors**, per model, the number of different signatures out of the number of results, plus a list of signatures that repeat and which items share them. *(My call, from the builder check:)* it counts only **custom functional results**: elements, breakdowns, Vanillaify results and plain vanilla items are left out, since they'd all share an empty signature and swamp the score. It also gets non-weapon wishes (Part B and C below).

A baseline comes first and costs nothing: run the signature over the last eval's `build/claude-eval/crafted_items.txt` and record the number in TASKS.md before any prompt change.

## Part B: vanilla fun Claude ignores (prompt plus one small effect)

- The toolkit text gets a "Fun without fighting" section: the `scale` attribute (shrink to 0.25 or grow to 3 on a worn or held item, e.g. a Giant Crown or a Shrink Ring), `gravity` (Moon Boots), `step_height` (Spider Gloves walk up walls of blocks), `movement_speed`, `jump_strength`, `block_interaction_range` and `entity_interaction_range` (Long Arms), `oxygen_bonus` and `water_movement_efficiency` (Fish Gills), `burning_time`, `mining_efficiency`, `sneaking_speed`, `fall_damage_multiplier`. The builder checks each id against 1.21.10's `EntityAttributes` and the `attributes` example in the prompt must parse.
- **Timed attributes for potions** *(my call: a potion can't set an attribute in vanilla, and a Shrink Potion is the iconic one)*: a new consume effect type `infinite_craft:attributes` {modifiers: the same list format as attribute_modifiers without the slot, seconds}, registered in vanilla's consume effect registry. It adds the modifiers for that many seconds and then takes them off, also on death or logging out. Stacking the same potion refreshes the time instead of stacking the effect.
- **On-hit effects for guns** *(my call: Freeze Ray and Shrink Ray were among the ideas the user liked, and `shoot` can't do it today)*: `shoot` gets an optional `effects` list in the aura format ({id, amplifier, seconds}), applied to each mob a shot hits. Same targeting rules as damage, so never the shooter or his pets. A Freeze Ray (slowness IV plus a frost particle color) and a Shrink Ray would also need a timed scale on mobs; the builder can reuse the timed-attribute effect for that, or leave shrinking mobs out if it fights vanilla.
- Prompt examples: Shrink Potion (consumable with the timed `scale` effect), Moon Boots (gravity plus safe_fall_distance) and Freeze Ray (a beam with slowness on hit).

## Part C: three new abilities

Each is a component on any base item, used with right-click, with a vanilla `minecraft:use_cooldown` if Claude gives one and a minimum built-in cooldown so it can't be spammed. Each gets a drawn item model via `tools/textures/make_textures.py`: *(my call)* `summon_horn`, `grappling_hook`, `blueprint`.

### `infinite_craft:summon`: helpers that fight for him

| field | meaning | default | clamp |
|---|---|---|---|
| `entity` | the mob type, from an allow-list | `minecraft:wolf` | must be on the list, else the default |
| `count` | how many per use | 1 | 1 to 6 |
| `seconds` | how long they stay | 60 | 10 to 180 |
| `name` | optional name shown above them | none | |
| `gear` | optional mainhand and head item ids (a skeleton with a crossbow, a zombie in a gold helmet) | none | items only, no NBT |
| `size` | scale attribute | 1 | 0.5 to 2 |
| `color` | optional particle and glow color, same codec as `shoot` | none | |

- **Allow-list** *(the user said helpers only; the exact list is the builder's)*: animals and golems that can fight (wolf, iron golem, snow golem, bee, fox, polar bear, llama, goat, axolotl, cat), plus classic monsters turned friendly (zombie, husk, skeleton, stray, bogged, piglin, vindicator, pillager, blaze, vex). Never bosses, creepers, endermen, ghasts, phantoms, wardens, the wither or the dragon, nor anything not on the list.
- **On his side:** summons attack hostile mobs near him, and whatever attacks him or whatever he attacks. They **never** target or hurt any player, any tamed pet, villagers or other summons. Hostile mob types get their own target goals replaced and don't burn in daylight. *(My call, from the builder check:)* the player's side leaves them alone too: iron golems, snow golems and tamed wolves don't target a summoned zombie or skeleton (a test with a golem beside a summoned zombie). Wild hostile mobs fight them as normal; that's the point. They don't drop loot or XP, don't pick up items, and don't despawn on their own except when their time runs out.
- **Limits:** at most 12 summons per player at once *(my call)*; summoning past that removes the oldest. They vanish with a puff when their time runs out, when he logs off, or when he changes dimension. They spawn around him on free ground, never inside blocks.
- **Safety, negative-controlled:** a summon hit by his own shot or blast isn't hurt (extend Blast's "shooter's pets" rule to his summons); a summon never damages a mock player or a tamed wolf.

### `infinite_craft:grapple`: grappling hook

| field | meaning | default | clamp |
|---|---|---|---|
| `range` | how far the hook flies | 24 | 8 to 48 |
| `speed` | how fast it reels him in, blocks per second | 16 | 6 to 30 |
| `pull_mobs` | true: a hooked mob is yanked to him instead | false | |
| `color` | rope color | gray | |

- Right-click fires a visible hook with a rope drawn from his hand. If it hits a block within range, it latches and reels him toward it while right-click is held; letting go releases the hook and keeps his momentum (swinging off is part of the fun). If it hits a mob and `pull_mobs` is set, the mob is pulled to him; otherwise a mob hit just releases. A miss reels the hook back.
- **No fall damage** from a landing within a few seconds of letting go, and no damage from flying into a wall.
- Works on a dedicated server with `allow-flight=false`: no kick, no "moved too quickly", with the player's movement driven the way dash and thrust already do it.

### `infinite_craft:build`: builder

The blueprint is the open-ended part: **Claude designs the structure for each item**, so every "Instant Castle" is different.

| field | meaning | default | clamp |
|---|---|---|---|
| `palette` | a map from one character to a block id, e.g. `{s:"minecraft:stone_bricks",g:"minecraft:glass",".":"keep"}`. A space means air, which **carves**: whatever is there is removed. `.` means "keep what's there" | | block states without NBT; unknown ids become air |
| `layers` | bottom-up list of layers; each layer is a list of rows of palette characters, rows going away from the player, characters going left to right | a small hut | at most 24 wide, 24 deep, 24 tall, and 6000 non-keep cells in total |
| `offset` | how far in front of where he points the near edge starts | 1 | 0 to 8 |

- **Placement:** he right-clicks a block; the structure is centered left to right on that spot, its near edge in front of it, rotated to face away from him (four rotations, block states rotated too), with layer 0 on the clicked block's level. Aiming at the sky builds at the end of his reach on the ground below, or does nothing if there's none in range.
- **Carving** (the user's answer: "carve into the hill, it's more fun that way"): structure cells replace whatever is there, and air cells clear it. Replaced blocks vanish without drops *(my call: a castle's worth of drops would lag)*.
- **Never replaced:** blocks with a block entity (chests and every other container, beds, signs, spawners, banners, shulker boxes), unbreakable blocks (hardness < 0: bedrock, barriers, portals, end portal frames), and the cells he or any other player stands in (they stay as they are). Fluids are replaced like any block.
- **A gamerule** `infiniteCraftBuildersCarve` *(my call, like `infiniteCraftBlastsBreakBlocks`)*, on by default; off means the builder only fills air.
- **It's a show:** the build rises layer by layer over about a second with block particles and a building sound *(my call)*. The whole structure is checked before the first block goes in, so a build never stops halfway.
- **Uses:** the item isn't used up; it has a cooldown of at least 5 s *(my call)*. Claude can make a one-shot builder with `minecraft:consumable` semantics if it wants, but the default is reusable.
- Prompt example: a small cottage (walls, door gap, glass windows, a roof) and a Bridge Stick (a 1-tall, 3-wide, 20-long path that carves a clear space above it).

## Judges (outside, run before claiming done)

**Part A**
1. A server game test: a crafted result given through the real hand-off code lands in the selected slot when it's empty, in the first empty hotbar slot (and that slot is selected) when it's not, and in the main inventory when the hotbar is full; nothing in his hand is ever replaced. Negative control: switch the "slot is empty" check off once and see the test catch the overwrite.
2. Unit tests for the signature: two beam guns with different damage and names share a signature; a beam gun and a rocket launcher don't; a beam on an item that also gives speed when held differs from a plain beam.
3. The baseline variety score on the last eval's items, recorded before any prompt change.
4. A canned log (checked into `tools/playtest/testdata/`) run through `report.py` gives the expected report; plus one real run: play a few crafts in `runClient`, quit, and run the report on that `latest.log`.
5. A spam check: fire a minigun-like item for 60 s in a game test; the log gets at most a handful of lines for it.

**Part B**
6. Game test: the Shrink Potion example makes the player's scale smaller and it returns to 1 after its time, also after death. Moon Boots lower gravity. A Freeze Ray beam gives a husk slowness and gives the shooter's pet wolf nothing (negative-controlled). Every new prompt example parses, and the signature test gains "a slowness beam differs from a plain beam".

**Part C**, each written first and watched fail, with each safety guard switched off once to see its test fail:
7. **Summon:** using the item spawns `count` mobs of the type; a summoned zombie attacks a husk and leaves a mock player and a tamed wolf untouched; it doesn't burn in daylight; summons are gone after `seconds`; the 13th summon removes the oldest; the player's own blast doesn't hurt them; an Ender Dragon or creeper in the field falls back to the default.
8. **Grapple:** fired at a wall 20 blocks away, the player ends within 2 blocks of it; landing after release does no damage; `pull_mobs` brings a husk to within 3 blocks. On a dedicated server with `allow-flight=false`, 30 s of grappling across a course with no kick and the client and server positions within 1 block.
9. **Builder:** a blueprint builds the expected blocks at the expected rotation in all four facings; it carves through a hill; a chest with a diamond in it, a bed, bedrock and the player's own cells are untouched; an oversized blueprint is clamped; the gamerule off leaves non-air blocks alone. A real-client screenshot of a built cottage.
10. **`CraftedItemUseTest`:** a summon item counts as working if mobs appear, a grapple if the player moves toward the hook, a builder if blocks change.

**Eval** (costs credits, about $1.45; ask the user before each run)
11. New wishes, at least: Wolf Whistle, Skeleton Army Horn, Bee Swarm Jar, Grappling Hook, Mob Lasso, Instant House, Castle in a Box, Bridge Stick, Shrink Potion, Moon Boots, Spider Gloves, Freeze Ray. *(My call: Mob Lasso replaces a "Hookshot", which means the same thing as a grappling hook and would rightly share its signature.)* **Pass:** each summon, grapple or build wish uses its ability; zero inert results; no two of the new wishes share a signature; the distinct-behavior score on Opus is higher than the baseline (target: at least 90% of custom functional results distinct). Then `runGameTest` on the results.

**The playtest** (the real judge, run by the user with their son, 30 minutes): the report says what it says. *(My target, not a pass mark:)* he uses at least 80% of what he crafts at least once, and a majority of his crafts have signatures distinct from each other.

## Left to the builder

- The exact signature bucketing and how its words read in the report.
- How the title looks (title plus subtitle, or an action-bar line) and which sound.
- Where use hooks go (Fabric callbacks vs. the existing ability call sites).
- The summon allow-list in detail, how hostile mob AI is swapped for friendly AI, and spawn placement.
- Grapple physics: rope rendering, the reel-in curve, how the hook entity syncs, what a mob hit without `pull_mobs` does.
- The blueprint's text format details (row direction, rotation of block states, how `keep` is spelled) as long as Claude can write it compactly and the prompt explains it with an example that parses.
- The three item models and their proportions.
- Whether each part needs its own design notes beyond this document, and the PR split within Part C.
