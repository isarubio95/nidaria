# La habitación horneada: construye en Blender la habitación fija (pared
# con la ventana en arco, friso, suelo, cortinas, estantería, puf y cuadro),
# hornea con Cycles la luz que recibe cada superficie y la exporta para la
# web (public/rooms/).
#
#   blender -b -P art/room/build_room.py -- --out <carpeta> [--samples 512] [--preview]
#
# La luz se hornea por separado para tres fuentes, cada una en su canal:
#   sol    el sol que entra por la ventana,
#   cielo  la luz del día que entra por la ventana y por el frente abierto,
#   lampara  una lámpara cálida dentro, para la noche.
# Se guarda solo la luz (sin el color de la superficie): el juego la
# multiplica por el papel pintado que toque y mezcla los tres canales según
# la hora y el tiempo. Después, pack_lightmaps.py junta los canales en PNG.
#
# Las medidas son las de public/js/scene/house.js. En este archivo las
# coordenadas se escriben como en Three.js (y hacia arriba, z hacia la
# cámara) y T() las pasa a Blender (z hacia arriba).

import bpy
import bmesh
import json
import math
import os
import sys
import numpy as np
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []


def arg(name, default=None):
    if name in argv:
        i = argv.index(name)
        return argv[i + 1] if i + 1 < len(argv) and not argv[i + 1].startswith('--') else True
    return default


OUT = os.path.abspath(arg('--out', 'out'))
SAMPLES = int(arg('--samples', 512))
PREVIEW = bool(arg('--preview', False))
os.makedirs(OUT, exist_ok=True)

# ── Medidas (las de house.js) ──
WALL_Z = -2.4
WAINSCOT = 0.62
WIN = dict(x=-0.68, y=0.98, width=0.86, straight=0.62, depth=0.14)
ROOM_X = 5.0          # la habitación va de -5 a 5 en x
FRONT_Z = 7.0         # el suelo llega hasta aquí (detrás de la cámara)
CEIL = 3.2
SHELF_AT = (1.9, 1.5, WALL_Z + 0.13)
POUF_AT = (-1.75, 0.0, -1.25)
PAINTING_AT = (1.02, 1.36, WALL_Z + 0.02)

# Mapas de luz: tamaño en texeles.
SIZES = {'floor': (1024, 1024), 'wall': (1024, 512), 'atlas': (1024, 1024), 'trims': (2048, 64), 'shell': (512, 512)}


def T(x, y, z):
    return Vector((x, -z, y))


def srgb(hexcolor):
    h = hexcolor.lstrip('#')
    c = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    return tuple(v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in c) + (1.0,)


# ── Escena vacía ──
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
cprefs = bpy.context.preferences.addons['cycles'].preferences
for kind in ('OPTIX', 'CUDA'):
    try:
        cprefs.compute_device_type = kind
        cprefs.get_devices()
        devices = [d for d in cprefs.devices if d.type == kind]
        if devices:
            for d in cprefs.devices:
                d.use = d.type == kind
            scene.cycles.device = 'GPU'
            print('Cycles en', kind, [d.name for d in devices])
            break
    except Exception as error:  # noqa: BLE001
        print('sin', kind, error)
scene.cycles.samples = SAMPLES
scene.cycles.max_bounces = 6
scene.cycles.diffuse_bounces = 4
scene.cycles.glossy_bounces = 2
scene.cycles.transmission_bounces = 2
scene.cycles.use_denoising = True

world = bpy.data.worlds.new('mundo')
scene.world = world
world.use_nodes = True
background = world.node_tree.nodes['Background']
background.inputs['Color'].default_value = (1, 1, 1, 1)

# ── Materiales ──
COLORS = {
    'wallpaper': '#93aabd', 'wainscot': '#f4ede2', 'floor': '#a5815c', 'ceiling': '#f6f3ee',
    'trim': '#fffaf2', 'reveal': '#f7eedf', 'wood_light': '#e8bf8c', 'wood_bracket': '#d9a874',
    'brass': '#e2b279', 'curtain': '#f3ece0', 'tie': '#c65a37',
    'book_red': '#c65a37', 'book_blue': '#3f6fb0', 'book_yellow': '#e0a93b', 'book_green': '#3f9a7a',
    'book_band': '#fffaf2', 'pot': '#fbf7f0', 'pot_rim': '#ffc23d', 'soil': '#6e4a33',
    'leaf': '#4f9f72', 'leaf2': '#6dbb87', 'ball': '#ff6a3d',
    'pouf': '#c8623d', 'pouf_button': '#a24a2c', 'painting_art': '#cfd9c8',
}
ROUGH = {'trim': 0.35, 'brass': 0.3, 'ball': 0.25, 'pot': 0.3}
MATS = {}
for name, hexcolor in COLORS.items():
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = srgb(hexcolor)
    bsdf.inputs['Roughness'].default_value = ROUGH.get(name, 0.8)
    MATS[name] = m


def link(obj, coll=None):
    (coll or scene.collection).objects.link(obj)
    return obj


def mesh_object(name, bm, mat):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    obj = bpy.data.objects.new(name, me)
    if mat:
        me.materials.append(MATS[mat])
    return link(obj)


def smooth(obj, angle=40):
    me = obj.data
    for p in me.polygons:
        p.use_smooth = True
    me.set_sharp_from_angle(angle=math.radians(angle))


def box(name, size, center, mat, bevel=0.0, rot=None):
    """Caja redondeada: tamaño y centro como en Three.js (ancho, alto, fondo)."""
    w, h, d = size
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=Vector((w, d, h)), verts=bm.verts)
    obj = mesh_object(name, bm, mat)
    obj.location = T(*center)
    if rot is not None:
        obj.rotation_euler = rot
    if bevel > 0:
        mod = obj.modifiers.new('bisel', 'BEVEL')
        mod.width = min(bevel, min(size) * 0.49)
        mod.segments = 3
        mod.limit_method = 'NONE'
    smooth(obj)
    return obj


def cylinder(name, radius, depth, center, mat, axis='y', segments=32, bevel=0.0):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segments, radius1=radius, radius2=radius, depth=depth)
    obj = mesh_object(name, bm, mat)
    obj.location = T(*center)
    # El cilindro de bmesh va a lo largo de z de Blender (y de Three.js).
    if axis == 'x':
        obj.rotation_euler = (0, math.pi / 2, 0)
    elif axis == 'z':
        obj.rotation_euler = (math.pi / 2, 0, 0)
    if bevel > 0:
        mod = obj.modifiers.new('bisel', 'BEVEL')
        mod.width = bevel
        mod.segments = 6
        mod.limit_method = 'ANGLE'
    smooth(obj, 50)
    return obj


def sphere(name, radius, center, mat, scale=(1, 1, 1), segments=24):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segments, v_segments=max(6, segments * 2 // 3), radius=radius)
    obj = mesh_object(name, bm, mat)
    obj.location = T(*center)
    obj.scale = (scale[0], scale[2], scale[1])
    smooth(obj, 180)
    return obj


def arch_points(width, straight, cx=0.0, cy=0.0, n=28):
    """Contorno del arco (como archShape de house.js), de abajo a la izquierda
    a abajo a la derecha pasando por arriba."""
    r = width / 2
    pts = [(cx - r, cy), (cx - r, cy + straight)]
    for i in range(1, n):
        a = math.pi - math.pi * i / n
        pts.append((cx + r * math.cos(a), cy + straight + r * math.sin(a)))
    pts += [(cx + r, cy + straight), (cx + r, cy)]
    return pts


def flat_shape(name, loops, mat, depth, bevel=0.0, at=(0, 0, 0)):
    """Figura plana en el plano de la pared (x, y de Three.js) con grosor
    hacia la cámara. Cada lazo es una lista de puntos; los de dentro son
    agujeros."""
    cu = bpy.data.curves.new(name, 'CURVE')
    cu.dimensions = '2D'
    cu.fill_mode = 'BOTH' if depth > 0 else 'FRONT'
    cu.extrude = depth / 2
    cu.bevel_depth = bevel
    cu.bevel_resolution = 3
    for loop in loops:
        sp = cu.splines.new('POLY')
        sp.points.add(len(loop) - 1)
        for p, (x, y) in zip(sp.points, loop):
            p.co = (x, y, 0, 1)
        sp.use_cyclic_u = True
    obj = bpy.data.objects.new(name, cu)
    link(obj)
    cu.materials.append(MATS[mat])
    obj.location = T(*at)
    obj.rotation_euler = (math.pi / 2, 0, 0)
    return obj


# ── Suelo ──
def build_floor():
    bm = bmesh.new()
    nx, nz = 20, 18
    verts = []
    for j in range(nz + 1):
        row = []
        for i in range(nx + 1):
            x = -ROOM_X + 2 * ROOM_X * i / nx
            z = WALL_Z + (FRONT_Z - WALL_Z) * j / nz
            row.append(bm.verts.new(T(x, 0, z)))
        verts.append(row)
    for j in range(nz):
        for i in range(nx):
            bm.faces.new((verts[j][i], verts[j + 1][i], verts[j + 1][i + 1], verts[j][i + 1]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    obj = mesh_object('floor', bm, 'floor')
    if obj.data.polygons[0].normal.z < 0:
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        bmesh.ops.reverse_faces(bm, faces=bm.faces)
        bm.to_mesh(obj.data)
        bm.free()
    return obj


# ── Pared con el hueco de la ventana y el friso ──
def build_wall():
    hole = arch_points(WIN['width'], WIN['straight'], WIN['x'], WIN['y'], n=32)
    # Papel pintado: de WAINSCOT al techo, con el hueco.
    paper = flat_shape('wall_paper', [
        [(-ROOM_X, WAINSCOT), (ROOM_X, WAINSCOT), (ROOM_X, CEIL), (-ROOM_X, CEIL)],
        hole,
    ], 'wallpaper', 0.0, at=(0, 0, WALL_Z))
    # Friso: un panel un poco por delante de la pared.
    panel = flat_shape('wall_wainscot', [
        [(-ROOM_X, 0), (ROOM_X, 0), (ROOM_X, WAINSCOT + 0.005), (-ROOM_X, WAINSCOT + 0.005)],
    ], 'wainscot', 0.0, at=(0, 0, WALL_Z + 0.012))
    objs = to_meshes([paper, panel])
    wall = join(objs, 'wall')
    # La cara del papel tiene que mirar a la cámara (+z de Three.js = -y de Blender).
    bm = bmesh.new()
    bm.from_mesh(wall.data)
    flip = [f for f in bm.faces if f.normal.y > 0]
    bmesh.ops.reverse_faces(bm, faces=flip)
    bm.to_mesh(wall.data)
    bm.free()
    return wall


def build_reveal():
    """El grosor del muro alrededor del hueco (la mocheta) y su repisa."""
    hole = arch_points(WIN['width'], WIN['straight'], WIN['x'], WIN['y'], n=32)
    bm = bmesh.new()
    front = [bm.verts.new(T(x, y, WALL_Z)) for x, y in hole]
    back = [bm.verts.new(T(x, y, WALL_Z - WIN['depth'])) for x, y in hole]
    n = len(hole)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((front[i], back[i], back[j], front[j]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    obj = mesh_object('reveal', bm, 'reveal')
    # Las normales tienen que mirar hacia dentro del hueco.
    cx, cy = WIN['x'], WIN['y'] + WIN['straight'] * 0.6
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    wrong = []
    for f in bm.faces:
        c = f.calc_center_median()
        to_center = Vector((cx - c.x, 0, cy - c.z))
        if f.normal.dot(to_center) < 0:
            wrong.append(f)
    bmesh.ops.reverse_faces(bm, faces=wrong)
    bm.to_mesh(obj.data)
    bm.free()
    return obj


def build_window():
    x0, y0 = WIN['x'], WIN['y']
    w, s, d = WIN['width'], WIN['straight'], WIN['depth']
    r = w / 2
    parts = [build_reveal()]
    # Marco de la hoja (anillo cerrado) metido en el hueco.
    sash = flat_shape('sash', [arch_points(w, s), arch_points(w - 0.07, s - 0.035, 0, 0.035)], 'trim', 0.03, 0.006,
                      at=(x0, y0, WALL_Z - d * 0.55))
    # Moldura sobre la pared: una sola figura en forma de ∩.
    outer = arch_points(w + 0.2, s)
    inner = arch_points(w, s)
    casing = flat_shape('casing', [outer + inner[::-1]], 'trim', 0.022, 0.01, at=(x0, y0, WALL_Z + 0.013))
    parts += [sash, casing]
    parts.append(box('bar_v', (0.03, s + r, 0.03), (x0, y0 + (s + r) / 2, WALL_Z - d * 0.5), 'trim', 0.01))
    parts.append(box('bar_h', (w - 0.04, 0.03, 0.03), (x0, y0 + s, WALL_Z - d * 0.5), 'trim', 0.01))
    parts.append(box('sill', (w + 0.36, 0.055, 0.2), (x0, y0 - 0.02, WALL_Z + 0.04), 'trim', 0.018))
    parts.append(box('apron', (w + 0.2, 0.08, 0.03), (x0, y0 - 0.085, WALL_Z + 0.015), 'trim', 0.01))
    # Barra, remates y cortinas.
    rod_y = y0 + s + r + 0.17
    parts.append(cylinder('rod', 0.014, w + 0.62, (x0, rod_y, WALL_Z + 0.09), 'brass', axis='x', segments=20))
    for side in (-1, 1):
        parts.append(sphere(f'finial_{side}', 0.032, (x0 + side * (r + 0.33), rod_y, WALL_Z + 0.09), 'brass', segments=16))
    curtain_h = rod_y - y0 + 0.14
    for side in (-1, 1):
        parts += build_curtain(0.4, curtain_h, side, (x0 + side * (r + 0.13), rod_y - curtain_h / 2 + 0.01, WALL_Z + 0.07))
    return parts


def build_curtain(width, height, side, center):
    """La cortina recogida con su alzapaño (la misma cuenta que curtain() de
    house.js)."""
    nx, ny = 32, 40
    outer = side * width / 2
    tie_y = -height / 2 + height * 0.36
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new('UVMap')
    grid = []
    for j in range(ny + 1):
        row = []
        for i in range(nx + 1):
            u, v = i / nx, j / ny
            x = -width / 2 + width * u
            y = -height / 2 + height * v
            pinch = 1 - 0.62 * math.exp(-(((y - tie_y) / (height * 0.2)) ** 2))
            below = 1 + 0.25 * min(1, (tie_y - y) / (height * 0.3)) if y < tie_y else 1
            nxp = outer + (x - outer) * pinch * below
            folds = math.sin(u * math.pi * 9) * (0.022 + 0.012 * (1 - pinch)) + math.sin(u * math.pi * 4 + 1) * 0.008
            row.append((bm.verts.new(T(center[0] + nxp, center[1] + y, center[2] + folds + 0.03)), u, v))
        grid.append(row)
    for j in range(ny):
        for i in range(nx):
            quad = (grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i])
            f = bm.faces.new([q[0] for q in quad])
            for loop, q in zip(f.loops, quad):
                loop[uv_layer].uv = (q[1], q[2])
    obj = mesh_object(f'curtain_{side}', bm, 'curtain')
    # Que mire a la cámara.
    if obj.data.polygons[0].normal.y > 0:
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        bmesh.ops.reverse_faces(bm, faces=bm.faces)
        bm.to_mesh(obj.data)
        bm.free()
    mod = obj.modifiers.new('grosor', 'SOLIDIFY')
    mod.thickness = 0.006
    mod.offset = -1
    smooth(obj, 180)
    # Alzapaño: un aro aplastado alrededor del fruncido.
    bm = bmesh.new()
    torus_major, torus_minor = width * 0.2, 0.018
    rings, sides = 28, 10
    verts = []
    for i in range(rings):
        a = 2 * math.pi * i / rings
        row = []
        for k in range(sides):
            b = 2 * math.pi * k / sides
            rr = torus_major + torus_minor * math.cos(b)
            # Aro en el plano horizontal, aplastado hacia la pared (0.42).
            px, pz, py = rr * math.cos(a), rr * math.sin(a) * 0.42, torus_minor * math.sin(b)
            row.append(bm.verts.new(T(px, py, pz)))
        verts.append(row)
    for i in range(rings):
        for k in range(sides):
            a, b = verts[i][k], verts[(i + 1) % rings][k]
            c, d = verts[(i + 1) % rings][(k + 1) % sides], verts[i][(k + 1) % sides]
            bm.faces.new((a, b, c, d))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    tie = mesh_object(f'tie_{side}', bm, 'tie')
    tie.location = T(center[0] + outer - side * width * 0.2, center[1] + tie_y, center[2] + 0.04)
    smooth(tie, 180)
    return [obj, tie]


# ── Zócalo y moldura del friso ──
def build_trims():
    rail = box('rail', (2 * ROOM_X, 0.05, 0.05), (0, WAINSCOT, WALL_Z + 0.025), 'trim', 0.018)
    base = box('baseboard', (2 * ROOM_X, 0.12, 0.04), (0, 0.06, WALL_Z + 0.032), 'trim', 0.012)
    return [rail, base]


# ── Estantería ──
def build_shelf():
    sx, sy, sz = SHELF_AT
    parts = [box('shelf_board', (1.0, 0.045, 0.24), (sx, sy, sz), 'wood_light', 0.014)]
    for side in (-1, 1):
        parts.append(box(f'bracket_{side}', (0.03, 0.16, 0.2), (sx + side * 0.36, sy - 0.1, sz - 0.01), 'wood_bracket', 0.01))
    books = [('book_red', 0.26, 0.07), ('book_blue', 0.3, 0.065), ('book_yellow', 0.22, 0.06), ('book_green', 0.27, 0.07)]
    bx = -0.42
    for i, (mat, tall, thick) in enumerate(books):
        tilt = 0.3 if i == 3 else 0
        cx = sx + bx + thick / 2 + (0.05 if i == 3 else 0)
        cy = sy + 0.0225 + tall / 2 - (0.02 if i == 3 else 0)
        book = box(f'book_{i}', (thick, tall, 0.17), (cx, cy, sz), mat, 0.008, rot=(0, tilt, 0))
        band = box(f'band_{i}', (thick + 0.004, 0.022, 0.172), (cx, cy, sz), 'book_band', 0.004)
        # La banda va a un 28 % del alto, girada con el libro.
        band.parent = book
        band.location = Vector((0, 0, tall * 0.28))
        band.matrix_parent_inverse = Matrix.Identity(4)
        parts += [book, band]
        bx += thick + 0.006
    # Maceta con una planta.
    px, py, pz = sx + 0.28, sy + 0.0225, sz
    parts.append(cylinder('pot', 0.075, 0.13, (px, py + 0.065, pz), 'pot', bevel=0.012))
    parts.append(cylinder('pot_rim', 0.08, 0.03, (px, py + 0.135, pz), 'pot_rim', bevel=0.01))
    parts.append(cylinder('soil', 0.064, 0.01, (px, py + 0.145, pz), 'soil'))
    for i in range(7):
        a = i / 7 * math.pi * 2
        tilt = 0 if i == 0 else 0.7
        length = 0.11 if i == 0 else 0.1
        leaf = build_leaf(f'leaf_{i}', length, 0.026, 'leaf' if i % 2 == 0 else 'leaf2')
        leaf.location = T(px, py + 0.14, pz)
        dir3 = Vector((math.sin(a) * math.sin(tilt), math.cos(tilt), math.cos(a) * math.sin(tilt)))
        d = T(*dir3)
        leaf.rotation_mode = 'QUATERNION'
        leaf.rotation_quaternion = d.to_track_quat('Z', 'Y')
        parts.append(leaf)
    parts.append(sphere('ball', 0.06, (sx + 0.04, sy + 0.0825, sz + 0.02), 'ball'))
    return parts


def build_leaf(name, length, radius, mat):
    """Hoja carnosa: un cono redondeado a lo largo de z de Blender."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=radius, radius2=radius * 0.25, depth=length)
    bmesh.ops.translate(bm, vec=Vector((0, 0, length / 2)), verts=bm.verts)
    obj = mesh_object(name, bm, mat)
    mod = obj.modifiers.new('suave', 'SUBSURF')
    mod.levels = 1
    mod.render_levels = 1
    smooth(obj, 180)
    return obj


# ── Puf ──
def build_pouf():
    x, y, z = POUF_AT
    pouf = cylinder('pouf', 0.4, 0.4, (x, y + 0.2, z), 'pouf', segments=48, bevel=0.13)
    parts = [pouf]
    for i in range(7):
        if i == 6:
            bx, bz = 0.0, 0.0
        else:
            a = i / 6 * math.pi * 2
            bx, bz = math.cos(a) * 0.2, math.sin(a) * 0.2
        parts.append(sphere(f'button_{i}', 0.022, (x + bx, y + 0.395, z + bz), 'pouf_button', scale=(1, 0.6, 1), segments=10))
    return parts


# ── Cuadro ──
def build_painting():
    x, y, z = PAINTING_AT
    outer = [(-0.24, -0.19), (0.24, -0.19), (0.24, 0.19), (-0.24, 0.19)]
    inner = [(-0.19, -0.14), (0.19, -0.14), (0.19, 0.14), (-0.19, 0.14)]
    frame = flat_shape('painting_frame', [outer, inner], 'wood_light', 0.03, 0.01, at=(x, y, z + 0.015))
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new('UVMap')
    corners = [(-0.2, -0.15, 0, 0), (0.2, -0.15, 1, 0), (0.2, 0.15, 1, 1), (-0.2, 0.15, 0, 1)]
    vs = [bm.verts.new(T(x + cx, y + cy, z + 0.012)) for cx, cy, _, _ in corners]
    f = bm.faces.new(vs)
    for loop, (_, _, u, v) in zip(f.loops, corners):
        loop[uv_layer].uv = (u, v)
    art = mesh_object('painting_art', bm, 'painting_art')
    if art.data.polygons[0].normal.y > 0:
        art.data.polygons[0].flip()
    return [frame, art]


# ── Pared de delante: solo para hornear (cierra la habitación detrás de la
# cámara; en el juego no está, así la cámara puede alejarse) ──
def build_front():
    bm = bmesh.new()
    quad = [T(-ROOM_X, 0, FRONT_Z), T(ROOM_X, 0, FRONT_Z), T(ROOM_X, CEIL, FRONT_Z), T(-ROOM_X, CEIL, FRONT_Z)]
    bm.faces.new([bm.verts.new(p) for p in quad])
    obj = mesh_object('front', bm, 'ceiling')
    if obj.data.polygons[0].normal.y < 0:
        obj.data.polygons[0].flip()
    return obj


# ── Paredes de los lados y techo (casi nunca se ven, pero dan rebotes) ──
def build_shell():
    parts = []
    for side in (-1, 1):
        bm = bmesh.new()
        x = side * ROOM_X
        quad = [T(x, 0, WALL_Z), T(x, 0, FRONT_Z), T(x, CEIL, FRONT_Z), T(x, CEIL, WALL_Z)]
        bm.faces.new([bm.verts.new(p) for p in quad])
        obj = mesh_object(f'side_{side}', bm, 'wallpaper')
        # Que miren hacia dentro.
        if obj.data.polygons[0].normal.x * side > 0:
            obj.data.polygons[0].flip()
        parts.append(obj)
    bm = bmesh.new()
    quad = [T(-ROOM_X, CEIL, WALL_Z), T(ROOM_X, CEIL, WALL_Z), T(ROOM_X, CEIL, FRONT_Z), T(-ROOM_X, CEIL, FRONT_Z)]
    bm.faces.new([bm.verts.new(p) for p in quad])
    ceil = mesh_object('ceiling', bm, 'ceiling')
    if ceil.data.polygons[0].normal.z > 0:
        ceil.data.polygons[0].flip()
    parts.append(ceil)
    # Subdividir para que las caras grandes no den problemas al hornear.
    for obj in parts:
        mod = obj.modifiers.new('rejilla', 'SUBSURF')
        mod.subdivision_type = 'SIMPLE'
        mod.levels = mod.render_levels = 3
    return parts


# ── Utilidades de malla ──
def to_meshes(objs):
    out = []
    for obj in objs:
        bpy.ops.object.select_all(action='DESELECT')
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.convert(target='MESH')
        out.append(bpy.context.view_layer.objects.active)
    return out


def join(objs, name):
    objs = to_meshes(objs)
    # Todas con una capa de UV con el mismo nombre (la de las texturas).
    for obj in objs:
        if not obj.data.uv_layers:
            obj.data.uv_layers.new(name='UVMap')
        else:
            obj.data.uv_layers[0].name = 'UVMap'
    bpy.ops.object.select_all(action='DESELECT')
    for obj in objs:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    if len(objs) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    obj.name = name
    obj.data.name = name
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return obj


def planar_lightmap(obj, fn):
    """Segunda capa de UV (la del mapa de luz) calculada con fn(x3, y3, z3) en
    coordenadas de Three.js."""
    me = obj.data
    lm = me.uv_layers.new(name='lightmap')
    for loop in me.loops:
        co = obj.matrix_world @ me.vertices[loop.vertex_index].co
        lm.data[loop.index].uv = fn(co.x, co.z, -co.y)


def profile_lightmap(obj, bands):
    """Mapa de luz de piezas largas (zócalo, moldura): u a lo largo de x y v
    alrededor del perfil, cada pieza en su franja. `bands` = [(y_centro,
    z_centro, v0, v1)] en coordenadas de Three.js; la costura queda detrás,
    contra la pared."""
    me = obj.data
    lm = me.uv_layers.new(name='lightmap')
    for poly in me.polygons:
        pts = [obj.matrix_world @ me.vertices[me.loops[li].vertex_index].co for li in poly.loop_indices]
        cy = sum(p.z for p in pts) / len(pts)
        band = min(bands, key=lambda b: abs(b[0] - cy))
        thetas = [math.atan2(p.z - band[0], (-p.y) - band[1]) for p in pts]
        if max(thetas) - min(thetas) > math.pi:
            thetas = [t + 2 * math.pi if t < 0 else t for t in thetas]
        for li, p, t in zip(poly.loop_indices, pts, thetas):
            u = (p.x + ROOM_X) / (2 * ROOM_X)
            v = band[2] + (band[3] - band[2]) * (t + math.pi) / (2 * math.pi)
            lm.data[li].uv = (u, v)


def unwrap_lightmap(obj, margin=0.004):
    me = obj.data
    lm = me.uv_layers.new(name='lightmap')
    me.uv_layers.active = lm
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=margin, area_weight=0.0, scale_to_bounds=False)
    bpy.ops.uv.pack_islands(margin=margin, rotate=True)
    bpy.ops.object.mode_set(mode='OBJECT')
    me.uv_layers.active = me.uv_layers[0]


# ── Construir ──
floor = build_floor()
floor.data.uv_layers.new(name='UVMap')
FLOOR_MAP = dict(x0=-ROOM_X, x1=ROOM_X, z0=WALL_Z, z1=FRONT_Z)
# u crece hacia la derecha; v de Blender = 1 en la pared (la fila de
# arriba del PNG, la t = 0 de Three.js).
planar_lightmap(floor, lambda x, y, z: ((x + ROOM_X) / (2 * ROOM_X), 1 - (z - WALL_Z) / (FRONT_Z - WALL_Z)))

wall = build_wall()
WALL_MAP = dict(x0=-ROOM_X, x1=ROOM_X, y0=0, y1=CEIL)
planar_lightmap(wall, lambda x, y, z: ((x + ROOM_X) / (2 * ROOM_X), y / CEIL))

trims = join(build_trims(), 'trims')
trims.data.uv_layers[0].name = 'UVMap'
smooth(trims, 45)
# Moldura arriba (franja 0.52-0.98), zócalo abajo (0.02-0.48).
profile_lightmap(trims, [(WAINSCOT, WALL_Z + 0.025, 0.52, 0.98), (0.06, WALL_Z + 0.032, 0.02, 0.48)])

atlas_parts = build_window() + build_shelf() + build_pouf() + build_painting()
atlas = join(atlas_parts, 'atlas')
smooth(atlas, 45)
unwrap_lightmap(atlas)

shell = join(build_shell(), 'shell')
front = build_front()
unwrap_lightmap(shell, margin=0.01)

TARGETS = {'floor': floor, 'wall': wall, 'atlas': atlas, 'trims': trims, 'shell': shell}

# ── Luces ──
sun_data = bpy.data.lights.new('sol', 'SUN')
sun_data.energy = 4.0
sun_data.angle = math.radians(1.6)
sun = link(bpy.data.objects.new('sol', sun_data))
# El sol entra por la ventana desde arriba a la izquierda y cae hacia la
# cámara, un poco hacia la derecha (la mancha queda a los pies de la criatura).
SUN_DIR = Vector((0.08, -0.7, 0.7)).normalized()  # en Three.js: hacia dónde viaja la luz
sun.rotation_mode = 'QUATERNION'
sun.rotation_quaternion = T(*SUN_DIR).to_track_quat('-Z', 'Y')

lamp_data = bpy.data.lights.new('lampara', 'POINT')
lamp_data.energy = 260
lamp_data.shadow_soft_size = 0.3
lamp_data.color = (1, 1, 1)
LAMP_AT = (1.5, 2.35, 0.4)
lamp = link(bpy.data.objects.new('lampara', lamp_data))
lamp.location = T(*LAMP_AT)


# Luz del día que no se ve entrar: un ventanal a la derecha, fuera de
# cuadro, y el rebote de la parte de la habitación que queda detrás de la
# cámara. Van en el canal del cielo con lo que entra por la ventana.
def area(name, size, energy, at, facing, color=(1, 1, 1)):
    data = bpy.data.lights.new(name, 'AREA')
    data.shape = 'RECTANGLE'
    data.size, data.size_y = size
    data.energy = energy
    data.color = color
    obj = link(bpy.data.objects.new(name, data))
    obj.location = T(*at)
    obj.rotation_mode = 'QUATERNION'
    obj.rotation_quaternion = T(*facing).to_track_quat('-Z', 'Y')
    return obj


SIDE_LIGHT = area('ventanal', (3.2, 1.9), float(arg('--side', 900)), (ROOM_X - 0.05, 1.55, 1.2), (-1, 0, 0))
FRONT_LIGHT = area('rebote', (5.0, 2.2), float(arg('--fill', 350)), (0.0, 1.7, FRONT_Z - 0.05), (0, -0.15, -1))
SKY_LIGHTS = [SIDE_LIGHT, FRONT_LIGHT]


def lights(channel):
    sun.hide_render = channel != 'sun'
    lamp.hide_render = channel != 'lamp'
    for light in SKY_LIGHTS:
        light.hide_render = channel != 'sky'
    background.inputs['Strength'].default_value = 1.0 if channel == 'sky' else 0.0


# ── Hornear ──
CHANNELS = ['sun', 'sky', 'lamp']
scene.render.bake.margin = 12
scene.render.bake.margin_type = 'EXTEND'
scene.render.bake.use_pass_direct = True
scene.render.bake.use_pass_indirect = True
scene.render.bake.use_pass_color = False

for name, obj in TARGETS.items():
    w, h = SIZES[name]
    image = bpy.data.images.new(f'lm_{name}', w, h, float_buffer=True, alpha=False)
    for slot in obj.material_slots:
        nodes = slot.material.node_tree.nodes
        node = nodes.get('bake_target') or nodes.new('ShaderNodeTexImage')
        node.name = 'bake_target'
        node.image = image
        nodes.active = node
    obj.data.uv_layers.active = obj.data.uv_layers['lightmap']
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    for channel in CHANNELS:
        lights(channel)
        print(f'horneando {name} / {channel} ({w}×{h}, {SAMPLES} muestras)', flush=True)
        bpy.ops.object.bake(type='DIFFUSE', pass_filter={'DIRECT', 'INDIRECT'}, margin=12, use_clear=True)
        pixels = np.empty(w * h * 4, dtype=np.float32)
        image.pixels.foreach_get(pixels)
        np.save(os.path.join(OUT, f'{name}_{channel}.npy'), pixels.reshape(h, w, 4)[:, :, :3])
    obj.data.uv_layers.active = obj.data.uv_layers[0]

# ── Vista previa (opcional): la habitación con las tres luces ──
if PREVIEW:
    cam_data = bpy.data.cameras.new('camara')
    cam_data.sensor_fit = 'VERTICAL'
    cam_data.angle_y = math.radians(30)
    cam = link(bpy.data.objects.new('camara', cam_data))
    cam.location = T(0, 1.55, 6.2)
    cam.rotation_mode = 'QUATERNION'
    cam.rotation_quaternion = (T(0, 0.8, 0) - cam.location).to_track_quat('-Z', 'Y')
    scene.camera = cam
    lights('sky')
    sun.hide_render = False
    scene.render.resolution_x, scene.render.resolution_y = 1280, 800
    scene.cycles.samples = 128
    scene.render.filepath = os.path.join(OUT, 'preview.png')
    bpy.ops.render.render(write_still=True)

# ── Exportar ──
for mat in bpy.data.materials:
    node = mat.node_tree.nodes.get('bake_target') if mat.node_tree else None
    if node:
        mat.node_tree.nodes.remove(node)
bpy.ops.object.select_all(action='DESELECT')
for obj in TARGETS.values():
    obj.select_set(True)
bpy.ops.export_scene.gltf(
    filepath=os.path.join(OUT, 'room.glb'), export_format='GLB', use_selection=True,
    export_apply=True, export_texcoords=True, export_normals=True, export_materials='EXPORT',
    export_lights=False, export_cameras=False, export_yup=True,
)

with open(os.path.join(OUT, 'room-bake.json'), 'w') as f:
    json.dump({
        'samples': SAMPLES, 'sizes': SIZES, 'floor': FLOOR_MAP, 'wall': WALL_MAP,
        'sun': list(SUN_DIR), 'lamp': list(LAMP_AT), 'window': WIN, 'wallZ': WALL_Z,
    }, f, indent=1)
print('listo:', OUT)
