# Bocetos de muebles y objetos (no criaturas) con FLUX.2 [klein] 4B (Apache 2.0), en local.
# Carga en dos fases para que quepa en 16 GB de VRAM sin llenar la RAM:
# primero el codificador de texto (Qwen3), luego el transformador y el VAE.
#
# Uso: concept.py prompts.json salida/ [semillas]
# prompts.json: {"nombre": "texto", ...}

import gc
import json
import sys
from pathlib import Path

import torch
from diffusers import Flux2KleinPipeline

REPO = "black-forest-labs/FLUX.2-klein-4B"
STYLE = (
    " Stylized 3D game prop render, high-end animated feature film quality, "
    "single object fully visible and centered with generous empty margin around it, three-quarter front view from slightly above, "
    "plain flat light grey studio background, soft even studio lighting, no cast shadow on the floor, "
    "clean readable silhouette, chunky rounded proportions, saturated but natural colours, "
    "matte hand-painted materials with subtle texture, no text, no logo, no people."
)


def main():
    prompts = json.loads(Path(sys.argv[1]).read_text())
    out = Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    seeds = [int(s) for s in sys.argv[3].split(",")] if len(sys.argv) > 3 else [1, 2, 3, 4]

    # Fase 1: textos a vectores.
    pipe = Flux2KleinPipeline.from_pretrained(
        REPO, transformer=None, vae=None, torch_dtype=torch.bfloat16
    )
    pipe.text_encoder.to("cuda")
    embeds = {}
    with torch.no_grad():
        for name, text in prompts.items():
            e, _ = pipe.encode_prompt(prompt=text + STYLE, device="cuda")
            embeds[name] = e.cpu()
    del pipe
    gc.collect()
    torch.cuda.empty_cache()

    # Fase 2: imágenes.
    pipe = Flux2KleinPipeline.from_pretrained(
        REPO, text_encoder=None, tokenizer=None, torch_dtype=torch.bfloat16
    ).to("cuda")
    for name, e in embeds.items():
        for seed in seeds:
            image = pipe(
                prompt_embeds=e.to("cuda"),
                height=1024,
                width=1024,
                guidance_scale=1.0,
                num_inference_steps=4,
                generator=torch.Generator("cuda").manual_seed(seed),
            ).images[0]
            path = out / f"{name}-s{seed}.png"
            image.save(path)
            print(path, flush=True)


if __name__ == "__main__":
    main()
