"""
Draws the mod's crafted-item art from the ASCII sprites below and writes, per model:
  textures/item/<name>.png        layer0, the body
  textures/item/<name>_glow.png   layer1, grayscale glowing parts, tinted in game (shoot color, custom_model_data, or default)
  textures/item/<name>_core.png   layer2, untinted highlights drawn over the glow (only if the sprite has any)
plus models/item/<name>.json and items/<name>.json.

Run from the repo root: python3 tools/textures/make_textures.py [preview.png]
The optional preview is every sprite at 16x with its default glow color, for eyeballing.
"""
import json
import os
import struct
import sys
import zlib

ASSETS = "src/main/resources/assets/infinite_craft"

# body colors
PALETTE = {
    "K": (0x1B, 0x1B, 0x22),  # outline
    "D": (0x3C, 0x3F, 0x48),  # dark metal
    "M": (0x6B, 0x72, 0x80),  # metal
    "L": (0xA8, 0xB0, 0xBC),  # light metal
    "W": (0xE6, 0xEA, 0xF0),  # shine
    "B": (0x7A, 0x4A, 0x2A),  # wood
    "b": (0x4F, 0x2F, 0x1A),  # dark wood
    "R": (0xC0, 0x39, 0x2B),  # red
    "r": (0x84, 0x22, 0x1A),  # dark red
    "O": (0xE6, 0x7E, 0x22),  # orange
    "Y": (0xF1, 0xC4, 0x0F),  # yellow
    "G": (0x4A, 0x7C, 0x3A),  # green
    "H": (0x2F, 0x52, 0x25),  # dark green
}
# glow layer, tinted in game: white takes the full tint color, gray a darker shade
GLOW = {"g": (0xFF, 0xFF, 0xFF), "h": (0xA0, 0xA0, 0xA0)}
# core layer, untinted, drawn on top of the glow
CORE = {"c": (0xFF, 0xFF, 0xFF), "y": (0xFF, 0xE0, 0x40)}

# name: (parent, default glow color, description for the prompt, sprite)
# "gun" holds a sprite whose barrel points right; "handheld" holds one pointing to the top right, like a sword
MODELS = {
    "ray_gun": ("gun", 0x33FF66, "retro ray gun with glowing rings", [
        "................",
        "................",
        "................",
        "................",
        "...KKKK.........",
        "..KLWWLK........",
        ".KLWLLLLKKKKKK..",
        ".KLLgLgLDDDDDDKg",
        ".KLLgLgLMMMMMMKg",
        ".KDLLLLLKKKKKK..",
        "..KDDMDK........",
        "...KKBBK........",
        "....KBBK........",
        "....KBBK........",
        ".....KK.........",
        "................",
    ]),
    "laser_blaster": ("gun", 0xFF3030, "sleek sci-fi laser blaster with a glowing stripe", [
        "................",
        "................",
        "................",
        "................",
        "................",
        "..KKKKKKKKKK....",
        ".KDMMMMMMMMDKKK.",
        ".KDggggggggDDDgg",
        ".KDDDDDDDDDDKKK.",
        "..KDDKKKKKKK....",
        "..KDDK.KK.......",
        "..KDDKK.........",
        "..KDDK..........",
        "...KK...........",
        "................",
        "................",
    ]),
    "nail_gun": ("gun", 0x66CCFF, "yellow power-tool nail gun", [
        "................",
        "................",
        "................",
        "................",
        "...KKKKKKKK.....",
        "..KYYYYYYYYKKK..",
        "..KYWYYYYgYKMMK.",
        "..KOYYYYYYYKMLLK",
        "..KOOOOOOOOKMMK.",
        "...KKDDKKKKKKK..",
        "....KDDK.KLLLK..",
        "....KDDK..KKK...",
        "....KDDK........",
        ".....KK.........",
        "................",
        "................",
    ]),
    "minigun": ("gun", 0xFF9933, "rotary minigun / chain gun with three barrels", [
        "................",
        "................",
        "................",
        "................",
        "....KKK.........",
        "...KDMDKKKKKKKKK",
        "..KDMMMKLLLLLLLg",
        "..KDMgMKKKKKKKKK",
        "..KDMMMKLLLLLLLg",
        "..KDMgMKKKKKKKKK",
        "..KDMMMKLLLLLLLg",
        "...KDMDKKKKKKKKK",
        "....KBK.........",
        "....KBBK........",
        ".....KK.........",
        "................",
    ]),
    "shotgun": ("gun", 0xFFAA33, "double-barrel shotgun with a wooden stock", [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".....KKKKKKKKKKK",
        "KKK.KMLLLLLLLLLK",
        "KBBKKMMMMMMMMMMK",
        "KBBBBKKKKKKKKKKK",
        ".KbBBBKBBK......",
        "..KbBKKKBK......",
        "...KK...KK......",
        "................",
        "................",
        "................",
    ]),
    "sniper_rifle": ("gun", 0x33DDFF, "long sniper rifle with a scope", [
        "................",
        "................",
        "................",
        "................",
        "....KKKKKK......",
        "...KDLWLLgK.....",
        "....KKKKKK......",
        "KK...K..K.......",
        "KBKKKKKKKKKKKKKK",
        "KBBBDDDDLLLLLLLK",
        "KBBBKKKKKKKKKKKK",
        ".KbBKDK.........",
        "..KKKDK.........",
        ".....K..........",
        "................",
        "................",
    ]),
    "rocket_launcher": ("gun", 0xFF7722, "green rocket launcher tube with a red rocket", [
        "................",
        "................",
        "................",
        "................",
        "................",
        "KKKKKKKKKKKK....",
        "gGGGGWGGGGGGKKK.",
        "gGGGGGGGGGGGKRRK",
        "gHHHHHHHHHHHKKK.",
        "KKKKKKDKKDKKK...",
        "......DK.DK.....",
        "......KK.KK.....",
        "................",
        "................",
        "................",
        "................",
    ]),
    "flamethrower": ("gun", 0xFF6A00, "flamethrower with a red fuel tank", [
        "................",
        "................",
        "................",
        "................",
        ".KKK............",
        "KRRRK...........",
        "KRWRK.KKKKKKKK..",
        "KRRRKKMMMMMMMMKg",
        "KRRRKKDDDDDDDDKg",
        "KRRRK.KKDKKKKK..",
        "KrRrK..KDK......",
        ".KKK...KDK......",
        ".......KK.......",
        "................",
        "................",
        "................",
    ]),
    "chainsaw": ("gun", 0xFFAA00, "orange chainsaw", [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..KKKKK.........",
        ".KOOOOOKKKKKKKK.",
        "KOWOOOOKMLMLMLMK",
        "KOOOgOOKMMMMMMMK",
        "KOOOOOOKMLMLMLMK",
        ".KDDDDDKKKKKKKK.",
        "..KDKDK.........",
        "..KKKKK.........",
        "................",
        "................",
    ]),
    "summon_horn": ("handheld", 0xFFD700, "curved summoning horn with glowing bands, for horns, bells and whistles that call helpers", [
        "................",
        "................",
        "..........KKKK..",
        ".........KWWWWK.",
        "KK.......KLLLLLK",
        "KbK.....KLgLLLLK",
        "KbbK....KLgLLLLK",
        ".KbBK...KLgLLLK.",
        ".KBBBK.KLLgLLK..",
        "..KBBBKBBBgLK...",
        "..KBgBBBBgBK....",
        "...KBgBBBgK.....",
        "....KKBBBK......",
        "......KKK.......",
        "................",
        "................",
    ]),
    "magic_wand": ("handheld", 0xCC66FF, "magic wand with a glowing star", [
        "............g...",
        "...........ggg..",
        "..........ggcgg.",
        "...........ggg..",
        "..........K.g...",
        ".........KBK....",
        "........KBK.....",
        ".......KBK......",
        "......KBK.......",
        ".....KBK........",
        "....KBK.........",
        "...KbK..........",
        "..KbK...........",
        ".KbK............",
        ".KK.............",
        "................",
    ]),
    "lightsaber": ("handheld", 0x3399FF, "lightsaber; the blade takes the item's color", [
        ".............gcc",
        "............gccg",
        "...........gccg.",
        "..........gccg..",
        ".........gccg...",
        "........gccg....",
        ".......gccg.....",
        "......gccg......",
        "......KKK.......",
        ".....KLWK.......",
        "....KDLK........",
        "...KLDK.........",
        "..KDLK..........",
        ".KLDK...........",
        ".KKK............",
        "................",
    ]),
    "power_fist": ("handheld", 0x33CCFF, "armored power fist / mega puncher gauntlet", [
        "................",
        "................",
        "...KKKKKKKK.....",
        "..KLLKLLKLLK....",
        "..KWLKWLKWLKK...",
        "..KLLKLLKLLKLK..",
        "..KMMKMMKMMKLK..",
        "..KKKKKKKKKKMK..",
        "..KMMMMMMMMMMK..",
        "..KMgggggggMK...",
        "..KMMMMMMMMMK...",
        "...KDDDDDDDK....",
        "...KDMDMDMDK....",
        "...KDDDDDDDK....",
        "....KKKKKKK.....",
        "................",
    ]),
    "jetpack": ("generated", 0xFF8800, "jetpack with two red tanks and flames", [
        "................",
        "...KKK....KKK...",
        "..KRWRK..KRWRK..",
        "..KRWRKKKKRWRK..",
        "..KRWRKDDKRWRK..",
        "..KRWRKDDKRWRK..",
        "..KRRRKDDKRRRK..",
        "..KMMMKDDKMMMK..",
        "..KRRRKDDKRRRK..",
        "..KMMMKKKKMMMK..",
        "..KDDDK..KDDDK..",
        "...KDK....KDK...",
        "...ggg....ggg...",
        "...hgh....hgh...",
        "....h......h....",
        "................",
    ]),
    "energy_drink": ("generated", 0x33FF66, "energy drink can; the band takes the item's color", [
        "................",
        ".....KKKKKK.....",
        "....KLWLLLLK....",
        "....KDDDDDDK....",
        "....KggggygK....",
        "....KgggyygK....",
        "....KggyyggK....",
        "....KgyyyygK....",
        "....KgggyggK....",
        "....KggyyggK....",
        "....KgyggggK....",
        "....KggggggK....",
        "....KDDDDDDK....",
        "....KLLLLLLK....",
        ".....KKKKKK.....",
        "................",
    ]),
    "tank": ("generated", 0xFF3030, "army tank with a turret and long gun; place it to drive it", [
        "................",
        "................",
        "................",
        "................",
        ".......KKKK.....",
        "......KGGGGKKKKK",
        "......KGHGGKMMMg",
        ".....KKKKKKKKKKK",
        "...KKGGGGGGGGGK.",
        "..KGGGGHGGGGHGGK",
        "..KHHHHHHHHHHHHK",
        ".KDDDDDDDDDDDDDK",
        ".KDMDDMDDMDDMDDK",
        "..KDDDDDDDDDDDK.",
        "...KKKKKKKKKKK..",
        "................",
    ]),
    "race_car": ("generated", 0xFFE040, "red race car on four wheels; place it to drive it", [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......KKKKK.....",
        ".....KLLWLLK....",
        "....KRLWLLLRK...",
        "..KKRRRRRRRRRKK.",
        ".KRRRRRrRRRRRRgK",
        ".KRRRRRrRRRRRRRK",
        ".KrrKKKrrrrKKKrK",
        "..KKDMDKKKKDMDK.",
        "....KDK....KDK..",
        ".....K......K...",
        "................",
    ]),
}


# Worn looks, drawn onto the chest part of the 64x32 humanoid armor layout (body box at u=16, v=16, 8x12x4).
# name: {face: sprite}; faces are front 8x12, back 8x12, right/left 4x12, top 8x4
EQUIPMENT = {
    "jetpack": {
        "back": [
            "KKK..KKK",
            "LWLKKLWL",
            "RWRDDRWR",
            "RWRDDRWR",
            "RWRDDRWR",
            "RRRDDRRR",
            "MMMDDMMM",
            "RRRDDRRR",
            "RRRKKRRR",
            "MMM..MMM",
            "DDD..DDD",
            ".K....K.",
        ],
        "front": [
            ".K....K.",
            ".K....K.",
            ".K....K.",
            ".KKKKKK.",
            ".K....K.",
            ".K....K.",
            ".K....K.",
            "........",
            "........",
            "........",
            "........",
            "........",
        ],
        "right": ["....", "....", "...R", "...R", "...R", "...R", "...M", "...R", "...R", "...M", "...D", "...."],
        "left": ["....", "....", "R...", "R...", "R...", "R...", "M...", "R...", "R...", "M...", "D...", "...."],
        "top": ["........", "........", "........", "RRR..RRR"],
    },
}
# where each chest face sits in the 64x32 armor texture
FACES = {"front": (20, 20), "back": (32, 20), "right": (16, 20), "left": (28, 20), "top": (20, 16)}


# Vehicle bodies (VehicleEntityModel): box-UV layouts, light so the vehicle's color tints them; dark parts stay dark.
# part: (u, v, width, height, depth, style); a box's UV region is (2*depth + 2*width) x (depth + height)
VEHICLE_TEXTURES = {
    "tank": ((256, 128), [
        (0, 0, 6, 10, 38, "track"),
        (0, 48, 20, 9, 34, "panel"),
        (88, 0, 28, 3, 36, "deck"),
        (108, 48, 14, 7, 14, "turret"),
        (164, 48, 3, 3, 20, "barrel"),
    ]),
    "car": ((128, 128), [
        (0, 0, 24, 7, 40, "body"),
        (0, 47, 20, 7, 16, "cabin"),
        (72, 47, 4, 8, 8, "wheel"),
    ]),
}


def vehicle_pixel(style, face, x, y, w, h):
    """Grayscale-ish color for a pixel of one face of a part; x, y within the face"""
    noise = ((x * 7 + y * 13) % 5) * 3
    if style == "track":
        if face in ("right", "left"):
            # treads along the length, wheels showing through
            if y in (0, h - 1): return (40, 40, 44)
            return (70, 70, 74) if (x // 3) % 2 == 0 and 2 <= y <= h - 3 else (48, 48, 52)
        return (52 + noise, 52 + noise, 56 + noise) if x % 3 else (36, 36, 40)
    if style == "barrel":
        if face == "front": return (20, 20, 22)
        return (150 + noise, 150 + noise, 150 + noise)
    if style == "wheel":
        if face in ("right", "left") and 2 <= x <= w - 3 and 2 <= y <= h - 3: return (150, 150, 155)
        return (35, 35, 38)
    if style == "cabin" and face != "top" and face != "bottom" and 1 <= y <= h - 2 and 1 <= x <= w - 2:
        return (70, 85, 105)
    base = {"panel": 205, "deck": 220, "turret": 225, "body": 235, "cabin": 235}[style]
    c = base + noise
    # panel lines and rivets
    if style in ("panel", "deck", "turret") and (x % 8 == 7 or y == h - 1): c -= 45
    if style == "turret" and face == "top" and 4 <= x <= 7 and 4 <= y <= 7: c = 90
    if style == "body" and face in ("right", "left") and x in (w // 3, 2 * w // 3): c -= 60
    return (c, c, c)


def vehicle_texture(size, parts):
    tw, th = size
    img = [[(0, 0, 0, 0)] * tw for _ in range(th)]
    for u, v, w, h, d, style in parts:
        faces = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
        }
        for face, (fx, fy, fw, fh) in faces.items():
            for y in range(fh):
                for x in range(fw):
                    img[fy + y][fx + x] = (*vehicle_pixel(style, face, x, y, fw, fh), 255)
    return img


def png(path, pixels):
    """pixels: rows of (r, g, b, a)"""
    raw = b"".join(b"\0" + bytes(c for px in row for c in px) for row in pixels)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def layer(sprite, colors):
    return [[(*colors[ch], 255) if ch in colors else (0, 0, 0, 0) for ch in row] for row in sprite]


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    known = set(PALETTE) | set(GLOW) | set(CORE) | {"."}
    os.makedirs(f"{ASSETS}/textures/item", exist_ok=True)
    preview = []
    for name, (parent, default, _, sprite) in MODELS.items():
        assert len(sprite) == 16 and all(len(row) == 16 for row in sprite), f"{name} is not 16x16"
        assert set("".join(sprite)) <= known, f"{name} uses unknown colors {set(''.join(sprite)) - known}"
        has_core = any(ch in CORE for row in sprite for ch in row)

        textures = {"layer0": f"infinite_craft:item/{name}", "layer1": f"infinite_craft:item/{name}_glow"}
        png(f"{ASSETS}/textures/item/{name}.png", layer(sprite, PALETTE))
        png(f"{ASSETS}/textures/item/{name}_glow.png", layer(sprite, GLOW))
        if has_core:
            png(f"{ASSETS}/textures/item/{name}_core.png", layer(sprite, CORE))
            textures["layer2"] = f"infinite_craft:item/{name}_core"
        textures["particle"] = "#layer0"
        write_json(f"{ASSETS}/models/item/{name}.json", {
            "parent": {"gun": "infinite_craft:item/gun", "handheld": "minecraft:item/handheld"}.get(parent, "minecraft:item/generated"),
            "textures": textures,
        })
        write_json(f"{ASSETS}/items/{name}.json", {"model": {
            "type": "minecraft:model",
            "model": f"infinite_craft:item/{name}",
            "tints": [
                {"type": "minecraft:constant", "value": -1},
                {"type": "infinite_craft:glow_color", "default": default},
                {"type": "minecraft:constant", "value": -1},
            ],
        }})

        # preview: body, glow tinted with the default color, core on top
        tint = ((default >> 16) & 255, (default >> 8) & 255, default & 255)
        def px(ch):
            if ch in PALETTE: return PALETTE[ch]
            if ch in GLOW: return tuple(g * t // 255 for g, t in zip(GLOW[ch], tint))
            if ch in CORE: return CORE[ch]
            return (0x2A, 0x2D, 0x34)
        preview.append([[px(ch) for ch in row] for row in sprite])

    if len(sys.argv) > 1:
        scale, cols = 16, 5
        rows = (len(preview) + cols - 1) // cols
        size = 16 * scale + 8
        img = [[(0x2A, 0x2D, 0x34, 255)] * (cols * size) for _ in range(rows * size)]
        for i, sprite in enumerate(preview):
            ox, oy = (i % cols) * size + 4, (i // cols) * size + 4
            for y in range(16 * scale):
                for x in range(16 * scale):
                    img[oy + y][ox + x] = (*sprite[y // scale][x // scale], 255)
        png(sys.argv[1], img)
    os.makedirs(f"{ASSETS}/textures/entity/equipment/humanoid", exist_ok=True)
    os.makedirs(f"{ASSETS}/equipment", exist_ok=True)
    for name, faces in EQUIPMENT.items():
        texture = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
        for face, sprite in faces.items():
            ox, oy = FACES[face]
            for y, row in enumerate(sprite):
                for x, ch in enumerate(row):
                    if ch in PALETTE:
                        texture[oy + y][ox + x] = (*PALETTE[ch], 255)
        png(f"{ASSETS}/textures/entity/equipment/humanoid/{name}.png", texture)
        write_json(f"{ASSETS}/equipment/{name}.json", {"layers": {"humanoid": [{"texture": f"infinite_craft:{name}"}]}})

    os.makedirs(f"{ASSETS}/textures/entity/vehicle", exist_ok=True)
    for name, (size, parts) in VEHICLE_TEXTURES.items():
        png(f"{ASSETS}/textures/entity/vehicle/{name}.png", vehicle_texture(size, parts))
    # the list the prompt offers Claude (AiPrompt reads it)
    write_json(f"{ASSETS}/crafted_models.json",
               [{"model": f"infinite_craft:{name}", "description": description} for name, (_, _, description, _) in MODELS.items()])
    print(f"{len(MODELS)} models written")


if __name__ == "__main__":
    main()
