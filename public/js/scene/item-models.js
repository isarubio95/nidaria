// Muebles de modelo (.glb hechos fuera: boceto con FLUX.2, forma y color
// con TRELLIS.2 y textura horneada en Blender; ver art/props/README.md) en
// vez de esculpidos. buildItemModel devuelve null para los artículos que
// aún no tienen modelo, y entonces se esculpen como siempre (props.js).
//
// El objeto se devuelve en el acto y la malla llega al descargarse: como
// las esculpidas, lleva la promesa en `geometry.userData.sculpting` y nadie
// lo enseña antes (ver whenSculpted).

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

// Súbelo al cambiar los .glb: forma parte de la huella de las miniaturas.
export const ITEM_MODELS_VERSION = 1;
const ITEMS = new Set(['planta', 'cactus', 'girasol', 'lampara', 'pecera', 'cojin', 'mesita', 'baul', 'camita']);

const loader = new GLTFLoader();
const sources = new Map();

function source(id) {
  if (!sources.has(id)) {
    sources.set(id, loader.loadAsync(`/models/items/${id}.glb?v=${ITEM_MODELS_VERSION}`).then((gltf) => {
      gltf.scene.traverse((node) => {
        if (!node.isMesh) return;
        // Mismo acabado mate que el resto de la habitación.
        const map = node.material.map;
        if (map) map.anisotropy = 4;
        node.material = new THREE.MeshStandardMaterial({ map, roughness: 0.78, metalness: 0 });
        node.material.userData.shared = true;
        node.geometry.userData.shared = true;
      });
      return gltf.scene;
    }));
  }
  return sources.get(id);
}

export const hasItemModel = (id) => ITEMS.has(id);

export function buildItemModel(id) {
  if (!ITEMS.has(id)) return null;
  const group = new THREE.Group();
  group.name = id;
  // Marcador vacío que lleva la espera, como una malla esculpida a medias.
  const pending = new THREE.Mesh(new THREE.BufferGeometry(), new THREE.MeshBasicMaterial({ visible: false }));
  pending.geometry.userData.sculpting = source(id).then((scene) => {
    const copy = scene.clone(true);
    copy.traverse((node) => {
      if (node.isMesh) {
        node.castShadow = true;
        node.receiveShadow = true;
      }
    });
    group.add(copy);
    group.remove(pending);
  });
  group.add(pending);
  return group;
}
