// Cajones de IndexedDB para guardar lo que cuesta hacer (las mallas
// esculpidas y las miniaturas) entre una visita y otra. Cada cajón tiene
// un tope de bytes: al pasarse, se tira lo que lleva más tiempo sin usar.
// Si el navegador no deja guardar (modo privado, sin espacio…), todo
// sigue funcionando: solo que sin guardar nada.
//
// No importa nada para que sirva también dentro de un Worker.

const NAME = 'nidaria-escena';
const VERSION = 1;
const DRAWERS = ['meshes', 'thumbs'];
// Al podar se baja hasta esta parte del tope, para no podar a cada rato.
const PRUNE_TO = 0.75;
// Se apunta un uso nuevo como mucho una vez por hora y entrada.
const TOUCH_EVERY = 60 * 60 * 1000;

let opening = null;

function promised(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function done(tx) {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

function open() {
  if (!opening) {
    opening = new Promise((resolve) => {
      try {
        const request = indexedDB.open(NAME, VERSION);
        request.onupgradeneeded = () => {
          const db = request.result;
          for (const drawer of DRAWERS) {
            if (!db.objectStoreNames.contains(drawer)) db.createObjectStore(drawer);
            // Lo que pesa cada entrada y cuándo se usó, aparte, para podar
            // sin tener que leer las entradas enteras.
            if (!db.objectStoreNames.contains(`${drawer}-info`)) db.createObjectStore(`${drawer}-info`);
          }
        };
        request.onsuccess = () => {
          const db = request.result;
          // Si otra pestaña necesita cambiar la base, esta se aparta.
          db.onversionchange = () => db.close();
          resolve(db);
        };
        request.onerror = () => resolve(null);
        request.onblocked = () => resolve(null);
      } catch {
        resolve(null);
      }
    });
  }
  return opening;
}

// Un cajón con tope de `maxBytes`. get y put nunca fallan: si algo va
// mal, get devuelve undefined y put no guarda.
export function drawer(name, maxBytes) {
  const info = `${name}-info`;
  let pruned = null;
  let added = 0;

  async function prune(db) {
    const tx = db.transaction(info, 'readonly');
    const store = tx.objectStore(info);
    const [keys, values] = await Promise.all([promised(store.getAllKeys()), promised(store.getAll())]);
    let total = values.reduce((sum, entry) => sum + (entry?.bytes ?? 0), 0);
    if (total <= maxBytes) return;
    const order = keys.map((key, i) => ({ key, used: values[i]?.used ?? 0, bytes: values[i]?.bytes ?? 0 })).sort((a, b) => a.used - b.used);
    const drop = [];
    for (const entry of order) {
      if (total <= maxBytes * PRUNE_TO) break;
      drop.push(entry.key);
      total -= entry.bytes;
    }
    const del = db.transaction([name, info], 'readwrite');
    for (const key of drop) {
      del.objectStore(name).delete(key);
      del.objectStore(info).delete(key);
    }
    await done(del);
  }

  async function ready() {
    const db = await open();
    if (db && !pruned) pruned = prune(db).catch(() => {});
    return db;
  }

  return {
    async get(key) {
      try {
        const db = await ready();
        if (!db) return undefined;
        const tx = db.transaction([name, info], 'readonly');
        const [value, meta] = await Promise.all([promised(tx.objectStore(name).get(key)), promised(tx.objectStore(info).get(key))]);
        if (value !== undefined && meta && Date.now() - meta.used > TOUCH_EVERY) {
          const touch = db.transaction(info, 'readwrite');
          touch.objectStore(info).put({ ...meta, used: Date.now() }, key);
        }
        return value;
      } catch {
        return undefined;
      }
    },

    async put(key, value, bytes) {
      try {
        const db = await ready();
        if (!db) return;
        await pruned;
        const tx = db.transaction([name, info], 'readwrite');
        tx.objectStore(name).put(value, key);
        tx.objectStore(info).put({ bytes, used: Date.now() }, key);
        await done(tx);
        // En una visita larga se vuelve a podar de vez en cuando.
        added += bytes;
        if (added > maxBytes * (1 - PRUNE_TO)) {
          added = 0;
          pruned = prune(db).catch(() => {});
        }
      } catch {
        // Sin espacio o sin permiso: se queda sin guardar.
      }
    },
  };
}
