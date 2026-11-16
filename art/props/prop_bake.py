# Objeto listo para el juego: de la malla fina con color por vértice que
# deja prop_glb.py a un .glb ligero con su textura, horneada con Cycles
# (sin nvdiffrast).
#
# blender -b -P prop_bake.py -- alta.ply salida.glb (--height H | --width W)
#         [--tris 14000] [--tex 1024] [--yaw grados] [--preview vista.png]
#
# Deja el objeto de pie sobre el suelo (y = 0), centrado, de frente a +z
# en glTF y con la altura (o el ancho) que se pide, en metros.

import math
import sys

import bpy
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index('--') + 1:]
src, dst = argv[0], argv[1]


def opt(name, default=None, cast=float):
    return cast(argv[argv.index(name) + 1]) if name in argv else default


HEIGHT = opt('--height')
WIDTH = opt('--width')
TRIS = opt('--tris', 14000, int)
# Hasta dónde busca el horneado la malla fina (en fracción del tamaño): más
# corto evita coger el color de lo que hay detrás (pétalos, hojas).
REACH = opt('--reach', 0.012)
TEX = opt('--tex', 1024, int)
YAW = opt('--yaw', 0.0)
PREVIEW = opt('--preview', None, str)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
prefs = bpy.context.preferences.addons['cycles'].preferences
for kind in ('OPTIX', 'CUDA'):
    try:
        prefs.compute_device_type = kind
        prefs.get_devices()
        if any(d.type == kind for d in prefs.devices):
            for d in prefs.devices:
                d.use = d.type == kind
            scene.cycles.device = 'GPU'
            break
    except Exception:  # noqa: BLE001
        pass

bpy.ops.wm.ply_import(filepath=src, import_colors='SRGB')
high = bpy.context.selected_objects[0]
high.name = 'alta'
bpy.context.view_layer.objects.active = high

# Girar, centrar, apoyar en el suelo y escalar.
high.rotation_euler = (0, 0, math.radians(YAW))
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
pts = [v.co for v in high.data.vertices]
lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
size = hi - lo
scale = HEIGHT / size.z if HEIGHT else WIDTH / max(size.x, size.y)
high.data.transform(Matrix.Translation(Vector((-(lo.x + hi.x) / 2, -(lo.y + hi.y) / 2, -lo.z))))
high.data.transform(Matrix.Scale(scale, 4))
high.data.update()
extent = max(size) * scale

# La versión ligera.
low = high.copy()
low.data = high.data.copy()
low.name = 'objeto'
scene.collection.objects.link(low)
faces = len(low.data.polygons)
mod = low.modifiers.new('reducir', 'DECIMATE')
mod.ratio = min(1.0, TRIS / faces)
mod.use_collapse_triangulate = True
bpy.ops.object.select_all(action='DESELECT')
low.select_set(True)
bpy.context.view_layer.objects.active = low
bpy.ops.object.modifier_apply(modifier=mod.name)
# Normales hacia fuera: TRELLIS y la reducción dejan zonas giradas, que se
# verían huecas (sin dibujar) y harían que el horneado mire hacia dentro.
bpy.ops.object.mode_set(mode='EDIT')
bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.mesh.normals_make_consistent(inside=False)
bpy.ops.object.mode_set(mode='OBJECT')
for attr in list(low.data.color_attributes):
    low.data.color_attributes.remove(attr)
for p in low.data.polygons:
    p.use_smooth = True
low.data.set_sharp_from_angle(angle=math.radians(50))

uv = low.data.uv_layers.new(name='UVMap')
low.data.uv_layers.active = uv
bpy.ops.object.mode_set(mode='EDIT')
bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.003, area_weight=0.0, scale_to_bounds=False)
bpy.ops.uv.pack_islands(margin=0.003, rotate=True)
bpy.ops.object.mode_set(mode='OBJECT')

# Materiales: la alta emite su color de vértice; la ligera lo recibe.
src_mat = bpy.data.materials.new('color_vertice')
nodes = src_mat.node_tree.nodes
nodes.clear()
attr = nodes.new('ShaderNodeVertexColor')
emit = nodes.new('ShaderNodeEmission')
out = nodes.new('ShaderNodeOutputMaterial')
src_mat.node_tree.links.new(attr.outputs['Color'], emit.inputs['Color'])
src_mat.node_tree.links.new(emit.outputs['Emission'], out.inputs['Surface'])
high.data.materials.clear()
high.data.materials.append(src_mat)

image = bpy.data.images.new('color', TEX, TEX, alpha=False)
mat = bpy.data.materials.new('objeto')
bsdf = mat.node_tree.nodes['Principled BSDF']
bsdf.inputs['Roughness'].default_value = 0.72
tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
tex.image = image
mat.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
mat.node_tree.nodes.active = tex
low.data.materials.clear()
low.data.materials.append(mat)

scene.cycles.samples = 16
scene.render.bake.use_selected_to_active = True
scene.render.bake.cage_extrusion = extent * REACH
scene.render.bake.max_ray_distance = extent * REACH * 3
scene.render.bake.margin = 8
bpy.ops.object.select_all(action='DESELECT')
high.select_set(True)
low.select_set(True)
bpy.context.view_layer.objects.active = low
bpy.ops.object.bake(type='EMIT', use_selected_to_active=True, cage_extrusion=extent * REACH,
                    max_ray_distance=extent * REACH * 3, margin=8)
image.pack()

bpy.data.objects.remove(high)
bpy.ops.object.select_all(action='DESELECT')
low.select_set(True)
bpy.ops.export_scene.gltf(filepath=dst, export_format='GLB', use_selection=True, export_image_format='JPEG',
                          export_jpeg_quality=88, export_yup=True, export_apply=True)
print(f'OK {dst}: {faces} -> {len(low.data.polygons)} caras, {size.z * scale:.2f} m de alto', flush=True)

if PREVIEW:
    cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
    scene.collection.objects.link(cam)
    h = size.z * scale
    cam.location = Vector((0, -extent * 2.6, h * 0.65 + extent * 0.4))
    cam.rotation_mode = 'QUATERNION'
    cam.rotation_quaternion = (Vector((0, 0, h * 0.45)) - cam.location).to_track_quat('-Z', 'Y')
    cam.data.lens = 50
    scene.camera = cam
    sun = bpy.data.objects.new('sol', bpy.data.lights.new('sol', 'SUN'))
    sun.data.energy = 3
    sun.rotation_euler = (math.radians(50), 0, math.radians(-30))
    scene.collection.objects.link(sun)
    scene.world = bpy.data.worlds.new('w')
    scene.world.color = (0.6, 0.6, 0.6)
    scene.render.resolution_x = scene.render.resolution_y = 400
    scene.cycles.samples = 32
    scene.render.filepath = PREVIEW
    bpy.ops.render.render(write_still=True)
