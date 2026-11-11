// Números pseudoaleatorios deterministas. El servidor y el navegador
// comparten este módulo, así que una misma semilla da siempre lo mismo.

export function hashString(text) {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function mix(a, b) {
  let h = (a ^ Math.imul(b + 0x9e3779b9, 0x85ebca6b)) >>> 0;
  h ^= h >>> 15;
  h = Math.imul(h, 0x2c1b3c6d);
  h ^= h >>> 12;
  h = Math.imul(h, 0x297a2d39);
  h ^= h >>> 15;
  return h >>> 0;
}

export function createRng(seed) {
  let a = seed >>> 0;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    range: (min, max) => min + (max - min) * next(),
    int: (min, max) => Math.floor(min + (max - min + 1) * next()),
    pick: (list) => list[Math.floor(next() * list.length)],
    chance: (p) => next() < p,
  };
}

// Tirada entre 0 y 1 ligada a una semilla y a un índice (p. ej. una hora
// concreta), sin estado: repetir la pregunta da la misma respuesta.
export function roll(seed, index) {
  return mix(seed >>> 0, index >>> 0) / 4294967296;
}

export const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
