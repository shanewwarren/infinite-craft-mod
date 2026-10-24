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

`./gradlew runClientGameTest` opens a real game window, holds right click with a Ray Gun and a Chain Gun at an iron golem,
checks the golem got hurt and saves screenshots to `build/run/clientGameTest/screenshots`.

### Mod abilities Claude can give items

`infinite_craft:shoot` makes any item fire while right click is held, with no ammo:
`{kind:"beam"|"bullet", damage:6.0f, interval:10, count:1, spread:0.0f, color:"green", fire:false, sound:"minecraft:..."}`.
`interval` is ticks between shots (4 is the fastest), `count` is shots per pull (shotguns). Respects `minecraft:use_cooldown`.

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