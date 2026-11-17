# Criaturas de modelo

Cada familia de monstruos tiene un modelo por etapa (ver
`public/js/scene/models.js`):

| familia | cría    | joven  | adulta |
|---------|---------|--------|--------|
| pelusa  | mascota | ascua  | brasa  |
| hoja    | brote   | trepa  | bosque |
| gota    | gotita  | chapo  | marea  |

Se hacen en local, con licencias que permiten uso comercial:

1. **Boceto** con FLUX.2 [klein] 4B (Apache 2.0).
2. **Forma y textura** con TRELLIS.2 (MIT): `img2glb.py`. DINOv3 sale de la
   copia de timm y el recorte del fondo, de BiRefNet (MIT), en vez de los
   originales de licencia cerrada.
3. **Para el juego** con Blender (`gameready.py`, `cut.py`, `splay.py`,
   `decimate.py`) y **atado** (`rig.py`, puntos en `rig/*-points.json`),
   que borra ojos y boca pintados y guarda sus sitios en los extras del
   `.glb`. `modeldata.py` copia esos extras a `public/js/scene/model-data.js`.

## Sin nvdiffrast

El paso a `.glb` de TRELLIS (`o_voxel.postprocess.to_glb`) usa nvdiffrast
para rasterizar la malla en el espacio UV, y nvdiffrast solo admite uso de
investigación. `uvraster.py` hace lo mismo en torch (`rasterize` e
`interpolate` con las mismas firmas) y se instala en `sys.modules` antes
de importar nada de TRELLIS, así que el nvdiffrast de verdad no llega a
cargarse (`uvraster.check()` lo comprueba al guardar). `test_uvraster.py`
lo compara téxel a téxel con un cálculo directo.

Las dos criaturas se rehicieron así el 2026-11-16, con los mismos bocetos,
semillas y puntos de atado que las primeras (TRELLIS da la misma malla con
la misma semilla; el remallado de CuMesh varía un poco, nada que se vea):

| modelo  | boceto                     | semilla | cadena |
|---------|----------------------------|---------|--------|
| mascota | `concepts/v4/mascota-s2.png` | 42    | gameready 60 000 → cut (`rig/mascota42-cut.json`) → splay (`auto/mascota-abrir.json`) → decimate 30 000 → rig (`auto/mascota-points.json`) |
| brasa   | `concepts/v4/brasa-s3.png`   | 7     | gameready 60 000 → decimate 30 000 → rig (`rig/brasa-points.json`) |

```sh
cd ~/opt/nidaria-art
systemd-run --user --wait -p MemoryMax=20G -p MemorySwapMax=0 \
  ~/opt/envs/trellis2/bin/python tools/img2glb.py concepts/v4/brasa-s3.png out/lic/brasa-s3-raw.glb 1024_cascade 7 bf16
blender -b -P tools/gameready.py -- out/lic/brasa-s3-raw.glb out/lic/brasa-s3.glb 1.0 60000 2048
blender -b -P tools/decimate.py -- out/lic/brasa-s3.glb out/lic/brasa-web.glb 30000
blender -b -P tools/rig.py -- out/lic/brasa-web.glb rig/brasa-points.json out/lic/brasa-rig.glb
python3 tools/modeldata.py ~/Escritorio/nidaria/public/models ~/Escritorio/nidaria/public/js/scene/model-data.js
```

`img2glb.py` deja también el `.pt` con la salida de TRELLIS: `bake.py`
rehace el `.glb` desde él sin volver a generar.

## Las siete del 2026-11-17

Bocetos en `concepts/prompts-v5.json` (ascua) y `prompts-v6.json` (las
demás), todas con semilla 42 en TRELLIS. Cambios en la cadena:

- **Memoria.** `img2glb.py` tira los pesos de cada modelo de flujo en
  cuanto lo usa (`free_after_use`). Aun así las hoja piden 20 GB: TRELLIS
  va solo, sin nada pesado a la vez. El PC tiene 30 GB y se congeló dos
  veces al juntar trabajos; desde entonces lo demás va en `nidart.slice`
  (unidad de usuario de systemd con un tope total de 14 GB) y TRELLIS, que
  no cabe ahí, se lanza solo y cuando no corre nada más.
- **Menos triángulos.** `decimate.py` (Blender) rompía estas mallas en
  facetas: se reduce con `bake.py ... 1 1024 30000` (la simplificación de
  CuMesh, la misma de TRELLIS) y `gameready.py` solo las pone a 1 m.
- **Atado automático.** `autopoints.py` cierra agujeros de la piel antes de
  rellenar en vóxeles y, si sigue hueca, rellena por cortes (bosque tenía el
  cuerpo abierto). Guarda ese volumen macizo y `rig.py` lo usa para los
  pesos cuando la copia en vóxeles de la malla sale hueca. `face.py` supone
  la nariz encima de la boca si no la ve (tritones).

```sh
cd ~/opt/nidaria-art
systemd-run --user --wait -p MemoryMax=20G -p MemorySwapMax=0 ~/opt/envs/trellis2/bin/python tools/img2glb.py concepts/v6/trepa-s3.png out/v6/trepa-raw.glb 1024_cascade 42 bf16
out/v6/prep.sh trepa      # bake 30 000 → gameready → tools/auto.sh (atado)
python3 tools/modeldata.py ~/Escritorio/nidaria/public/models ~/Escritorio/nidaria/public/js/scene/model-data.js
```

En el juego, `hueScale` frena el giro de tono del ADN en las hoja (el pelo
castaño se volvía rosa) y en las gota (las branquias coral, amarillas).

Los scripts viven en `~/opt/nidaria-art/tools` con sus entornos de
`~/opt/envs`; aquí se guarda una copia de lo que toca a la licencia.
