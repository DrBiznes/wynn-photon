"""Spell survey tools: read Wynncraft's server pack offline and find spell models.

Wynncraft's spells are rigs of small oak_boat models (custom_model_data) with hashed texture names that change every
release. This maps every oak_boat model to its textures and their pixel hashes (the same CRC-32 the mod's spell table
and debug log use), finds rigs that are recoloured twins of each other (a base class's spell and its reskin), and draws
contact sheets and glow masks. See docs/research/wynn-spells.md, "The survey".

Needs Python 3 with Pillow and numpy. Every command takes the pack as an extracted folder (see "extract").

  survey.py extract <cached pack zip> <out folder>
      Extracts the obfuscated pack through its central directory (its local headers are zeroed). The game caches the
      pack in <instance>/downloads/<uuid>/<sha1>.
  survey.py map <pack folder> <out.json> [item]
      Maps every custom_model_data threshold of the item (oak_boat) to its model, element count, a geometry signature
      and its textures with their pixel hashes. Takes a few minutes.
  survey.py twins <map.json> <out.json> [<old map.json>]
      Groups textures whose models have the same geometry: the same rig, recoloured. With the map of an older pack,
      marks the textures that are new in this one (pixel hashes that weren't there).
  survey.py find <map.json> <hash or cmd> ...
      Prints the models that use a pixel hash, or the model at a custom_model_data.
  survey.py sheet <pack folder> <map.json> <from cmd> <to cmd> <out.png>
      A contact sheet of the textures of a custom_model_data range, one tile per run of models sharing a texture.
  survey.py mask <pack folder> <texture> <sat from> <sat to> <light from> <light to> <out.png>
      The texture beside the glow a spell kind's cut gives it (white glows), as in wynn_spells.glsl. <texture> is a
      texture path like item/w123 or wynn/spell/meteor_0_a.
"""
import collections
import colorsys
import hashlib
import io
import json
import os
import re
import struct
import sys
import zlib


def extract(src, out):
    data = open(src, 'rb').read()
    eocd = data.rfind(b'PK\x05\x06')
    cd_off = struct.unpack_from('<I', data, eocd + 16)[0]
    p, count = cd_off, 0
    while data[p:p + 4] == b'PK\x01\x02':
        method = struct.unpack_from('<H', data, p + 10)[0]
        csize = struct.unpack_from('<I', data, p + 20)[0]
        nlen, elen, clen = struct.unpack_from('<HHH', data, p + 28)
        off = struct.unpack_from('<I', data, p + 42)[0]
        name = data[p + 46:p + 46 + nlen].decode('utf-8', 'replace')
        p += 46 + nlen + elen + clen
        if name.endswith('/') or '..' in name.split('/'):
            continue
        raw = data[off + 30:off + 30 + csize]
        content = zlib.decompress(raw, -15) if method == 8 else raw
        dst = os.path.join(out, *name.split('/'))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'wb') as f:
            f.write(content)
        count += 1
    print('extracted', count, 'files')


def load_png(path):
    """RGBA image. The pack's PNGs have broken zlib checksums: if Pillow refuses one, rebuild it."""
    from PIL import Image
    try:
        return Image.open(path).convert('RGBA')
    except Exception:
        data = open(path, 'rb').read()

        def chunk(t, d):
            return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)

        p, idat, png = 8, b'', b'\x89PNG\r\n\x1a\n'
        while p < len(data):
            ln, typ = struct.unpack_from('>I4s', data, p)
            body = data[p + 8:p + 8 + ln]
            if typ == b'IDAT':
                idat += body
            elif typ in (b'IHDR', b'PLTE', b'tRNS'):
                png += chunk(typ, body)
            p += 12 + ln
        raw = zlib.decompressobj(-15).decompress(idat[2:])
        png += chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
        return Image.open(io.BytesIO(png)).convert('RGBA')


def texture_file(pack, name):
    ns, path = name.split(':', 1) if ':' in name else ('minecraft', name)
    return os.path.join(pack, 'assets', ns, 'textures', *path.split('/')) + '.png'


def pixel_hash(image):
    """The mod's SpellTags.pixelHash: CRC-32 of every pixel, row by row, as big-endian ARGB (all frames)."""
    rgba = image.tobytes()
    argb = bytearray(len(rgba))
    argb[0::4], argb[1::4], argb[2::4], argb[3::4] = rgba[3::4], rgba[0::4], rgba[1::4], rgba[2::4]
    return '%08x' % (zlib.crc32(bytes(argb)) & 0xffffffff)


def map_item(pack, out, item='oak_boat'):
    mc = os.path.join(pack, 'assets', 'minecraft')
    tree = json.load(open(os.path.join(mc, 'items', item + '.json'), encoding='utf-8'))
    entries = []

    def walk(node):
        if isinstance(node, dict):
            if node.get('type') in ('minecraft:range_dispatch', 'range_dispatch') and 'entries' in node:
                for e in node['entries']:
                    m = e['model']
                    if m.get('type') in ('model', 'minecraft:model'):
                        entries.append((e['threshold'], m['model']))
                    else:
                        walk(m)
            else:
                for v in node.values():
                    walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(tree)
    textures, models = {}, {}

    def texture(name):
        if name not in textures:
            f = texture_file(pack, name)
            if not os.path.exists(f):
                textures[name] = None
            else:
                img = load_png(f)
                textures[name] = {'size': list(img.size), 'hash': pixel_hash(img),
                                  'animated': os.path.exists(f + '.mcmeta')}
        return textures[name]

    def model(name):
        if name not in models:
            ns, path = name.split(':', 1) if ':' in name else ('minecraft', name)
            f = os.path.join(pack, 'assets', ns, 'models', *path.split('/')) + '.json'
            m = json.load(open(f, encoding='utf-8')) if os.path.exists(f) else {}
            tex = dict(model(m['parent'])[0]) if 'parent' in m else {}
            tex.update(m.get('textures', {}))
            els = m.get('elements', [])
            # Texture slot names (#0, #1) differ between twins: leave them out of the signature
            geom = hashlib.md5(re.sub(r'"#[^"]*"', '"#T"', json.dumps(els, sort_keys=True)).encode()).hexdigest()[:10]
            models[name] = (tex, len(els), geom)
        return models[name]

    result = []
    for threshold, name in entries:
        tex, elements, geom = model(name)
        texs = {}
        for key, value in tex.items():
            if key != 'particle' and isinstance(value, str) and not value.startswith('#'):
                info = texture(value)
                if info:
                    texs[key] = {'name': value, **info}
        result.append({'cmd': threshold, 'model': name, 'elements': elements, 'geom': geom, 'textures': texs})
    json.dump(result, open(out, 'w', encoding='utf-8', newline='\n'))
    print(len(result), 'models,', len(textures), 'textures')


def twins(map_file, out, old_file=None):
    entries = json.load(open(map_file, encoding='utf-8'))
    old = set()
    if old_file:
        for e in json.load(open(old_file, encoding='utf-8')):
            old.update(t['hash'] for t in e['textures'].values())
    by_texture, info = collections.defaultdict(list), {}
    for e in entries:
        if e['cmd'] >= 65536:  # Higher bits: tinted variants of the low models
            continue
        for t in e['textures'].values():
            by_texture[t['hash']].append(e)
            info[t['hash']] = t
    by_geometry = collections.defaultdict(list)
    for h, es in by_texture.items():
        by_geometry[tuple(sorted(e['geom'] for e in es))].append(h)
    groups = []
    for hashes in by_geometry.values():
        if len(hashes) < 2 or (old_file and all(h in old for h in hashes)):
            continue
        hashes.sort(key=lambda h: min(e['cmd'] for e in by_texture[h]))
        groups.append([{'hash': h, 'name': info[h]['name'], 'size': info[h]['size'], 'animated': info[h]['animated'],
                        'new': bool(old_file) and h not in old,
                        'cmds': sorted(e['cmd'] for e in by_texture[h])} for h in hashes])
    groups.sort(key=lambda g: g[0]['cmds'][0])
    json.dump(groups, open(out, 'w', encoding='utf-8', newline='\n'), indent=1)
    for i, g in enumerate(groups):
        print(f'G{i}: ' + ' | '.join(f"{'*' if t['new'] else ''}{t['hash']} {t['cmds'][0]}-{t['cmds'][-1]} "
                                     f"n{len(t['cmds'])}" for t in g))


def find(map_file, keys):
    entries = json.load(open(map_file, encoding='utf-8'))
    for key in keys:
        for e in entries:
            hashes = [t['hash'] for t in e['textures'].values()]
            if key in hashes or key == str(e['cmd']):
                print(e['cmd'], e['model'], e['elements'], 'elements',
                      ', '.join(f"{t['hash']} {t['name']} {t['size'][0]}x{t['size'][1]}"
                                f"{' animated' if t['animated'] else ''}" for t in e['textures'].values()))


def first_frame(pack, name):
    f = texture_file(pack, name)
    img = load_png(f)
    w, h = img.size
    if os.path.exists(f + '.mcmeta') and h > w:
        img = img.crop((0, 0, w, w))
    box = img.getbbox()
    return img.crop(box) if box else img


def sheet(pack, map_file, lo, hi, out, tile=120, cols=10):
    from PIL import Image, ImageDraw
    runs = []
    for e in json.load(open(map_file, encoding='utf-8')):
        if not lo <= e['cmd'] <= hi:
            continue
        key = tuple(sorted(t['name'] for t in e['textures'].values()))
        if runs and runs[-1][0] == key and runs[-1][2] == e['cmd'] - 1:
            runs[-1][2] = e['cmd']
        else:
            runs.append([key, e['cmd'], e['cmd']])
    rows = (len(runs) + cols - 1) // cols
    img = Image.new('RGB', (cols * tile, rows * (tile + 14)), (30, 30, 30))
    draw = ImageDraw.Draw(img)
    for i, (key, a, b) in enumerate(runs):
        x, y = (i % cols) * tile, (i // cols) * (tile + 14)
        for j, name in enumerate(key[:2]):
            t = first_frame(pack, name)
            size = tile // len(key[:2]) - 2
            scale = size / max(t.size)
            t = t.resize((max(1, int(t.size[0] * scale)), max(1, int(t.size[1] * scale))), Image.NEAREST)
            bg = Image.new('RGB', t.size, (60, 60, 60))
            bg.paste(t, (0, 0), t)
            img.paste(bg, (x + 1 + j * (tile // 2), y + 1))
        draw.text((x + 2, y + tile), f'{a}-{b}', fill=(255, 255, 0))
    img.save(out)
    print(len(runs), 'runs')


def mask(pack, name, s0, s1, l0, l1, out):
    import numpy as np
    from PIL import Image
    t = first_frame(pack, name)
    a = np.asarray(t) / 255.0
    mx, mn = a[..., :3].max(-1), a[..., :3].min(-1)
    light = (mx + mn) / 2
    sat = np.where(mx == mn, 0, (mx - mn) / (1 - np.abs(2 * light - 1) + 1e-9))

    def step(e0, e1, x):
        return np.clip((x - e0) / (e1 - e0), 0, 1) if e1 > e0 else (x >= e0).astype(float)

    glow = step(s0, s1, sat) * step(l0, l1, light)
    opaque = a[..., 3:4] > 0.5
    right = Image.fromarray((np.stack([glow] * 3, -1) * 255 * opaque + (~opaque) * 60).astype('uint8'))
    left = Image.new('RGB', t.size, (60, 60, 60))
    left.paste(t, (0, 0), t)
    w, h = t.size
    both = Image.new('RGB', (2 * w + 2, h), (255, 0, 0))
    both.paste(left, (0, 0))
    both.paste(right, (w + 2, 0))
    scale = max(1, 384 // max(w, h))
    both.resize((both.size[0] * scale, h * scale), Image.NEAREST).save(out)


def main(args):
    if not args:
        print(__doc__)
        return
    command, rest = args[0], args[1:]
    if command == 'extract':
        extract(*rest)
    elif command == 'map':
        map_item(*rest)
    elif command == 'twins':
        twins(*rest)
    elif command == 'find':
        find(rest[0], rest[1:])
    elif command == 'sheet':
        sheet(rest[0], rest[1], int(rest[2]), int(rest[3]), rest[4])
    elif command == 'mask':
        mask(rest[0], rest[1], *map(float, rest[2:6]), rest[6])
    else:
        print(__doc__)


if __name__ == '__main__':
    main(sys.argv[1:])
