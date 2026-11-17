# Prueba de uvraster.py contra un cálculo directo, téxel a téxel, en una
# rejilla de triángulos con aristas compartidas (y en muchas tandas).
# ~/opt/envs/trellis2/bin/python test_uvraster.py   (necesita CUDA)
import os
import sys
import numpy as np
import torch
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import uvraster
uvraster.install()
import nvdiffrast.torch as dr
uvraster.check()

torch.manual_seed(0)
H = W = 64
# Rejilla de quads partidos en dos (aristas compartidas) más algo de ruido.
g = 9
xs, ys = np.meshgrid(np.linspace(0.02, 0.98, g), np.linspace(0.03, 0.97, g))
uv = np.stack([xs, ys], -1).reshape(-1, 2) + np.random.default_rng(1).normal(0, 0.01, (g * g, 2))
faces = []
for j in range(g - 1):
    for i in range(g - 1):
        a, b, c, d = j * g + i, j * g + i + 1, (j + 1) * g + i, (j + 1) * g + i + 1
        faces += [[a, b, d], [a, d, c]]
uv = torch.tensor(uv, dtype=torch.float32, device='cuda')
faces = torch.tensor(faces, dtype=torch.int32, device='cuda')
clip = torch.cat([uv * 2 - 1, torch.zeros_like(uv[:, :1]), torch.ones_like(uv[:, :1])], -1)[None]
uvraster.CHUNK = 50  # fuerza muchas tandas
rast, _ = dr.rasterize(None, clip, faces, resolution=[H, W])
pos3 = torch.randn(len(uv), 3, device='cuda')
interp, _ = dr.interpolate(pos3[None], rast, faces)

# Referencia: para cada téxel, el triángulo que contiene su centro.
U = uv.cpu().numpy().astype(np.float64); F = faces.cpu().numpy(); P = pos3.cpu().numpy()
holes = wrong = 0
for j in range(H):
    for i in range(W):
        p = np.array([(i + 0.5) / W, (j + 0.5) / H])
        found = None
        for fi, (a, b, c) in enumerate(F):
            m = np.array([U[a] - U[c], U[b] - U[c]]).T
            l0, l1 = np.linalg.solve(m, p - U[c])
            if min(l0, l1, 1 - l0 - l1) > 1e-4:
                found = (fi, l0, l1); break
        t = int(rast[0, j, i, 3].item())
        if found is None:
            continue
        fi, l0, l1 = found
        if t == 0:
            holes += 1; continue
        a, b, c = F[fi]
        want = l0 * P[a] + l1 * P[b] + (1 - l0 - l1) * P[c]
        if t - 1 != fi or np.abs(interp[0, j, i].cpu().numpy() - want).max() > 1e-4:
            wrong += 1
covered = int((rast[0, ..., 3] > 0).sum())
print(f'téxeles cubiertos {covered}, huecos {holes}, mal {wrong}')
assert holes == 0 and wrong == 0
# Filas: la fila 0 es v ≈ 0 (como nvdiffrast, clip y = -1).
assert rast[0, 0, :, 3].gt(0).sum() == 0 and rast[0, 2, 32, 3] > 0
print('OK')
