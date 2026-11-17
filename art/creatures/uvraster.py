# Sustituto de nvdiffrast para el paso a .glb de TRELLIS.2 (o_voxel).
#
# nvdiffrast solo admite uso de investigación o evaluación, así que no puede
# hacer las texturas de un juego comercial. o_voxel.postprocess.to_glb solo
# le pide dos cosas: rasterizar la malla en el espacio UV (qué triángulo cae
# en cada téxel y con qué baricéntricas) e interpolar las posiciones. Aquí
# están las dos en torch, con las mismas firmas.
#
# install() pone este módulo en sys.modules como `nvdiffrast` y
# `nvdiffrast.torch` antes de que nada lo importe: el nvdiffrast de verdad
# no llega a cargarse. Cualquier otra función suya da error.
#
#   import uvraster; uvraster.install()   # antes de importar o_voxel/trellis2

import sys
import types

import torch

# Pares (triángulo, téxel candidato) por tanda: ~100 bytes cada uno en la GPU.
CHUNK = 6_000_000
# Margen en las baricéntricas para no dejar téxeles sin pintar en las
# aristas compartidas por errores de redondeo.
EPS = 1e-6


class RasterizeCudaContext:
    def __init__(self, *args, **kwargs):
        pass


def rasterize(ctx, pos, tri, resolution, ranges=None, grad_db=True):
    """Como nvdiffrast.torch.rasterize para una sola capa sin profundidad:
    devuelve (1, H, W, 4) con (b0, b1, 0, triángulo + 1); 0 = vacío.
    El téxel (fila j, columna i) tiene su centro en el clip
    ((i + 0.5) / W * 2 - 1, (j + 0.5) / H * 2 - 1), igual que nvdiffrast."""
    assert pos.shape[0] == 1 and ranges is None
    H, W = resolution
    p = pos[0, :, :2] / pos[0, :, 3:4]
    x = (p[:, 0] + 1) * 0.5 * W - 0.5
    y = (p[:, 1] + 1) * 0.5 * H - 0.5
    tri = tri.long()
    xs, ys = x[tri], y[tri]  # (F, 3)
    x0, x1, x2 = xs.unbind(1)
    y0, y1, y2 = ys.unbind(1)
    det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    xmin = xs.min(1).values.ceil().clamp(0, W - 1).long()
    xmax = xs.max(1).values.floor().clamp(-1, W - 1).long()
    ymin = ys.min(1).values.ceil().clamp(0, H - 1).long()
    ymax = ys.max(1).values.floor().clamp(-1, H - 1).long()
    w = (xmax - xmin + 1).clamp(min=0)
    h = (ymax - ymin + 1).clamp(min=0)
    n = w * h
    n[det.abs() < 1e-12] = 0

    out = torch.zeros(H * W, 4, device=pos.device, dtype=torch.float32)
    ends = n.cumsum(0)
    a = 0
    while a < len(n):
        base = int(ends[a - 1]) if a > 0 else 0
        b = int(torch.searchsorted(ends, base + CHUNK, right=True))
        b = max(b, a + 1)
        count = n[a:b]
        total = int(count.sum())
        if total:
            f = torch.repeat_interleave(torch.arange(a, b, device=pos.device), count)
            k = torch.arange(total, device=pos.device) - (ends[f] - n[f] - base)
            px = xmin[f] + k % w[f]
            py = ymin[f] + k // w[f]
            fx, fy = px.float(), py.float()
            d = det[f]
            b0 = ((y1[f] - y2[f]) * (fx - x2[f]) + (x2[f] - x1[f]) * (fy - y2[f])) / d
            b1 = ((y2[f] - y0[f]) * (fx - x2[f]) + (x0[f] - x2[f]) * (fy - y2[f])) / d
            inside = (b0 >= -EPS) & (b1 >= -EPS) & (1 - b0 - b1 >= -EPS)
            idx = (py * W + px)[inside]
            vals = torch.stack([b0, b1, torch.zeros_like(b0), (f + 1).float()], 1)[inside]
            out[idx] = vals
        a = b
    return out.reshape(1, H, W, 4), None


def interpolate(attr, rast, tri, rast_db=None, diff_attrs=None):
    """Como nvdiffrast.torch.interpolate: b0·A0 + b1·A1 + (1 − b0 − b1)·A2."""
    a = attr[0] if attr.dim() == 3 else attr
    tid = rast[..., 3].long() - 1
    valid = tid >= 0
    out = torch.zeros(*rast.shape[:-1], a.shape[-1], device=a.device, dtype=a.dtype)
    t = tri.long()[tid[valid]]
    b0 = rast[..., 0][valid][:, None]
    b1 = rast[..., 1][valid][:, None]
    out[valid] = b0 * a[t[:, 0]] + b1 * a[t[:, 1]] + (1 - b0 - b1) * a[t[:, 2]]
    return out, None


def install():
    if isinstance(sys.modules.get("nvdiffrast.torch"), types.ModuleType) and getattr(
            sys.modules["nvdiffrast.torch"], "UVRASTER", False):
        return
    assert "nvdiffrast" not in sys.modules, "nvdiffrast ya estaba cargado"
    top = types.ModuleType("nvdiffrast")
    mod = types.ModuleType("nvdiffrast.torch")
    mod.UVRASTER = True
    mod.RasterizeCudaContext = RasterizeCudaContext
    mod.RasterizeGLContext = RasterizeCudaContext
    mod.rasterize = rasterize
    mod.interpolate = interpolate

    def missing(name):
        raise AttributeError(f"nvdiffrast.torch.{name}: no está en uvraster (nvdiffrast no se usa)")

    mod.__getattr__ = missing
    top.torch = mod
    sys.modules["nvdiffrast"] = top
    sys.modules["nvdiffrast.torch"] = mod


def check():
    """Comprueba que lo cargado es esto y no el nvdiffrast de verdad."""
    assert getattr(sys.modules.get("nvdiffrast.torch"), "UVRASTER", False), "se cargó el nvdiffrast de verdad"
