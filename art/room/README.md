# Habitación horneada

La habitación del juego (`public/rooms/`) se construye y se ilumina en Blender
y el juego solo la pinta (`public/js/scene/baked-house.js`).

```sh
# 1. Construir y hornear (Blender 5.2, Cycles en la GPU; ~40 s)
systemd-run --user --unit=nidaria-room-bake -p MemoryMax=12G -p MemorySwapMax=0 \
  blender -b -P art/room/build_room.py -- --out <horneado> --samples 512 [--preview]

# 2. Empaquetar para la web
python3 art/room/pack_lightmaps.py <horneado> public/rooms \
  --floor-texture laminate_floor_02_diff_2k.jpg --floor-ao laminate_floor_02_ao_2k.jpg
```

- La luz se hornea en tres canales (sol, cielo y lámpara) y se guarda sin
  color. El juego la multiplica por el papel pintado que toque y mezcla los
  canales según la hora, el tiempo y si la criatura duerme.
- `--side` y `--fill` cambian la fuerza del ventanal de fuera de cuadro y del
  rebote de detrás de la cámara (canal del cielo).
- Las medidas de la pared y la ventana son las de `public/js/scene/house.js`;
  si cambian allí, hay que cambiarlas en `build_room.py`.
- Tarima: «Laminate Floor 02» de Poly Haven (CC0), descargada de
  https://polyhaven.com/a/laminate_floor_02.
