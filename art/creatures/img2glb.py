# Imagen a modelo 3D texturizado (.glb) con TRELLIS.2 (MIT), en local.
#
# Cambios respecto al ejemplo oficial, todos por licencia o por memoria:
# - DINOv3 sale de la copia de timm (sin bloqueo en Hugging Face) en vez de
#   facebook/dinov3-*, que exige aprobación manual de Meta.
# - El recorte del fondo usa ZhengPeng7/BiRefNet (MIT) en vez de
#   briaai/RMBG-2.0 (licencia no comercial).
# - Atención con xformers (flash-attn exigiría compilarlo).
# - El paso a .glb rasteriza en UV con uvraster.py en vez de nvdiffrast
#   (licencia solo de investigación): nvdiffrast no llega a cargarse.
#
# Uso: img2glb.py entrada.png salida.glb [tipo=512|1024_cascade] [semilla]

import os
import sys

os.environ.setdefault("ATTN_BACKEND", "xformers")
os.environ.setdefault("OPENCV_IO_ENABLE_OPENEXR", "1")
os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")
sys.path.insert(0, os.path.expanduser("~/opt/trellis2"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import uvraster  # noqa: E402

uvraster.install()

import time

import timm
import torch
import torch.nn.functional as F
from PIL import Image
from torchvision import transforms

from trellis2.modules import image_feature_extractor
from trellis2.pipelines import rembg


class TimmDinoV3:
    """Lo mismo que DinoV3FeatureExtractor de TRELLIS.2 (todas las fichas
    de la última capa con layer_norm sin pesos), con los pesos de timm."""

    def __init__(self, model_name: str = "", image_size: int = 512):
        self.model = timm.create_model("vit_large_patch16_dinov3.lvd1689m", pretrained=True)
        self.model.norm = torch.nn.Identity()
        self.model.eval()
        self.image_size = image_size
        self.transform = transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])

    def to(self, device):
        self.model.to(device)

    def cuda(self):
        self.model.cuda()

    def cpu(self):
        self.model.cpu()

    @torch.no_grad()
    def __call__(self, image):
        import numpy as np

        if isinstance(image, list):
            image = [i.resize((self.image_size, self.image_size), Image.LANCZOS) for i in image]
            image = [np.array(i.convert("RGB")).astype(np.float32) / 255 for i in image]
            image = torch.stack([torch.from_numpy(i).permute(2, 0, 1) for i in image]).cuda()
        image = self.transform(image).cuda()
        hidden = self.model.forward_features(image)
        return F.layer_norm(hidden, hidden.shape[-1:])


class OpenBiRefNet(rembg.BiRefNet):
    def __init__(self, model_name: str = ""):
        super().__init__("ZhengPeng7/BiRefNet")


image_feature_extractor.DinoV3FeatureExtractor = TimmDinoV3
rembg.BiRefNet = OpenBiRefNet

from trellis2.pipelines import Trellis2ImageTo3DPipeline  # noqa: E402
import o_voxel  # noqa: E402


def to_fp32(pipeline, decoders):
    """Precisión completa solo mientras cada modelo está en la GPU: en la RAM
    sigue en su precisión de origen (si no, ocuparía el doble)."""
    import types

    for name, model in pipeline.models.items():
        is_flow = hasattr(model, "convert_to")
        is_dec = hasattr(model, "convert_to_fp32") and getattr(model, "use_fp16", False)
        if not (is_flow or (decoders and is_dec)):
            continue
        native = model.dtype
        to, cpu = model.to, model.cpu

        def gpu(self, *args, _to=to, _flow=is_flow, **kwargs):
            out = _to(*args, **kwargs)
            if _flow:
                self.convert_to(torch.float32)
            else:
                self.convert_to_fp32()
            return out

        def back(self, _cpu=cpu, _flow=is_flow, _native=native):
            if _flow:
                self.convert_to(_native)
            else:
                self.convert_to_fp16()
            return _cpu()

        model.to = types.MethodType(gpu, model)
        model.cpu = types.MethodType(back, model)
        print(f"fp32 en GPU: {name}", flush=True)


def free_after_use(pipeline):
    """Cada modelo de flujo se usa una sola vez por imagen: cuando el
    pipeline lo devuelve a la CPU, se tiran sus pesos. Sin esto, a 1024 se
    juntan ~15 GB de modelos en la RAM con el decodificado y no cabe en los
    20 GB que se le dejan."""
    import types

    for name, model in pipeline.models.items():
        if "flow_model" not in name:
            continue

        def gone(self):
            self.to_empty(device="meta")
            torch.cuda.empty_cache()
            return self

        model.cpu = types.MethodType(gone, model)


def main():
    global precision
    src, dst = sys.argv[1], sys.argv[2]
    kind = sys.argv[3] if len(sys.argv) > 3 else "512"
    seed = int(sys.argv[4]) if len(sys.argv) > 4 else 42
    precision = sys.argv[5] if len(sys.argv) > 5 else "bf16"  # bf16 | fp32 | fp32flow

    t0 = time.time()
    # Solo los modelos que usa cada modo: todos juntos ocupan ~17 GB de RAM.
    common = ["sparse_structure_decoder", "sparse_structure_flow_model", "shape_slat_decoder", "tex_slat_decoder"]
    Trellis2ImageTo3DPipeline.model_names_to_load = common + {
        "512": ["shape_slat_flow_model_512", "tex_slat_flow_model_512"],
        "1024": ["shape_slat_flow_model_1024", "tex_slat_flow_model_1024"],
        "1024_cascade": ["shape_slat_flow_model_512", "shape_slat_flow_model_1024", "tex_slat_flow_model_1024"],
    }[kind]
    pipeline = Trellis2ImageTo3DPipeline.from_pretrained("microsoft/TRELLIS.2-4B")
    if precision != "bf16":
        to_fp32(pipeline, decoders=precision == "fp32")
    else:
        free_after_use(pipeline)
    pipeline.cuda()
    image = Image.open(src)
    mesh = pipeline.run(image, seed=seed, pipeline_type=kind)[0]
    print(f"generado en {time.time() - t0:.0f} s; pico de VRAM {torch.cuda.max_memory_allocated() / 2**30:.1f} GB", flush=True)
    # El paso a malla (CuMesh) reserva memoria por su cuenta: se suelta la
    # caché de PyTorch y los modelos antes, que a 1024 no cabe todo junto.
    import gc

    del pipeline
    gc.collect()
    torch.cuda.empty_cache()
    print(f"caras antes de simplificar: {mesh.faces.shape[0]}", flush=True)
    # Lo que sale de TRELLIS, tal cual: bake.py rehace el paso a .glb sin
    # tener que volver a generar.
    torch.save({
        "vertices": mesh.vertices.cpu(), "faces": mesh.faces.cpu(), "attrs": mesh.attrs.cpu(),
        "coords": mesh.coords.cpu(), "layout": mesh.layout, "voxel_size": mesh.voxel_size,
    }, dst.replace(".glb", ".pt"))
    mesh.simplify(16777216)  # límite de nvdiffrast, como el ejemplo oficial

    glb = o_voxel.postprocess.to_glb(
        vertices=mesh.vertices,
        faces=mesh.faces,
        attr_volume=mesh.attrs,
        coords=mesh.coords,
        attr_layout=mesh.layout,
        voxel_size=mesh.voxel_size,
        aabb=[[-0.5, -0.5, -0.5], [0.5, 0.5, 0.5]],
        decimation_target=200000,
        texture_size=2048,
        remesh=True,
        remesh_band=1,
        remesh_project=0,
        verbose=False,
    )
    glb.export(dst, extension_webp=False)
    uvraster.check()
    print(f"guardado {dst} en {time.time() - t0:.0f} s", flush=True)


if __name__ == "__main__":
    main()
