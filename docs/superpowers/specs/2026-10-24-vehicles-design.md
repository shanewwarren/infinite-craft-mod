# T9 Vehicles: design

## Intent

The user's son wants to craft **a tank he can drive around and blow stuff up** (his words, via the user). That is the whole point of T9: a crafted "Tank" shows up in the world, he climbs in, steers it, aims the turret and fires shells that leave craters. Vehicles join the other powers (shoot, thrust, launch, dash, aura): Claude writes an `infinite_craft:vehicle` component on the crafted item and tunes it to the name, so a "Mini Tank" and a "Mega Tank" differ, and a "Race Car" is a fast car with no gun (or a minigun).

## Goals

- A crafted vehicle item, used on the ground, places a rideable vehicle entity there (like placing a boat).
- Right-click the vehicle to get in. W/S drive forward and back, A/D turn the body, the mouse aims (the turret follows the rider's look), right-click (held) fires, shift gets out. All vanilla controls.
- The vehicle fires the item's existing `infinite_craft:shoot` component (rockets for a tank), with no new weapon code: blasts, `destroy`, and the `infiniteCraftBlastsBreakBlocks` gamerule behave exactly as for hand-held guns.
- It climbs one-block steps, so it doesn't stall on every bump.
- It looks like what it is: a drawn 3D tank (hull, two tracks, turret, barrel) or car (body, cabin, four wheels), tinted with the item's color. The item in the hotbar gets a drawn tank and car sprite too.
- Works in multiplayer on a dedicated server (`allow-flight=false`): no kicks, no rubber-banding.
- Claude knows about it: a prompt example (Tank) and toolkit text, an eval wish and a FUNCTIONAL entry.

## Non-goals (for now)

- Planes, boats, hovercraft, mechs. The user said "ground first is fine, planes can come later". Design the component so a `mode` (ground, later fly or water) can be added without breaking saved items, but build only ground.
- Several seats, passengers, fuel, ammo, inventories.
- New weapon kinds: the vehicle reuses `shoot` as it is.

## Anti-goals (failure even if it "works")

- The vehicle hurts its own rider: from its own shells (including point-blank), from ramming, from falls or crash landings, or from suffocating when it gets out.
- The kid loses the tank: it drives off without him, despawns, or breaks with nothing dropped.
- It breaks multiplayer: "moved wrongly" rubber-banding, kicks, or other players' clients crashing or showing a pig.
- A tank that's slower than walking or can't get over a single block, so driving feels worse than running.
- Driving through or breaking blocks by touch. Only shells break blocks, and only under the existing `destroy` and gamerule rules.

## Constraints

- Minecraft 1.21.10, Fabric, yarn mappings. The mod already runs on both client and server (`environment: "*"`, client mixins), so a new entity type and renderer are fine.
- Code style follows `src/main/java/com/infinite_craft/ability/`: a `VehicleComponent` record with a codec whose constructor clamps every field, plus tooltip lines, registered in `Abilities`.
- Item NBT parsing is lenient (a bad field falls back to defaults), so defaults must give a working tank.
- CRLF files (`README.md`, `AiPrompt.java`) are edited byte-safe. Prompt text blocks contain literal `\t` escapes.
- Never spend API credits without asking; the user runs the eval themselves.
- Workflow: feature branch, PR into `main` on the fork, wait for "merge it", then wait for CI.

## How it works (decided)

### The component: `infinite_craft:vehicle`

| field | meaning | default | clamp |
|---|---|---|---|
| `body` | `"tank"` or `"car"`: model and handling | `"tank"` | enum |
| `speed` | top speed in blocks per second | tank 6, car 12 | 2 to 20 |
| `health` | vehicle hit points | tank 100, car 40 | 10 to 300 |
| `color` | hull or paint color: a name, `"#RRGGBB"` or an int, same codec as `shoot` | tank olive `#556B2F`, car red | |
| `size` | scale of the whole vehicle (model, hitbox, barrel tip), via vanilla's scale attribute | 1 | 0.5 to 2 |

The weapon is the same item's `infinite_craft:shoot`, if any. *(My call, from the builder check:)* on an item that has `vehicle`, `shoot` fires **only from the placed vehicle**. Using the item places it (on a block) or does nothing (in the air); the held item is never a hand-held gun. Example Tank:
`{id:"infinite_craft:custom_crafted_item",components:{"minecraft:item_model":"infinite_craft:tank","minecraft:item_name":{text:"Tank",color:"dark_green"},"minecraft:max_stack_size":1,"infinite_craft:vehicle":{body:"tank",speed:6.0f,health:120.0f,color:"#556B2F"},"infinite_craft:shoot":{kind:"rocket",damage:10.0f,explode:3.0f,interval:30}}}`

Running speed is about 5.6 blocks/s, so the tank's default is a little faster than running and the car is about twice that.

### Handling

- **Tank:** W/S drive along the hull's facing (reverse at half speed). A/D rotate the hull in place at a fixed turn rate. The turret and barrel point where the rider looks.
- **Car:** W/S as above. A/D steer, and the car only turns while it's moving. There's no turret; the gun, if any, fires where the rider looks.
- Both: step height 1, gravity, no jumping. Speed ramps up over about half a second, not instantly (a game-feel choice; tune by driving).

### The entity: `infinite_craft:vehicle`

- One entity type for every body. It extends a vanilla mob class so vanilla's ridden-mob path does the movement and multiplayer sync (`getControllingPassenger`, `getControlledMovementInput`, `tickControlled`, `getSaddledSpeed`, the same hooks horses and pigs use). The client moves the vehicle it is driving and the server checks it, as for horses.
- It stores the whole item stack that placed it (synced to clients), so it knows its body, speed, color and weapon on both sides, and a saved world keeps them.
- No AI: it never wanders, never despawns, makes no mob sounds, can't be leashed or bred. It isn't pushed by mobs or water currents while parked.
- **Anyone can use any vehicle** (the user's answer): any player can get in, drive, pack it up or break it. No owner is stored.
- **What damages it** *(my call)*: mobs, explosions and other players' attacks. Its own shells, its rider and ramming never do. The rider is not shielded by the vehicle; mobs and arrows can hit them as when riding a horse (except their own shells, as always).
- **Health isn't remembered** *(my call)*: packing it up and placing it again gives a fully repaired vehicle.
- **Water** *(my call)*: it doesn't drown; it drives slowly through shallow water and sinks in deep water, where the rider can get out as usual.
- **Getting the item back:** a player who hits it while sneaking packs it back into the item (in creative too, like a boat). When its health runs out it breaks with a small explosion effect (no block damage) and **drops the item**, so the kid never loses his tank. Lava and the void are the only exceptions.

### Firing

While riding, a right-click (held) fires instead of using whatever is in the rider's hand. Client side, intercept the use key when the player's vehicle is one of ours and send a small "fire" packet. The server checks that the sender is the vehicle's rider and calls `ShootAbility` with the rider as the shooter and the vehicle's stored item stack as the gun, so interval, cooldown, sound and blasts all come from the existing code. Shots start from the turret's barrel tip, not the rider's eyes, so a point-blank shot doesn't hit the tank itself.

### Safety

- Blasts already spare the shooter, their pets, armor stands and dropped items. Extend "the shooter's" to the vehicle they're riding (the shell doesn't damage the tank or the rider sitting in it).
- No fall damage for the rider while riding, and none for the vehicle up to a reasonable height (driving off a 3-block ledge shouldn't hurt anyone).
- **Ramming:** driving into a mob at speed hurts it and knocks it aside. It never hurts players (the server's PvP setting doesn't apply to ramming), the rider's own pets, or armor stands.
- Getting out places the rider beside the vehicle on solid ground, not inside a block.

### Looks

- Client: an entity renderer with a drawn cuboid model per body, registered with Fabric's entity model layer and renderer registries. The hull is tinted with `color`, the tracks and wheels stay dark, and the turret yaw and barrel pitch come from the rider's look.
- Items: `tools/textures/make_textures.py` gains `tank` and `car` sprites and models, listed in `crafted_models.json`, so `"minecraft:item_model":"infinite_craft:tank"` works.

## Judges (outside, run before claiming done)

1. **`VehicleTest`** (server game test on `runway.snbt`): place a Tank from its item, mount a mock player, hold forward for 5 s; it covers at least 25 blocks (6 blocks/s minus ramp-up), stays on the ground and climbs a 1-block step. A car covers about 2×. Written first and watched fail.
2. **Safety tests**, each negative-controlled (switch the guard off once and watch the test fail, as in PRs #3, #6 and #8):
   - a point-blank shell leaves the tank and its rider unhurt, and breaks blocks only when the gamerule allows it;
   - a ram hurts a zombie but not a mock player or a tamed wolf;
   - driving off a 4-block ledge hurts neither the vehicle nor the rider;
   - breaking the vehicle drops its item, and hitting it while sneaking returns the item.
3. **Real client** (`runClientGameTest`): place the tank, get in, hold W for 3 s and check the distance, fire at a target, and take a screenshot of riding it from third person.
4. **Dedicated server** with `allow-flight=false`, as `LaunchDashClientGameTest` does: drive 30 s over rough ground. No kick, no "moved wrongly" in the server log, and the client and server positions end within 1 block of each other.
5. **`CraftedItemUseTest`**: a crafted vehicle item counts as working when using it places a vehicle that moves when ridden.
6. **Eval** (`ClaudeCraftEval`): add wishes "Tank" and "Race Car", and FUNCTIONAL entries for the vehicle component. Pass: both get `infinite_craft:vehicle` with a fitting body, the Tank has a `shoot` with an explosion, and both pass `runGameTest`. **Costs credits (~$0.85); ask the user before running.**

## Left to the builder

- Which vanilla mob class to extend (the decompiled `PigEntity`, `AbstractHorseEntity` and `HappyGhastEntity` show the ridden-mob hooks), and the exact sync fields.
- Turn rate, ramp-up curve, model proportions and texture details. Tune by driving it and looking at screenshots.
- Packet names and the client hook for the fire key.
- Where step-height and fall-damage overrides live (attributes or overrides).
