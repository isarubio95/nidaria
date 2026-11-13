// Reparte los encargos de escultura (ver sdf-core.js) entre unos Workers
// para que esculpir no congele la pantalla. Cada encargo se resume en una
// clave: lo ya esculpido se reaprovecha de memoria y, entre visitas, de
// IndexedDB (lo guarda el Worker).
//
// `geometryFor` devuelve la geometría en el acto. Si hay que esculpirla,
// llega vacía y se rellena sola al terminar; mientras tanto lleva en
// `userData.sculpting` la promesa de que estará lista. `whenSculpted`
// espera a todas las de un objeto.
//
// Sin Workers (o si fallan) se esculpe aquí mismo, como antes.

import * as THREE from 'three';
import { meshJob, meshBytes, fingerprint } from './sdf-core.js';

const MEMORY_BYTES = 48 * 1024 * 1024;

// ── Memoria ───────────────────────────────────────────────────

const memory = new Map();
let memoryBytes = 0;
const bounds = new WeakMap();

function recall(key) {
  if (!memory.has(key)) return undefined;
  const mesh = memory.get(key);
  memory.delete(key);
  memory.set(key, mesh);
  return mesh;
}

function remember(key, mesh) {
  if (memory.has(key)) return;
  memory.set(key, mesh);
  memoryBytes += meshBytes(mesh);
  for (const [oldKey, old] of memory) {
    if (memoryBytes <= MEMORY_BYTES) break;
    memory.delete(oldKey);
    memoryBytes -= meshBytes(old);
  }
}

// Mete la malla en la geometría. Varias geometrías pueden compartir las
// mismas listas: cada una sube su copia a la tarjeta gráfica.
function fill(geometry, mesh) {
  if (!mesh) return geometry;
  geometry.setAttribute('position', new THREE.BufferAttribute(mesh.position, 3));
  geometry.setAttribute('normal', new THREE.BufferAttribute(mesh.normal, 3));
  geometry.setAttribute('color', new THREE.BufferAttribute(mesh.color, 3));
  geometry.setAttribute('ao', new THREE.BufferAttribute(mesh.ao, 1));
  if (mesh.skinIndex) {
    geometry.setAttribute('skinIndex', new THREE.BufferAttribute(mesh.skinIndex, 4));
    geometry.setAttribute('skinWeight', new THREE.BufferAttribute(mesh.skinWeight, 4));
  }
  geometry.setIndex(new THREE.BufferAttribute(mesh.index, 1));
  if (!bounds.has(mesh)) {
    geometry.computeBoundingSphere();
    geometry.computeBoundingBox();
    bounds.set(mesh, { sphere: geometry.boundingSphere.clone(), box: geometry.boundingBox.clone() });
  } else {
    const { sphere, box } = bounds.get(mesh);
    geometry.boundingSphere = sphere.clone();
    geometry.boundingBox = box.clone();
  }
  return geometry;
}

// ── Workers ───────────────────────────────────────────────────

let pool;
const queues = { high: [], low: [] };
const pending = new Map();
let nextId = 1;

function startPool() {
  if (typeof Worker === 'undefined' || typeof window === 'undefined') return null;
  const size = Math.max(1, Math.min(3, (navigator.hardwareConcurrency || 2) - 1));
  const slots = [];
  try {
    for (let i = 0; i < size; i++) {
      const slot = { worker: new Worker(new URL('./sculpt-worker.js', import.meta.url), { type: 'module' }), request: null };
      slot.worker.onmessage = ({ data }) => finished(slot, data);
      slot.worker.onerror = (event) => {
        event.preventDefault?.();
        giveUp();
      };
      slots.push(slot);
    }
  } catch {
    for (const slot of slots) slot.worker.terminate();
    return null;
  }
  return slots;
}

// Si los Workers no arrancan, lo que quede se esculpe aquí.
function giveUp() {
  if (!pool) return;
  const stranded = [...pool.map((slot) => slot.request).filter(Boolean), ...queues.high.splice(0), ...queues.low.splice(0)];
  for (const slot of pool) slot.worker.terminate();
  pool = null;
  for (const request of stranded) request.resolve(meshJob(request.job));
}

function dispatch() {
  if (!pool) return;
  for (const slot of pool) {
    if (slot.request) continue;
    const request = queues.high.shift() ?? queues.low.shift();
    if (!request) return;
    request.queue = null;
    request.id = nextId++;
    slot.request = request;
    slot.worker.postMessage({ id: request.id, key: request.key, job: request.job, persist: request.persist });
  }
}

function finished(slot, data) {
  const request = slot.request;
  slot.request = null;
  if (request && request.id === data.id) {
    // Si el Worker no pudo, se hace aquí.
    request.resolve(data.error ? meshJob(request.job) : data.mesh);
  }
  dispatch();
}

function ask(key, job, priority, persist) {
  const known = pending.get(key);
  if (known) {
    // Si ahora lo quiere la habitación, se adelanta en la cola.
    if (priority === 'high' && known.request.queue === 'low') {
      queues.low.splice(queues.low.indexOf(known.request), 1);
      queues.high.push(known.request);
      known.request.queue = 'high';
    }
    known.request.persist ||= persist;
    return known.promise;
  }
  const request = { key, job, persist, queue: priority };
  const promise = new Promise((resolve) => { request.resolve = resolve; })
    .finally(() => pending.delete(key));
  pending.set(key, { request, promise });
  queues[priority].push(request);
  dispatch();
  return promise;
}

// ── Lo que usan los demás ─────────────────────────────────────

let context = { priority: 'high', persist: true };

// Esculpe lo que pida `fn` con otras condiciones: `priority` 'low' deja
// pasar antes a lo de la habitación; `persist` false no lo guarda (lo
// que solo sirve para hacer una miniatura, que ya se guarda aparte).
export function sculptingAs(options, fn) {
  const previous = context;
  context = { ...previous, ...options };
  try {
    return fn();
  } finally {
    context = previous;
  }
}

export function geometryFor(job) {
  const key = fingerprint(JSON.stringify(job));
  const known = recall(key);
  if (known !== undefined) return fill(new THREE.BufferGeometry(), known);
  if (pool === undefined) pool = startPool();
  const { priority, persist } = context;
  if (!pool) {
    const mesh = meshJob(job);
    if (persist) remember(key, mesh);
    return fill(new THREE.BufferGeometry(), mesh);
  }
  const geometry = new THREE.BufferGeometry();
  geometry.userData.sculpting = ask(key, job, priority, persist).then((mesh) => {
    if (persist) remember(key, mesh);
    fill(geometry, mesh);
    delete geometry.userData.sculpting;
  });
  return geometry;
}

// Promesa de que todas las geometrías de los objetos están esculpidas, o
// null si ya lo están (así se puede seguir sin esperar a nada).
export function whenSculpted(...objects) {
  const waits = [];
  for (const object of objects) {
    object?.traverse?.((node) => {
      const wait = node.geometry?.userData.sculpting;
      if (wait) waits.push(wait);
    });
  }
  return waits.length ? Promise.all(waits).then(() => {}) : null;
}
