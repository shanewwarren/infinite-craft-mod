# Infinite Craft Mod
[简体中文](docs/README.zh-CN.md)
[繁體中文](docs/README.zh-CHT.md)
[français](docs/README.fr.md)

This is an AI-based Minecraft Infinite Crafting mod that allows players to create new items with the help of AI.

*Unbalanced, may produce illegal item or duplicate items, just for fun!*

## Overview

Infinite Craft is a Fabric mod that adds an innovative crafting system to Minecraft. The mod uses Google's Gemini AI API to generate and handle new item crafting recipes.

## Quick Start

1. Download this mod from [Modrinth](https://modrinth.com/mod/infinite-craft-mod/versions) or [Github](https://github.com/RedstoneOre/infinite-craft-mod/releases/latest)
2. Use it in Minecraft 1.21.10 with Fabric & Fabric API
3. Create `config/InfiniteCraft.json` and launch the game
4. Close the game and put your [api key](https://aistudio.google.com/app/api-keys) to `"gemini"."api_key"` in `config/InfiniteCraft.json`
5. Set `"gemini"."model"` to `gemini-2.5-flash`
6. Restart the game

### Using Claude instead of Gemini

1. Get an API key from the [Claude Console](https://platform.claude.com/settings/keys)
2. In `config/InfiniteCraft.json`, set `"model"` to `"claude"` and put the key in `"claude"."api_key"`
3. `"claude"."model"` defaults to `claude-opus-5-5`. `claude-haiku-5-5` is roughly 40x cheaper per craft
4. Restart the game. Each craft's token usage and estimated cost are written to the game log

To compare models on a fixed set of recipes (spends a little API credit), run
`ANTHROPIC_API_KEY=... ./gradlew test --tests '*ClaudeCraftEval*' -DclaudeEval.models=claude-opus-5-5,claude-haiku-5-5`
and read `build/claude-eval/report.md`.

### Game tests: does each crafted item actually do something?

`./gradlew runGameTest` (also part of `./gradlew build`) starts a test world and uses items the way a survival player would:
drinks must apply their effects, weapons must hurt or launch a mob, armor must change the wearer's stats (and not end up weaker than its base item),
gliders must glide, guns must fire with no ammo in the inventory, tools must mine faster. Reports land in `build/gametest/`:
`prompt-examples.md` (the prompt's own examples, must all work) and `eval-items.md` (what Claude made in the last eval run, reported but
allowed to fail; a failure there means the prompt needs work). `ShootTest` fires the guns over real ticks at a mob.
`BlastTest` fires explosive and lightning shots at a wall of glass, planks, wool and torches: the mobs beside it must get hurt,
while the shooter's pet wolf, an armor stand and a dropped item must come through untouched, with no fire. Shots without destroy
must leave every block in place; rockets and destroy:true shots must blow holes in the wall, unless the gamerule is off
(the `blasts_keep_blocks` test environment).
`AuraTest` wears God Armor for a minute and checks its effects, real healing, that they come off with it, and that its aura
hurts a husk but not a pig or a pet wolf. `LaunchTest` and `DashTest` run on a 48-block runway (`data/infinite-craft-test/gametest/structure/runway.snbt`), since the default
test area is walled in by barriers at 8 blocks, which would cap any flight or dash at about 3 blocks.
`VehicleTest` places a Tank and a car from their items, drives them down the runway with a mock player at the wheel (the tank must
cover 25 blocks in 5 s, climb a 1-block step and turn in place; the car must be at least 1.6× faster), and fires the tank's gun from
the barrel. It checks the vehicle never hurts its rider: no fall damage, its own point-blank shell leaves it whole, rams hurt mobs but
never players, pets or armor stands, getting out never lands the rider in a wall. Sneak-hitting it gives the item back, and so does destroying it.

`./gradlew runClientGameTest` opens a real game window, holds right click with a Ray Gun and a Chain Gun at an iron golem,
checks the golem got hurt and saves screenshots to `build/run/clientGameTest/screenshots`. It also fires a rocket and a lightning wand
at a golem, and a rocket point-blank into a glass wall, which must blow a hole in the wall but leave the player unharmed
(and leave the wall whole with the gamerule off). It punches an iron golem
with a Mega Puncher, dashes in singleplayer, and triple-dashes on a dedicated server without `allow-flight`, where the server must agree
with the client on where the player ended up. `VehicleClientGameTest` places a Tank, drives it, fires at a husk and point-blank at the ground
(the rider must stay unhurt and the dirt in their hand unplaced), then drives for 30 s on a dedicated server without `allow-flight`.

Fabric runs every client test in one window. While working on one, `./gradlew runClientGameTest -PclientTests=Vehicle` runs only the
tests whose class name contains `Vehicle` (comma-separate several); without it they all run.

### Playtest report: what did they actually play with?

Every craft is logged to `logs/latest.log` as a line starting with `[InfiniteCraft/playtest]`: the ingredients (a renamed one is the
player's wish), the result, what it does, what it cost, and later whether it got used (right-click, hit, worn, fired from a vehicle),
thrown away, or was still in the inventory when the player left. Nothing leaves your machine. After a session, run

    python3 tools/playtest/report.py logs/latest.log

(put rotated `logs/*.log.gz` files from the same session before `latest.log`, or add `-o report.md` to save it). The report lists each
craft with what it does and how it was used, a variety score (how many crafts do different things, ignoring names, looks and bigger
numbers), the mod abilities that never came up, and flags: never used, thrown away within a minute, items Minecraft could only partly read,
and failed crafts. The eval's report has the same variety score (*Distinct behaviors*), computed by `BehaviorSignature`.

A new craft goes straight into the player's hand: the selected slot if it's free, else the first free hotbar slot (which gets selected),
with the item's name shown as a title. `CraftHandoffTest` and `PlaytestClientGameTest` check both, the latter in a real client
that fires, wears and throws away crafted items and then reads its own `latest.log`.

### Mod abilities Claude can give items

A field one of these components doesn't know (a typo, or a slot that doesn't exist) no longer vanishes without a word: the item still
works with everything else, and the craft is logged as *partial* (the eval grades it *usable*) with the field named. (A hand-typed
`/give` with an unknown field is refused as a malformed component.) A crafted item's cooldown locks only that craft and its copies,
not every crafted item, unless Claude gives it its own `cooldown_group`; this holds for items crafted before too.

`infinite_craft:shoot` makes any item fire while right click is held, with no ammo:
`{kind:"beam"|"bullet"|"rocket", damage:6.0f, interval:10, count:1, spread:0.0f, color:"green", fire:false, sound:"minecraft:..."}`.
`interval` is ticks between shots (4 is the fastest), `count` is shots per pull (shotguns). Respects `minecraft:use_cooldown`.
`kind:"rocket"` fires a slower firework-like rocket that always explodes. `explode:2.0f` (power 0 to 4) blows up where any shot lands,
`lightning:true` calls lightning there. `destroy:true` makes the explosion break blocks like TNT; rockets do unless they have
`destroy:false`, other shots don't unless they have `destroy:true`. To keep builds safe in a world, run
`/gamerule infiniteCraftBlastsBreakBlocks false`. Blasts never start fires, charge creepers or hurt the shooter,
their pets, armor stands or dropped items, and lightning never breaks blocks.
`effects:[{id:"minecraft:slowness",amplifier:3,seconds:6.0f}]` puts status effects on each mob a shot hits (a freeze ray), never on
the shooter or their pets; amplifier 0 to 3, 1 to 30 seconds, up to 4 effects.

`infinite_craft:launch` makes a melee hit fling the mob away in an arc with a cloud trail, even mobs that resist knockback:
`{power:2.0f, up:0.8f}`; power 1 lands a pig about 10 blocks away, 3 about 30; up 0 skims the ground, 2 goes nearly straight up.

`infinite_craft:dash` makes a worn (or held) item dash: press jump again in mid-air to dash the way you face.
`{power:1.0f, charges:1}`; power 1 goes about 8 blocks, `charges` is dashes before landing again. Works on servers without `allow-flight`.

`infinite_craft:aura` gives a worn (or held) item an aura: `{effects:[{id:"minecraft:regeneration",amplifier:0}], particle:"minecraft:end_rod",
color:"gold", damage:2.0f, radius:4.0f}`. Effects stay on while it's worn (night vision without flicker, regeneration healing at its
normal rate) and come off within half a second when it's removed; particles swirl around the wearer (dust in `color` if no `particle`);
`damage` hurts monsters within `radius` each second, never animals, pets or players.

`infinite_craft:thrust` makes a worn (or held) item a jetpack: hold jump to fly up, hold forward to zoom ahead.
`{power:1.0f, speed:1.5f}`; power 1 climbs about 7 blocks a second. Works on servers without `allow-flight`
(the floating kick is skipped while it fires). `equippable` `asset_id:"infinite_craft:jetpack"` shows tanks on the player's back.

`infinite_craft:vehicle` makes the item a vehicle you ride: use it on the ground to place it, right-click it to get in, W/S to drive,
A/D to steer, shift to get out. `{body:"tank"|"car", speed:6.0f, health:120.0f, color:"#556B2F", size:1.0f}`; speed is in blocks per second
(tank 6 and car 12 by default; running is about 5.6), size 0.5 to 2 scales it. A tank turns in place and its turret aims where the rider looks;
a car steers while moving. The item's `infinite_craft:shoot` is its gun: right click while driving fires it from the barrel, never from the hand.
It never hurts its rider (its own shells, rams, falls). Ramming hurts mobs, never players, pets or armor stands. Anyone can drive or pack up
any vehicle; sneak-hitting it gives the item back repaired, and a destroyed vehicle drops its item too. Ground vehicles only, for now.

`infinite_craft:summon` calls helpers that fight for the player: right-click and they appear on free ground around him.
`{entity:"minecraft:skeleton", count:5, seconds:60, gear:{mainhand:"minecraft:bow", head:"minecraft:iron_helmet"}, name:"Bones",
size:1.0f, color:"#E0E0E0"}`. Mobs: wolf, iron golem, snow golem, bee, fox, polar bear, llama, zombie, husk, drowned, skeleton, stray,
bogged, vindicator, pillager, vex (anything else is a wolf); count 1 to 6, 10 to 180 seconds, size 0.5 to 2. `gear` takes `mainhand`,
`offhand`, `head`, `chest`, `legs` and `feet`; armor shows on zombies, skeletons and illagers. They go for whatever attacks
him, whatever he attacks and monsters near him, and follow him around. They never target or hurt a player, a pet, a villager or another
summon (their arrows included); golems and pets leave them alone, and his own hits and blasts don't hurt them. They don't burn in daylight,
don't turn into drowned or strays, pick nothing up and drop no loot or xp. At most 12 per player (the oldest goes first) and a 3 s cooldown;
they vanish after their time, or when he leaves, dies or changes dimension.

`infinite_craft:attributes` is a consume effect for `minecraft:consumable`'s `on_consume_effects`: attribute modifiers for a while, which
vanilla potions can't do. A Shrink Potion: `{type:"infinite_craft:attributes", seconds:30.0f, modifiers:[{type:"minecraft:scale",
id:"infinite_craft:shrink", amount:-0.75d, operation:"add_multiplied_total"}]}` (the `attribute_modifiers` format without a slot;
1 to 600 seconds, up to 8 modifiers). Drinking another restarts the time instead of stacking; they come off when time runs out, on death,
and on logging out (they're never saved). A player whose size changes doesn't suffocate in a low ceiling while it lasts and for 3 s after.

### Item art

The mod ships its own item models (ray gun, blaster, minigun, lightsaber, jetpack, energy drink and more) that Claude can pick with
`"minecraft:item_model":"infinite_craft:ray_gun"`. Their glowing parts take the item's shoot color, or the first `minecraft:custom_model_data` color.
The art is ASCII in `tools/textures/make_textures.py`; edit a sprite and run `python3 tools/textures/make_textures.py preview.png` to regenerate
the textures, models and the list the prompt reads (`assets/infinite_craft/crafted_models.json`). `./gradlew runClientGameTest` screenshots them in game.

## System Requirements

- Minecraft 1.21.10
- Fabric Loader >= 0.17.3
- Java >= 21
- Fabric API

> For single-player or server users in mainland China, if you need to use Gemini through a proxy, add `-Djava.net.useSystemProxies=true` to the JVM arguments

## Configuration
The config file is `config/InfiniteCraft.json`, structure as follows:
```json
{
  "gemini": {
    "api_key": "...",
    "model": "...",
    "note": [
      "Gemini API Is Currently Free!",
      "the `model` can be: ",
      "    gemini-2.5-flash",
      "    gemini-2.5-flash-lite",
      "    or any other ones in https://ai.google.dev/gemini-api/docs/models",
      "For API key, generate here: https://aistudio.google.com/api-keys"
    ]
  },
  "claude": {
    "api_key": "...",
    "model": "claude-opus-5-5"
  },
  "proxy": "...",
  "model": "...",
  "lang": "en-US...",
  "description": {
    "proxy": "Proxy type, could be (none, system)",
    "model": "The model to use, could be (none, gemini, claude, ollama), use none if you only use the mod as a player of a server",
    "gemini": "The gemini config",
    "lang": "The language of the result, should be a language code: https://en.wikipedia.org/wiki/List_of_ISO_639_language_codes, but you actually can use the language name",
    "!!!": "This file will be overridden, so please save your important information somewhere else!"
  }
}
```
+ The proxy options cannot work for some reason so you still need to add `-Djava.net.useSystemProxies=true`
+ The config for ollama is currently not avalible!

## Features

1. AI-assisted crafting
   - Uses Google Gemini AI API to process item crafting
   - Supports dynamically generating new items and crafting recipes
   - Generates intelligent crafting results

2. Configuration
   - Configure AI API keys via a JSON config file
   - Config file location: `config/InfiniteCraft.json`

3. Vanillaify
   - Craft a `Vanillaify` item using this recipe:
    ![Vanillaify Recipe](docs/crafting%20vanillaify.png)
   - Combine it with other items in the crafting table

4. Element
   - Elements are special types of item that you can craft with it like [the original game](https://neal.fun/infinite-craft)
   - To Get basic element, you should craft a `Element Catcher`:
    ![Element Catcher](docs/crafting%20element%20catcher.png)
   - And you can crafting new elements/items with the element!

## Links

- Modrinth: https://modrinth.com/mod/infinite-craft-mod
- Github: https://github.com/RedstoneOre/infinite-craft-mod
- Gemini Docs: https://ai.google.dev/gemini-api/docs