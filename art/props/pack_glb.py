# Aligera un .glb de objeto (sin esqueleto) con KHR_mesh_quantization:
# posiciones en enteros de 16 bits, normales en 8 bits y UV en 16 bits, en
# vez de floats de 32. La geometría pasa a ocupar la mitad y además se
# comprime mejor (gzip/zstd en el servidor).
#
#   python3 pack_glb.py entrada.glb salida.glb
#
# La posición se guarda como entero alrededor del centro de la caja con un
# paso igual en los tres ejes; ese paso y el centro van en la matriz del
# nodo (igual en los tres ejes para que las normales no se tuerzan). Three.js
# lo lee de serie; en Android, Glb.parseStatic aplica la matriz del nodo.
# Los vértices se reordenan por su primer uso en los índices, que comprime
# mejor y no cambia nada en pantalla.
#
# Solo numpy: no hace falta Blender ni el entorno de TRELLIS.

import json
import struct
import sys

import numpy as np

FLOAT, SHORT, USHORT, BYTE, UINT, UBYTE = 5126, 5122, 5123, 5120, 5125, 5121
# Bits de verdad dentro de cada entero: lo que sobra va a cero y se
# comprime solo. 14 bits de posición son 0,1 mm en un objeto de 1,5 m; 12
# de UV, 1/8 de téxel en una textura de 512.
POS_BITS = 14
UV_BITS = 12
COMPS = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}
DTYPES = {FLOAT: np.float32, SHORT: np.int16, USHORT: np.uint16, BYTE: np.int8, UINT: np.uint32, UBYTE: np.uint8}


def read_glb(path):
    b = open(path, 'rb').read()
    assert b[:4] == b'glTF'
    n = struct.unpack('<I', b[12:16])[0]
    gltf = json.loads(b[20:20 + n])
    m = struct.unpack('<I', b[20 + n:24 + n])[0]
    return gltf, b[28 + n:28 + n + m]


def accessor(gltf, binary, index):
    acc = gltf['accessors'][index]
    view = gltf['bufferViews'][acc['bufferView']]
    comps = COMPS[acc['type']]
    dtype = np.dtype(DTYPES[acc['componentType']])
    stride = view.get('byteStride', comps * dtype.itemsize)
    start = view.get('byteOffset', 0) + acc.get('byteOffset', 0)
    raw = np.frombuffer(binary, np.uint8, count=stride * (acc['count'] - 1) + comps * dtype.itemsize, offset=start)
    rows = np.lib.stride_tricks.as_strided(raw, (acc['count'], comps * dtype.itemsize), (stride, 1))
    out = np.ascontiguousarray(rows).view(dtype).reshape(acc['count'], comps)
    assert not acc.get('normalized'), 'ya está cuantizado'
    return out


def trs(node):
    """Matriz 4×4 (por columnas, como glTF) de un nodo."""
    if 'matrix' in node:
        return np.array(node['matrix'], float).reshape(4, 4).T
    x, y, z, w = node.get('rotation', [0, 0, 0, 1])
    r = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y)],
        [2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x)],
        [2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)],
    ])
    m = np.eye(4)
    m[:3, :3] = r * np.array(node.get('scale', [1, 1, 1]))
    m[:3, 3] = node.get('translation', [0, 0, 0])
    return m


def main():
    src, dst = sys.argv[1], sys.argv[2]
    gltf, binary = read_glb(src)
    chunks = []  # (bytes, bufferView sin offset)

    def add_view(data, stride=None, target=None):
        view = {'buffer': 0, 'byteLength': len(data)}
        if stride:
            view['byteStride'] = stride
        if target:
            view['target'] = target
        chunks.append((data, view))
        return len(chunks) - 1

    accessors = []

    def add_accessor(view, ctype, count, kind, normalized=False, lo=None, hi=None):
        acc = {'bufferView': view, 'componentType': ctype, 'count': count, 'type': kind}
        if normalized:
            acc['normalized'] = True
        if lo is not None:
            acc['min'], acc['max'] = [int(v) for v in lo], [int(v) for v in hi]
        accessors.append(acc)
        return len(accessors) - 1

    mesh_nodes = {}
    for i, node in enumerate(gltf['nodes']):
        if 'mesh' in node:
            assert 'skin' not in node, 'con esqueleto no: los huesos leen las posiciones tal cual'
            assert node['mesh'] not in mesh_nodes, 'una malla usada por dos nodos'
            mesh_nodes[node['mesh']] = i

    for mi, mesh in enumerate(gltf['meshes']):
        prims = mesh['primitives']
        positions = [accessor(gltf, binary, p['attributes']['POSITION']).astype(np.float64) for p in prims]
        allp = np.concatenate(positions)
        lo, hi = allp.min(0), allp.max(0)
        center = (lo + hi) / 2
        top = (1 << (POS_BITS - 1)) - 1
        step = max(float((hi - lo).max()) / 2 / top, 1e-9)
        for p, pos in zip(prims, positions):
            idx = accessor(gltf, binary, p['indices']).reshape(-1).astype(np.int64)
            n = len(pos)
            # Orden de primer uso.
            first = np.full(n, len(idx), np.int64)
            np.minimum.at(first, idx, np.arange(len(idx)))
            order = np.argsort(first, kind='stable')
            remap = np.empty(n, np.int64)
            remap[order] = np.arange(n)

            q = np.clip(np.round((pos[order] - center) / step), -top, top).astype(np.int16)
            data = np.zeros((n, 4), np.int16)
            data[:, :3] = q
            attrs = {'POSITION': add_accessor(add_view(data.tobytes(), 8, 34962), SHORT, n, 'VEC3',
                                              lo=q.min(0), hi=q.max(0))}
            for key, a in p['attributes'].items():
                if key == 'POSITION':
                    continue
                v = accessor(gltf, binary, a)[order]
                if key == 'NORMAL':
                    v = v / np.maximum(np.linalg.norm(v, axis=1, keepdims=True), 1e-9)
                    d = np.zeros((n, 4), np.int8)
                    d[:, :3] = np.round(v * 127)
                    attrs[key] = add_accessor(add_view(d.tobytes(), 4, 34962), BYTE, n, 'VEC3', normalized=True)
                elif key.startswith('TEXCOORD') and v.min() >= 0 and v.max() <= 1:
                    uv_top = (1 << UV_BITS) - 1
                    d = (np.round(v * uv_top) * 65535 / uv_top).round().astype(np.uint16)
                    attrs[key] = add_accessor(add_view(d.tobytes(), 4, 34962), USHORT, n, 'VEC2', normalized=True)
                else:  # lo que no se sabe cuantizar sin perder, tal cual
                    d = np.ascontiguousarray(v)
                    kind = gltf['accessors'][a]['type']
                    ctype = gltf['accessors'][a]['componentType']
                    stride = d.dtype.itemsize * d.shape[1]
                    attrs[key] = add_accessor(add_view(d.tobytes(), stride if stride % 4 == 0 else None, 34962),
                                              ctype, n, kind)
            new_idx = remap[idx]
            itype = USHORT if n < 65536 else UINT
            p['indices'] = add_accessor(add_view(new_idx.astype(DTYPES[itype]).tobytes(), target=34963),
                                        itype, len(new_idx), 'SCALAR')
            p['attributes'] = attrs

        # El paso y el centro, en el nodo: M · T(centro) · S(paso).
        node = gltf['nodes'][mesh_nodes[mi]]
        m = trs(node)
        m3 = m[:3, :3]
        scale = np.linalg.norm(m3, axis=0)
        rot = m3 / scale
        assert np.allclose(rot @ rot.T, np.eye(3), atol=1e-5), 'el nodo tiene cizalla'
        t = m[:3, 3] + m3 @ center
        node.pop('matrix', None)
        node['translation'] = [float(v) for v in t]
        node['scale'] = [float(v * step) for v in scale]
        if not np.allclose(rot, np.eye(3)):
            from math import sqrt
            w = sqrt(max(0.0, 1 + rot[0, 0] + rot[1, 1] + rot[2, 2])) / 2
            node['rotation'] = [float((rot[2, 1] - rot[1, 2]) / (4 * w)), float((rot[0, 2] - rot[2, 0]) / (4 * w)),
                                float((rot[1, 0] - rot[0, 1]) / (4 * w)), float(w)]

    # Las imágenes, tal cual.
    for img in gltf.get('images', []):
        view = gltf['bufferViews'][img['bufferView']]
        start = view.get('byteOffset', 0)
        img['bufferView'] = add_view(binary[start:start + view['byteLength']])

    out = bytearray()
    views = []
    for data, view in chunks:
        while len(out) % 4:
            out.append(0)
        view['byteOffset'] = len(out)
        out += data
        views.append(view)
    while len(out) % 4:
        out.append(0)
    gltf['bufferViews'] = views
    gltf['accessors'] = accessors
    gltf['buffers'] = [{'byteLength': len(out)}]
    for key in ('extensionsUsed', 'extensionsRequired'):
        gltf[key] = sorted(set(gltf.get(key, [])) | {'KHR_mesh_quantization'})

    text = json.dumps(gltf, separators=(',', ':')).encode()
    text += b' ' * (-len(text) % 4)
    glb = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(text) + 8 + len(out))
    glb += struct.pack('<II', len(text), 0x4E4F534A) + text
    glb += struct.pack('<II', len(out), 0x004E4942) + bytes(out)
    open(dst, 'wb').write(glb)
    before = len(open(src, 'rb').read())
    print(f'OK {dst}: {before // 1024} KB -> {len(glb) // 1024} KB, paso {step * 1000:.3f} mm')


if __name__ == '__main__':
    main()
