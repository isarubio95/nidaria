# Artículos de modelo

Los muebles, la comida, los cuidados y los juguetes de la tienda
(`public/models/items/*.glb`) se hacen en local con la misma cadena que las
criaturas, todo con licencias que permiten uso comercial:

1. **Boceto** con FLUX.2 [klein] 4B (Apache 2.0): `concept_props.py` con los
   textos de `prompts.json`, cuatro semillas por mueble. Se elige uno a ojo.
2. **Forma y color** con TRELLIS.2 (MIT): `prop_glb.py` deja una malla fina
   (unas 300 000 caras) con el color en los vértices. No usa nvdiffrast, que
   solo admite uso de investigación: el color se toma del volumen de
   TRELLIS con `grid_sample_3d` en los vértices.
   Donde el volumen casi no tiene vóxeles, el color se toma de los vecinos
   buenos (si no, salen manchas). `prop_glb.py jobs.json recolor` lo rehace
   desde el `.pt` guardado, sin volver a generar.
3. **Listo para el juego** con Blender: `prop_bake.py` la reduce a ~10 000
   caras (6 000 en los artículos pequeños), la despliega, hornea la textura
   (512 px) desde la malla fina y la deja de pie, centrada, de frente y con
   su tamaño en metros.
4. **Más ligero** con `pack_glb.py` (solo numpy): posiciones, normales y UV
   en enteros (KHR_mesh_quantization) en vez de floats. Pesa un 40 % menos
   y se comprime mejor; el error es de centésimas de milímetro. Three.js lo
   lee de serie y Android (`Glb.parseStatic`) también.

Estos scripts viven en `~/opt/nidaria-art/tools` (junto a `img2glb.py`, del
que `prop_glb.py` toma los cambios de licencia de TRELLIS) con sus entornos
de `~/opt/envs`; aquí se guarda una copia para saber cómo se hicieron.

```sh
cd ~/opt/nidaria-art
# 1. Bocetos (FLUX necesita ~20 GB de RAM)
systemd-run --user --wait -p MemoryMax=20G -p MemorySwapMax=0 \
  ~/opt/envs/concept/bin/python tools/concept_props.py props/prompts.json props/concepts 1,2,3,4
# 2. TRELLIS: jobs.json = [["props/concepts/planta-s1.png", "props/gen/planta"], ...]
systemd-run --user --wait -p MemoryMax=20G -p MemorySwapMax=0 \
  ~/opt/envs/trellis2/bin/python tools/prop_glb.py props/jobs.json 1024_cascade 42
# 3. Blender, uno por mueble
blender -b -P tools/prop_bake.py -- props/gen/planta-high.ply planta.glb --height 0.75 --tris 10000 --tex 512
# 4. Cuantizar
python3 ~/Escritorio/nidaria/art/props/pack_glb.py planta.glb ~/Escritorio/nidaria/public/models/items/planta.glb
```

Bocetos elegidos (semilla): planta 1, cactus 2, girasol 1, lámpara 1, pecera 1,
cojín 1, mesita 2, baúl 2 (TRELLIS a 512: a 1024 no cabía en la tarjeta),
camita 1. Medidas: planta 0,75 m de alto, cactus 1,0, girasol 1,35, lámpara
1,5, pecera 1,05, mesita 1,0, baúl 0,78; cojín 0,84 m y camita 0,9 m de ancho.

Comida, cuidados y juguetes (`prompts-2.json`, semilla de TRELLIS 42, 6 000
caras; el arroz, 10 000): manzana s2, zanahoria s1, pan s2, arroz s2, sopa
s1, pescado s1, galleta s2, helado s1, tarta s2, festín s2 (el texto de la
tabla de madera), medicina s1, vitaminas s1, jabón s1, pelota s2, peonza s1,
peluche s1, cometa s3. Miden lo mismo que los esculpidos a los que
sustituyen (la escena los escala con un factor fijo): ver `sizes-2.txt`.

TRELLIS pierde las bases planas y finas (platos, tablas): el pescado y el
festín salen sin plato aunque el boceto lo tenga, sea blanco o de color.
Lo que no cabe a 1024 en la tarjeta (cosas muy peludas o con mucho
volumen vacío) se hace a 512, que sale peor.

Las mallas de TRELLIS tienen zonas abiertas o giradas: el juego dibuja los
artículos por las dos caras.

Para añadir uno: su `.glb` cuantizado en `public/models/items/`, su id y
su rugosidad en `public/js/scene/item-models.js` y en
`android/.../scene/ItemModels.kt`, y sube `ITEM_MODELS_VERSION` si cambias
uno que ya existía.
