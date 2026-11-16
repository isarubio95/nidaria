# Junta lo que hornea build_room.py en los archivos que carga el juego:
#
#   python3 art/room/pack_lightmaps.py <horneado> <public/rooms> [--floor-texture <diff.jpg> --floor-ao <ao.jpg>]
#
# Cada mapa de luz es un WebP sin pérdida (no mezcla canales) con uno por fuente: rojo = sol, verde =
# cielo, azul = lámpara. Se guarda la luminancia de la luz que llega (sin
# color: el juego la tiñe según la hora) codificada como
# v = (L / max) ^ (1 / 2.2), con el máximo de cada canal en room.json.
#
# Con --floor-texture también prepara la textura de la tarima (CC0 de Poly
# Haven: laminate_floor_02), con su oclusión ya multiplicada.

import json
import os
import shutil
import sys

import numpy as np
from PIL import Image

GAMMA = 2.2
CHANNELS = ['sun', 'sky', 'lamp']
TARGETS = ['floor', 'wall', 'atlas', 'trims', 'shell']


def blur(a, sigma):
    """Desenfoque gaussiano separable (sin scipy)."""
    r = max(1, int(sigma * 3))
    x = np.arange(-r, r + 1)
    k = np.exp(-(x ** 2) / (2 * sigma ** 2))
    k /= k.sum()
    pad = np.pad(a, r, mode='edge')
    rows = sum(k[i] * pad[:, i:i + a.shape[1]] for i in range(2 * r + 1))
    return sum(k[i] * rows[i:i + a.shape[0], :] for i in range(2 * r + 1))


def main(src, dst, extra):
    os.makedirs(dst, exist_ok=True)
    bake = json.load(open(os.path.join(src, 'room-bake.json')))
    out = {'version': 1, 'gamma': GAMMA, 'maps': {}, 'floor': bake['floor'], 'wall': bake['wall']}
    for target in TARGETS:
        planes = []
        maxima = []
        for channel in CHANNELS:
            rgb = np.load(os.path.join(src, f'{target}_{channel}.npy'))
            lum = rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722
            lum = np.flipud(np.maximum(lum, 0))  # Blender guarda de abajo arriba
            # Un poco de suavizado se lleva el grano del horneado sin borrar
            # la sombra de la ventana.
            lum = blur(lum, 0.8)
            top = float(np.percentile(lum, 99.95)) * 1.05 or 1.0
            maxima.append(round(top, 5))
            planes.append(np.clip(lum / top, 0, 1) ** (1 / GAMMA))
        packed = (np.stack(planes, axis=-1) * 255 + 0.5).astype(np.uint8)
        Image.fromarray(packed, 'RGB').save(os.path.join(dst, f'light-{target}.webp'), lossless=True, quality=100, method=6)
        out['maps'][target] = {'file': f'light-{target}.webp', 'max': maxima}
        print(target, packed.shape, 'máximos', maxima)

    # Luz de referencia: el cielo en el suelo, donde está la criatura (x = 0,
    # z = 0). El juego la usa para que el suelo salga con el brillo de siempre.
    f = bake['floor']
    sky = np.flipud(np.load(os.path.join(src, 'floor_sky.npy')).mean(axis=-1))
    h, w = sky.shape
    i = int((0 - f['x0']) / (f['x1'] - f['x0']) * w)
    j = int((0 - f['z0']) / (f['z1'] - f['z0']) * h)
    out['skyAtCreature'] = round(float(sky[j - 4:j + 4, i - 4:i + 4].mean()), 5)
    lamp = np.flipud(np.load(os.path.join(src, 'floor_lamp.npy')).mean(axis=-1))
    out['lampAtCreature'] = round(float(lamp[j - 4:j + 4, i - 4:i + 4].mean()), 5)
    wsky = np.flipud(np.load(os.path.join(src, 'wall_sky.npy')).mean(axis=-1))
    wf = bake['wall']
    wh, ww = wsky.shape
    wi = int((0 - wf['x0']) / (wf['x1'] - wf['x0']) * ww)
    wj = int((1 - (1.3 - wf['y0']) / (wf['y1'] - wf['y0'])) * wh)
    out['skyOnWall'] = round(float(wsky[wj - 4:wj + 4, wi - 4:wi + 4].mean()), 5)
    print('cielo: suelo', out['skyAtCreature'], 'pared', out['skyOnWall'])
    sun = np.flipud(np.load(os.path.join(src, 'floor_sun.npy')).mean(axis=-1))
    out['sunPeak'] = round(float(np.percentile(sun, 99.9)), 5)

    shutil.copy(os.path.join(src, 'room.glb'), os.path.join(dst, 'room.glb'))

    if '--floor-texture' in extra:
        diff = Image.open(extra[extra.index('--floor-texture') + 1]).convert('RGB')
        img = np.asarray(diff).astype(np.float32) / 255
        if '--floor-ao' in extra:
            ao = np.asarray(Image.open(extra[extra.index('--floor-ao') + 1]).convert('L')).astype(np.float32) / 255
            img = img * (0.35 + 0.65 * ao[..., None])
        # Las tablas del original van de lado: se giran para que vayan hacia
        # la cámara, como la tarima de antes.
        img = np.rot90(img)
        # 1024 px por baldosa de 1,7 m sobra para lo que se ve.
        Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)).resize((1024, 1024), Image.LANCZOS).save(
            os.path.join(dst, 'floor-wood.webp'), quality=84, method=6)
        out['floorTexture'] = {'file': 'floor-wood.webp', 'meters': 1.7}

    with open(os.path.join(dst, 'room.json'), 'w') as fh:
        json.dump(out, fh, indent=1)
    print('listo:', dst)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
