// Miniaturas: dibuja un modelo 3D en un lienzo aparte y lo devuelve como
// imagen (una promesa con su dirección `data:`). Se guardan en memoria y,
// entre visitas, en IndexedDB; la clave lleva la huella del código de la
// escena, así que al cambiar un modelo las viejas dejan de valer solas.
// Se esculpen sin prisa: lo que pide la habitación pasa antes.

import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { createGenes } from '/shared/dna.js';
import { buildCreature } from './creature.js';
import { buildItem, buildEgg } from './props.js';
import { sculptingAs, whenSculpted } from './sculpt-pool.js';
import { fingerprint } from './sdf-core.js';
import { drawer } from './idb.js';
import { LOCAL } from './materials.js';

const SIZE = 192;
const memory = new Map();
const store = drawer('thumbs', 12 * 1024 * 1024);
let kit = null;

// Lo que decide cómo sale una miniatura.
const SOURCES = ['./sdf-core.js', './sdf.js', './creature.js', './plan.js', './props.js', './decor.js', './materials.js', './thumbs.js', '/shared/rng.js'];
let version = null;
function sceneVersion() {
  version ??= Promise.all(SOURCES.map(async (path) => {
    const response = await fetch(new URL(path, import.meta.url), { cache: 'force-cache' });
    if (!response.ok) throw new Error(path);
    return response.text();
  }))
    .then((texts) => fingerprint([THREE.REVISION, ...texts].join('\n')))
    .catch(() => null);
  return version;
}

function setup() {
  if (kit) return kit;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = SIZE;
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.debug.checkShaderErrors = LOCAL;
  renderer.setPixelRatio(1);
  renderer.setSize(SIZE, SIZE, false);
  renderer.setClearColor(0x000000, 0);
  renderer.toneMapping = THREE.NeutralToneMapping;
  renderer.toneMappingExposure = 1.05;
  const scene = new THREE.Scene();
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environmentIntensity = 0.45;
  pmrem.dispose();
  // La misma luz que la habitación: sol cálido arriba a la izquierda,
  // relleno de cielo y un contraluz frío que recorta el borde.
  const key = new THREE.DirectionalLight('#fff0dc', 2.9);
  key.position.set(-2.4, 4.5, 4);
  const rim = new THREE.DirectionalLight('#d6e8ff', 1.1);
  rim.position.set(3, 2.4, -2.6);
  scene.add(new THREE.HemisphereLight('#e8f3ff', '#f2d7b8', 0.7), key, rim);
  const camera = new THREE.PerspectiveCamera(24, 1, 0.1, 50);
  const flat = document.createElement('canvas');
  flat.width = flat.height = SIZE;
  const parallel = renderer.extensions.has('KHR_parallel_shader_compile');
  kit = { canvas, renderer, scene, camera, flat, ctx: flat.getContext('2d'), parallel };
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
  // El lienzo no está en la página: sin esperar a que la tarjeta gráfica
  // acabe, Chrome copia a veces un dibujo a medias (o vacío) a partir de
  // la segunda miniatura.
  renderer.getContext().finish();
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
    if (node.material && !node.material.userData?.shared && node.material.map?.isCanvasTexture !== true) node.material.dispose?.();
  });
}

function cached(key, make) {
  if (!memory.has(key)) memory.set(key, produce(key, make));
  return memory.get(key);
}

async function produce(key, make) {
  const stamp = await sceneVersion();
  const slot = stamp && `${stamp}:${key}`;
  if (slot) {
    const stored = await store.get(slot);
    if (typeof stored === 'string') return stored;
  }
  let url = '';
  try {
    url = (await make()) ?? '';
  } catch {
    url = '';
  }
  if (slot && url) store.put(slot, url, url.length * 2);
  return url;
}

// Espera a que el objeto esté esculpido y, donde se puede, a que sus
// programas de la tarjeta gráfica se compilen sin bloquear.
async function drawable(object) {
  await whenSculpted(object);
  const { renderer, camera, scene, parallel } = setup();
  if (parallel) await renderer.compileAsync(object, camera, scene).catch(() => {});
}

// Construye el modelo sin prisa, espera a que se pueda dibujar y lo dibuja.
// `persist`: si su malla merece guardarse (los artículos y los huevos
// salen también en la habitación; las criaturas de miniatura, no).
async function snap(build, view, { silhouette = false, persist = true } = {}) {
  const object = sculptingAs({ priority: 'low', persist }, build);
  if (!object) return '';
  try {
    await drawable(object);
    return render(object, view, silhouette);
  } finally {
    free(object);
  }
}

export function itemThumb(itemId) {
  return cached(`item:${itemId}`, () => snap(() => buildItem(itemId), [0.45, 0.42]));
}

export function eggThumb(egg) {
  return cached(`egg:${egg.hue}:${egg.sat}:${egg.light}:${egg.hue2}`, () => snap(() => buildEgg(egg), [0.2, 0.18]));
}

const CANON = { pelusa: createGenes(101, 'pelusa'), hoja: createGenes(202, 'hoja'), gota: createGenes(303, 'gota') };
export const canonGenes = (family) => CANON[family];

export function creatureThumb(formId, genes, silhouette = false) {
  if (formId === 'huevo') {
    if (!silhouette) return eggThumb(genes);
    return cached('egg:silhouette', () => snap(() => buildEgg(genes), [0.2, 0.18], { silhouette: true }));
  }
  return cached(`form:${formId}:${fingerprint(JSON.stringify(genes))}:${silhouette}`, async () => {
    const built = sculptingAs({ priority: 'low', persist: false }, () => buildCreature(formId, genes, { detail: silhouette ? 'tiny' : 'low' }));
    try {
      await drawable(built.object);
      return render(built.object, [0.28, 0.16], silhouette);
    } finally {
      built.dispose();
    }
  });
}

// Rellena imágenes según van saliendo, unas pocas a la vez para que lo
// primero de la lista llegue antes. Devuelve una función para parar.
export function lazyThumbs(jobs, together = 3) {
  let index = 0;
  let stopped = false;
  const next = () => {
    if (stopped || index >= jobs.length) return;
    const { img, make } = jobs[index++];
    Promise.resolve()
      .then(make)
      .then((url) => {
        if (!stopped && url) img.src = url;
      }, () => {})
      .finally(next);
  };
  for (let i = 0; i < together; i++) next();
  return () => { stopped = true; };
}

// Pone en `img` la miniatura cuando llegue (una promesa o una dirección).
export function showThumb(img, thumb) {
  Promise.resolve(thumb).then((url) => {
    if (url) img.src = url;
  }, () => {});
  return img;
}
