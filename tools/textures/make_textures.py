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
}


def png(path, pixels):
    """pixels: 16 rows of 16 (r, g, b, a)"""
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
    # the list the prompt offers Claude (AiPrompt reads it)
    write_json(f"{ASSETS}/crafted_models.json",
               [{"model": f"infinite_craft:{name}", "description": description} for name, (_, _, description, _) in MODELS.items()])
    print(f"{len(MODELS)} models written")


if __name__ == "__main__":
    main()
