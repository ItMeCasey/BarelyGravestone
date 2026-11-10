"""Draws the mod's textures (original art, no Minecraft textures) and writes the shared model and language files.

Run from the project folder:  python scripts/make_assets.py
Needs Pillow. Writes into common/src/main/resources/assets/barelygravestone.
"""
import json
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'common', 'src', 'main', 'resources', 'assets', 'barelygravestone')


def save(img, *parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write_json(data, *parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


def shade(color, amount):
    return tuple(max(0, min(255, int(c + amount))) for c in color) + (255,)


def gravestone_texture():
    """Top half: weathered grey stone for the base. Bottom half: dark wood for the pike."""
    rng = random.Random(7)
    img = Image.new('RGBA', (16, 16))
    stone = (122, 122, 130)
    wood = (92, 62, 38)
    for y in range(16):
        for x in range(16):
            if y < 8:
                n = rng.randint(-14, 14)
                if (x + y * 3) % 7 == 0:
                    n -= 16
                img.putpixel((x, y), shade(stone, n))
            else:
                n = rng.randint(-8, 8) + (10 if x % 4 < 1 else 0) - (8 if x % 4 == 3 else 0)
                img.putpixel((x, y), shade(wood, n))
    return img


def obituary_icon():
    """A closed book: dark leather cover, parchment edge, gold clasp and a red ribbon."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cover = (74, 44, 26)
    edge = (232, 216, 174)
    gold = (242, 179, 61)
    ribbon = (160, 40, 32)
    outline = (30, 18, 10, 255)
    for y in range(2, 14):
        for x in range(3, 13):
            img.putpixel((x, y), shade(cover, 8 if x < 5 else (-6 if x > 10 else 0)))
    for x in range(3, 13):
        img.putpixel((x, 2), outline)
        img.putpixel((x, 13), outline)
    for y in range(2, 14):
        img.putpixel((3, y), outline)
        img.putpixel((12, y), outline)
    # page edge at the bottom
    for x in range(4, 12):
        img.putpixel((x, 12), shade(edge, 0))
    # gold clasp and border
    for x in range(5, 11):
        img.putpixel((x, 4), shade(gold, 0))
    for x in range(5, 11):
        img.putpixel((x, 10), shade(gold, -30))
    for y in range(5, 10):
        img.putpixel((5, y), shade(gold, -20))
        img.putpixel((10, y), shade(gold, -20))
    # a small cross on the cover
    for y in range(5, 10):
        img.putpixel((7, y), shade(gold, 25))
        img.putpixel((8, y), shade(gold, 25))
    for x in range(6, 10):
        img.putpixel((x, 6), shade(gold, 25))
    # ribbon hanging from the bottom
    for y in range(12, 15):
        img.putpixel((9, y), shade(ribbon, 0))
        img.putpixel((10, y), shade(ribbon, -20))
    img.putpixel((9, 15), shade(ribbon, -20))
    img.putpixel((10, 15), shade(ribbon, -40))
    return img


def main():
    save(gravestone_texture(), 'textures', 'block', 'gravestone.png')
    save(obituary_icon(), 'textures', 'item', 'obituary.png')

    write_json({
        'textures': {'tex': 'barelygravestone:block/gravestone', 'particle': 'barelygravestone:block/gravestone'},
        'elements': [
            {  # stone base
                'from': [3, 0, 3], 'to': [13, 2, 13],
                'faces': {
                    'down': {'uv': [0, 0, 8, 8], 'texture': '#tex'},
                    'up': {'uv': [0, 0, 8, 8], 'texture': '#tex'},
                    'north': {'uv': [0, 0, 8, 2], 'texture': '#tex'},
                    'south': {'uv': [0, 0, 8, 2], 'texture': '#tex'},
                    'west': {'uv': [0, 0, 8, 2], 'texture': '#tex'},
                    'east': {'uv': [0, 0, 8, 2], 'texture': '#tex'},
                },
            },
            {  # the pike
                'from': [7, 2, 7], 'to': [9, 9, 9],
                'faces': {
                    'up': {'uv': [0, 8, 2, 10], 'texture': '#tex'},
                    'north': {'uv': [0, 9, 2, 16], 'texture': '#tex'},
                    'south': {'uv': [0, 9, 2, 16], 'texture': '#tex'},
                    'west': {'uv': [2, 9, 4, 16], 'texture': '#tex'},
                    'east': {'uv': [2, 9, 4, 16], 'texture': '#tex'},
                },
            },
        ],
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, -2, 0], 'scale': [0.7, 0.7, 0.7]},
            'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        },
    }, 'models', 'block', 'gravestone.json')
    write_json({'parent': 'barelygravestone:block/gravestone'}, 'models', 'item', 'gravestone.json')
    write_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'barelygravestone:item/obituary'}},
               'models', 'item', 'obituary.json')
    write_json({'variants': {'': {'model': 'barelygravestone:block/gravestone'}}}, 'blockstates', 'gravestone.json')

    write_json({
        'itemGroup.barelygravestone': 'Barely Gravestone',
        'block.barelygravestone.gravestone': 'Gravestone',
        'item.barelygravestone.obituary': 'Obituary',
        'item.barelygravestone.obituary.named': 'Obituary of %s',
        'tooltip.barelygravestone.obituary.died': 'Died in %s',
        'tooltip.barelygravestone.obituary.use': 'Right-click to read. Hold it to follow the particles to your grave.',
        'tooltip.barelygravestone.obituary.gone': 'The grave has been emptied.',
        'gui.barelygravestone.obituary': 'Obituary',
        'gui.barelygravestone.blank': 'A blank page.',
        'gui.barelygravestone.in_memory': 'In memory of %s',
        'gui.barelygravestone.when': 'When',
        'gui.barelygravestone.when_value': '%s, day %s',
        'gui.barelygravestone.where': 'Where',
        'gui.barelygravestone.where_value': '%s, %s %s %s',
        'gui.barelygravestone.where_hidden': '%s (coordinates hidden)',
        'gui.barelygravestone.xp': 'Experience',
        'gui.barelygravestone.xp_cost': 'The grave cost %s levels.',
        'gui.barelygravestone.xp_kept': 'Kept in the grave: %s points.',
        'gui.barelygravestone.xp_taken': 'The grave gave its experience back.',
        'gui.barelygravestone.contents': 'Still in the grave',
        'gui.barelygravestone.count': '%s stacks left',
        'gui.barelygravestone.gone': 'The grave is empty.',
        'message.barelygravestone.not_yours': 'This is the grave of %s. It is not yours to open.',
        'message.barelygravestone.collected': 'You collected everything from the grave.',
        'message.barelygravestone.partial': 'Your inventory is full: %s stacks are still in the grave.',
        'message.barelygravestone.obituary_given': 'You received %s Obituary. Hold it to follow the particles to your grave.',
        'command.barelygravestone.none': 'There are no graves.',
        'command.barelygravestone.entry': '[%s] %s in %s at %s %s %s (%s min ago)',
        'command.barelygravestone.no_such_grave': 'No grave starts with "%s".',
        'command.barelygravestone.ambiguous': 'More than one grave starts with "%s"; type more of the id.',
        'command.barelygravestone.no_dimension': 'The dimension %s is not loaded.',
        'command.barelygravestone.teleported': 'Teleported to grave %s.',
        'command.barelygravestone.removed': 'Removed grave %s; its items were dropped there.',
        'command.barelygravestone.restored': 'Gave the contents of grave %s to %s.',
        'command.barelygravestone.obituary': 'Gave an Obituary for grave %s to %s.',
        'command.barelygravestone.not_loaded': 'Grave %s is not there any more.',
    }, 'lang', 'en_us.json')
    print('assets written to', os.path.normpath(ASSETS))


if __name__ == '__main__':
    main()
