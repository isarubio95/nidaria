#!/bin/bash
# Horneado y empaquetado de los artículos nuevos: bake2.sh lista.txt
# lista.txt: "id --height H" o "id --width W" por línea.
set -u
cd ~/opt/nidaria-art
mkdir -p props/web2 props/prev2
while read -r id size value; do
  [ -z "$id" ] && continue
  log=$HOME/opt/nidaria-art/props/bake2-$id.log
  rm -f "$log"
  systemd-run --user --wait --collect -q -p MemoryMax=8G -p MemorySwapMax=0 -p Nice=10 \
    --working-directory=$HOME/opt/nidaria-art -p StandardOutput=file:$log -p StandardError=file:$log \
    $HOME/.local/bin/blender -b -P tools/prop_bake.py -- props/gen2/$id-high.ply props/web2/$id.glb $size $value --tris ${TRIS:-6000} --tex 512 --preview props/prev2/$id.png
  echo "== $id: $(grep -E '^OK|Error|Traceback' $log | cut -c1-160 | tr '\n' ' ')"
  python3 /home/isaias/Escritorio/nidaria/art/props/pack_glb.py props/web2/$id.glb props/web2/$id-packed.glb | sed 's/^/   /'
done < "$1"
echo FIN
