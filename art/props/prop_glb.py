# Objetos (muebles, plantas…): imagen → TRELLIS.2 → malla fina con el color
# en los vértices (.ply), sin nvdiffrast (su licencia es solo de
# investigación). La textura la hornea después Blender (prop_bake.py).
#
# Uso: prop_glb.py lista.json [tipo=1024_cascade] [semilla=42]
#   lista.json: [["entrada.png", "salida/base"], ...] → base.pt y base-high.ply
#
# El color se toma del volumen de atributos de TRELLIS con muestreo
# trilineal y se divide por la cobertura (los vóxeles vacíos cuentan como
# negro; ver bake.py), igual que el horneado original pero en los vértices.

import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import img2glb  # noqa: E402,F401  (aplica los cambios de licencia: DINOv3 de timm, BiRefNet)

import numpy as np  # noqa: E402
import torch  # noqa: E402
import trimesh  # noqa: E402
import cumesh  # noqa: E402
from flex_gemm.ops.grid_sample import grid_sample_3d  # noqa: E402
from PIL import Image  # noqa: E402
from trellis2.pipelines import Trellis2ImageTo3DPipeline  # noqa: E402

HIGH_FACES = 300000


def clean(vertices, faces):
    mesh = cumesh.CuMesh()
    mesh.init(vertices.cuda(), faces.cuda())
    mesh.fill_holes(max_hole_perimeter=3e-2)
    mesh.simplify(HIGH_FACES * 2, verbose=False)
    mesh.remove_duplicate_faces()
    mesh.repair_non_manifold_edges()
    mesh.remove_small_connected_components(1e-5)
    mesh.fill_holes(max_hole_perimeter=3e-2)
    mesh.simplify(HIGH_FACES, verbose=False)
    mesh.remove_duplicate_faces()
    mesh.repair_non_manifold_edges()
    mesh.remove_small_connected_components(1e-5)
    mesh.unify_face_orientations()
    return mesh.read()


def vertex_colors(mesh, positions):
    attrs = mesh.attrs.float().cuda()
    coords = mesh.coords.cuda()
    aabb = torch.tensor([[-0.5, -0.5, -0.5], [0.5, 0.5, 0.5]], device='cuda')
    voxel = float(mesh.voxel_size)
    grid = ((aabb[1] - aabb[0]) / voxel).round().int()
    feats = torch.cat([attrs, torch.ones_like(attrs[:, :1])], 1)
    out = grid_sample_3d(
        feats,
        torch.cat([torch.zeros_like(coords[:, :1]), coords], -1).int(),
        shape=torch.Size([1, feats.shape[1], *grid.tolist()]),
        grid=((positions - aabb[0]) / voxel).reshape(1, -1, 3),
        mode='trilinear',
    ).reshape(-1, feats.shape[1])
    cover = out[:, -1:].clamp(min=1e-4)
    color = (out[:, mesh.layout['base_color']] / cover).clamp(0, 1)
    # Donde casi no hay cobertura, el color de la media (no negro).
    weak = cover[:, 0] < 0.35
    if weak.any():
        color[weak] = color[~weak].mean(0)
    return color


def main():
    jobs = json.loads(open(sys.argv[1]).read())
    kind = sys.argv[2] if len(sys.argv) > 2 else '1024_cascade'
    seed = int(sys.argv[3]) if len(sys.argv) > 3 else 42
    common = ['sparse_structure_decoder', 'sparse_structure_flow_model', 'shape_slat_decoder', 'tex_slat_decoder']
    Trellis2ImageTo3DPipeline.model_names_to_load = common + {
        '512': ['shape_slat_flow_model_512', 'tex_slat_flow_model_512'],
        '1024_cascade': ['shape_slat_flow_model_512', 'shape_slat_flow_model_1024', 'tex_slat_flow_model_1024'],
    }[kind]
    pipeline = Trellis2ImageTo3DPipeline.from_pretrained('microsoft/TRELLIS.2-4B')
    pipeline.cuda()
    for src, base in jobs:
        if os.path.exists(base + '-high.ply'):
            print('ya está', base, flush=True)
            continue
        t0 = time.time()
        os.makedirs(os.path.dirname(base), exist_ok=True)
        mesh = pipeline.run(Image.open(src), seed=seed, pipeline_type=kind)[0]
        torch.save({
            'vertices': mesh.vertices.cpu(), 'faces': mesh.faces.cpu(), 'attrs': mesh.attrs.cpu(),
            'coords': mesh.coords.cpu(), 'layout': mesh.layout, 'voxel_size': mesh.voxel_size,
        }, base + '.pt')
        vertices, faces = clean(mesh.vertices, mesh.faces)
        color = vertex_colors(mesh, vertices.float())
        rgba = np.concatenate([(color.cpu().numpy() * 255 + 0.5).astype(np.uint8),
                               np.full((len(vertices), 1), 255, np.uint8)], 1)
        trimesh.Trimesh(vertices.cpu().numpy(), faces.cpu().numpy(), vertex_colors=rgba, process=False).export(base + '-high.ply')
        print(f'{base}: {len(faces)} caras en {time.time() - t0:.0f} s', flush=True)
        del mesh
        torch.cuda.empty_cache()


if __name__ == '__main__':
    main()
