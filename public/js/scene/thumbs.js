// Miniaturas: dibuja un modelo 3D en un lienzo aparte y lo devuelve como
// imagen. Se guardan en memoria para no repetir el trabajo.

import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { createGenes } from '/shared/dna.js';
import { buildCreature } from './creature.js';
import { buildItem, buildEgg } from './props.js';

const SIZE = 192;
const cache = new Map();
let kit = null;

function setup() {
  if (kit) return kit;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = SIZE;
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.setSize(SIZE, SIZE, false);
  renderer.setClearColor(0x000000, 0);
  const scene = new THREE.Scene();
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environmentIntensity = 0.5;
  pmrem.dispose();
  const key = new THREE.DirectionalLight('#fff4e2', 2.1);
  key.position.set(-2.4, 4.5, 4);
  scene.add(new THREE.HemisphereLight('#ffffff', '#ffe6cc', 1.05), key);
  const camera = new THREE.PerspectiveCamera(24, 1, 0.1, 50);
  const flat = document.createElement('canvas');
  flat.width = flat.height = SIZE;
  kit = { canvas, renderer, scene, camera, flat, ctx: flat.getContext('2d') };
  return kit;
}

function render(object, view, silhouette) {
  const { canvas, renderer, scene, camera, flat, ctx } = setup();
  scene.add(object);
  const box = new THREE.Box3().setFromObject(object);
  const center = box.getCenter(new THREE.Vector3());
  const size = box.getSize(new THREE.Vector3());
  const radius = Math.max(size.x, size.y, size.z * 0.8) / 2;
  const distance = (radius / Math.tan(THREE.MathUtils.degToRad(12))) * 1.12;
  camera.position.copy(center).add(new THREE.Vector3(view[0], view[1], 1).normalize().multiplyScalar(distance));
  camera.lookAt(center);
  renderer.render(scene, camera);
  scene.remove(object);
  ctx.globalCompositeOperation = 'source-over';
  ctx.clearRect(0, 0, SIZE, SIZE);
  ctx.drawImage(canvas, 0, 0);
  if (silhouette) {
    ctx.globalCompositeOperation = 'source-in';
    ctx.fillStyle = '#b89f8c';
    ctx.fillRect(0, 0, SIZE, SIZE);
  }
  return flat.toDataURL('image/png');
}

function free(object) {
  object.traverse((node) => {
    if (node.geometry && !node.geometry.userData.shared) node.geometry.dispose();
  });
}

function cached(key, make) {
  if (!cache.has(key)) {
    try {
      cache.set(key, make());
    } catch {
      cache.set(key, '');
    }
  }
  return cache.get(key);
}

export function itemThumb(itemId) {
  return cached(`item:${itemId}`, () => {
    const object = buildItem(itemId);
    if (!object) return '';
    const url = render(object, [0.45, 0.42]);
    free(object);
    return url;
  });
}

export function eggThumb(egg) {
  return cached(`egg:${egg.hue}:${egg.sat}:${egg.light}:${egg.hue2}`, () => {
    const object = buildEgg(egg);
    const url = render(object, [0.2, 0.18]);
    free(object);
    return url;
  });
}

const CANON = { pelusa: createGenes(101, 'pelusa'), hoja: createGenes(202, 'hoja'), gota: createGenes(303, 'gota') };
export const canonGenes = (family) => CANON[family];

export function creatureThumb(formId, genes, silhouette = false) {
  if (formId === 'huevo') {
    if (!silhouette) return eggThumb(genes);
    return cached('egg:silhouette', () => {
      const object = buildEgg(genes);
      const url = render(object, [0.2, 0.18], true);
      free(object);
      return url;
    });
  }
  return cached(`form:${formId}:${genes.seed}:${genes.hue}:${silhouette}`, () => {
    const built = buildCreature(formId, genes);
    const url = render(built.object, [0.28, 0.16], silhouette);
    built.dispose();
    return url;
  });
}

// Rellena imágenes poco a poco para no bloquear la interfaz.
export function lazyThumbs(jobs, perFrame = 4) {
  let index = 0;
  let stopped = false;
  const step = () => {
    if (stopped) return;
    for (let n = 0; n < perFrame && index < jobs.length; n++, index++) {
      const { img, make } = jobs[index];
      const url = make();
      if (url) img.src = url;
    }
    if (index < jobs.length) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
  return () => { stopped = true; };
}
