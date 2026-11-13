// Esculpe en segundo plano. Recibe encargos (ver sdf-core.js) y devuelve
// las listas de la malla. Las guarda en IndexedDB para no repetirlas en
// la siguiente visita.

import { meshJob, meshBytes, fingerprint } from './sdf-core.js';
import { drawer } from './idb.js';

const store = drawer('meshes', 96 * 1024 * 1024);

// La huella del propio núcleo forma parte de la clave: si cambia cómo se
// calcula la malla, las guardadas dejan de valer solas. Sin huella no se
// guarda nada.
const core = fetch(new URL('./sdf-core.js', import.meta.url), { cache: 'force-cache' })
  .then((response) => (response.ok ? response.text() : null))
  .then((text) => (text ? fingerprint(text) : null))
  .catch(() => null);

const buffers = (mesh) => (mesh ? Object.values(mesh).map((array) => array.buffer) : []);

self.onmessage = async ({ data: { id, key, job, persist } }) => {
  try {
    const version = persist ? await core : null;
    const slot = version && `${version}:${key}`;
    const stored = slot ? await store.get(slot) : undefined;
    if (stored !== undefined) {
      self.postMessage({ id, mesh: stored, stored: true }, buffers(stored));
      return;
    }
    const mesh = meshJob(job);
    if (!slot) {
      self.postMessage({ id, mesh }, buffers(mesh));
      return;
    }
    // Se manda una copia (es rápido) para poder guardar el original.
    self.postMessage({ id, mesh });
    if (mesh) store.put(slot, mesh, meshBytes(mesh));
  } catch (error) {
    self.postMessage({ id, error: String(error?.message ?? error) });
  }
};
