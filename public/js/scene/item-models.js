// Artículos de modelo (.glb hechos fuera: boceto con FLUX.2, forma y color
// con TRELLIS.2 y textura horneada en Blender; ver art/props/README.md) en
// vez de esculpidos: muebles, comida, cuidados y juguetes. buildItemModel
// devuelve null para los artículos que aún no tienen modelo, y entonces se
// esculpen como siempre (props.js).
//
// El objeto se devuelve en el acto y la malla llega al descargarse: como
// las esculpidas, lleva la promesa en `geometry.userData.sculpting` y nadie
// lo enseña antes (ver whenSculpted).

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

// Súbelo al cambiar los .glb: forma parte de la huella de las miniaturas.
export const ITEM_MODELS_VERSION = 5;
// Cada artículo con su rugosidad: la habitación es mate; la fruta, el
// helado, los frascos y los juguetes de plástico brillan un poco.
const ITEMS = new Map([
  ['planta', 0.78], ['cactus', 0.78], ['girasol', 0.78], ['lampara', 0.78], ['pecera', 0.78],
  ['cojin', 0.78], ['mesita', 0.78], ['baul', 0.78], ['camita', 0.78],
  ['manzana', 0.42], ['zanahoria', 0.7], ['pan', 0.85], ['arroz', 0.6], ['sopa', 0.55],
  ['pescado', 0.6], ['festin', 0.6],
  ['galleta', 0.85], ['helado', 0.5], ['tarta', 0.65],
  ['medicina', 0.4], ['vitaminas', 0.35], ['jabon', 0.55],
  ['pelota', 0.4], ['peonza', 0.5], ['peluche', 0.9], ['cometa', 0.75],
]);

const loader = new GLTFLoader();
const sources = new Map();

function source(id) {
  if (!sources.has(id)) {
    sources.set(id, loader.loadAsync(`/models/items/${id}.glb?v=${ITEM_MODELS_VERSION}`).then((gltf) => {
      gltf.scene.traverse((node) => {
        if (!node.isMesh) return;
        // Por las dos caras: las mallas de TRELLIS tienen zonas giradas o
        // abiertas que, por una sola cara, se verían como agujeros.
        const map = node.material.map;
        if (map) map.anisotropy = 4;
        node.material = new THREE.MeshStandardMaterial({ map, roughness: ITEMS.get(id), metalness: 0, side: THREE.DoubleSide });
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
