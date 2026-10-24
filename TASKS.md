# Tasks

Goal: crafting should feel like the "unlimited crafting" videos ([example](https://www.youtube.com/watch?v=TDes5gFBls0)), where every crafted item *does* something that matches its name: energy drinks, jetpacks, lightsabers, ray guns, mega punchers, god armor.

How we judge progress: `ClaudeCraftEval` (see README) plus the game tests (`runGameTest`, `runClientGameTest`), which use the items in a real world and check something happened. The eval writes its items to `build/claude-eval/crafted_items.txt` and `runGameTest` then uses those. Playtesting with the actual player beats all of them.

## Part 1: teach Claude the power toolkit (prompt only)

- [x] **T1. Power toolkit prompt.** *(done 2026-10-24: 0 inert on both models; Opus follows all the rules and its items are video-like, Haiku follows them loosely, so stay on Opus)* Catalog the 1.21.10 components that give items real behavior (consumable effects, attribute modifiers, equippable, glider, tool, weapon, cooldowns) and the vanilla base items that bring behavior for free (crossbow for guns, mace for punchers, elytra for flight). Rule: every result must do something that matches its name. Every example in the prompt must parse against 1.21.10.
- [x] **T2. "Does something" check + video wishes in the eval.** Flag custom items that are only a name and a model as *inert*. Add the video's items as wish recipes (an ingredient renamed in an anvil, e.g. a stick named "Ray Gun"). Target: no inert results on Opus or Haiku.

## Part 2: abilities in mod code (what vanilla can't do)

Claude picks and tunes abilities through the structured-output schema; the mod stores them on the item and runs them on use.

- [x] **T3. In-game use test.** *(done 2026-10-24: `CraftedItemUseTest`, see README "Game tests". First run on the T1 eval's 38 items: 35 work; both Blaze Bows fire 0 shots without arrows, and Haiku's God Armor gives 4 armor where a plain diamond chestplate gives 8)* Fabric game test that spawns a test player, uses each crafted item and checks for an effect (moved, target damaged, effect applied). Needed before T4, since from here on the behavior is our code, not Minecraft's.
- [x] **T4. Ability framework + Shoot.** *(done 2026-10-24, code side: `infinite_craft:shoot` component (beam or bullet, damage, interval, count, spread, color, fire), works on any base item including crossbows, respects use_cooldown. Server game tests and a real-client test pass: Ray Gun 20 damage and Chain Gun 44 damage to a golem in 2 s with no ammo. Follow-up eval on Opus: all three gun wishes use shoot with distinct settings (Ray Gun beam 8 dmg every 8t; Nail Gun bullet 4 dmg every 6t; Chain Gun bullet 3.5 dmg every 4t, spread 4), the Blaze Bow became an ammo-free fire bow, and all 20 items pass `runGameTest`. Claude picked loud or long per-shot sounds (an explosion on the Chain Gun), so the prompt now says to keep sounds short; not yet re-evaluated)* Beam or projectile with color, damage, rapid fire, no ammo needed (vanilla crossbow guns need arrows in survival). Covers ray gun, nail gun, chain gun.
- [ ] **T5. Thrust.** Real jetpack flight while holding use.
- [ ] **T6. Launch / dash.** Mega puncher, dash boots.
- [ ] **T7. Blast.** Explosion or lightning.
- [ ] **T8. Aura.** Ongoing effects and particles from worn or held items (god armor glow).
- [ ] **T9. Vehicles (stretch).** Rideable things like the tank need a new entity and model; scope separately.

## Other ideas

- [ ] **T10. Recipe memory.** Same ingredients give the same result per world; repeats are instant and free.
- [ ] **T11. First-discovery fanfare.** Sound, particles and a "First discovery!" message for new results.
- [ ] **Playtest.** Watch a 30-minute session, then read `logs/latest.log` (crafts, repeats, what got kept).
