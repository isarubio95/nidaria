// El zoom de toda la pantalla. La interfaz se encoge dentro de #ui y la
// habitación se aleja con su cámara, las dos con el mismo factor. Se
// cambia pellizcando, con Ctrl + rueda (o la rueda sobre la habitación) y
// con Ctrl + − / + / 0. No se enseña en ningún sitio y se recuerda en este
// dispositivo. El tamaño de siempre es el máximo: solo se puede alejar.

import { clamp } from '/shared/rng.js';

export const ZOOM_MIN = 0.8;
export const ZOOM_MAX = 1;
export const ZOOM_DEFAULT = 0.95;

const KEY = 'nidaria:zoom';
const STEP = 0.05;
const watchers = new Set();

function stored() {
  try {
    const value = Number(localStorage.getItem(KEY));
    return value > 0 ? clamp(value, ZOOM_MIN, ZOOM_MAX) : ZOOM_DEFAULT;
  } catch {
    return ZOOM_DEFAULT;
  }
}

let level = stored();
document.documentElement.style.setProperty('--zoom', String(level));

export const zoomLevel = () => level;

// `fn(level)` cada vez que cambia.
export function onZoom(fn) {
  watchers.add(fn);
  return () => watchers.delete(fn);
}

let saveTimer = 0;
function setZoom(value) {
  const next = Math.round(clamp(value, ZOOM_MIN, ZOOM_MAX) * 1000) / 1000;
  if (next === level) return;
  level = next;
  document.documentElement.style.setProperty('--zoom', String(level));
  for (const fn of watchers) fn(level);
  // Para la interfaz es como si la ventana cambiara de tamaño: lo que se
  // recoloca al redimensionar (el encuadre, el bocadillo) se recoloca.
  window.dispatchEvent(new Event('resize'));
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try {
      localStorage.setItem(KEY, String(level));
    } catch {
      // Sin almacenamiento, el zoom dura lo que la visita.
    }
  }, 300);
}

// ── Pellizco ──
// Con dos dedos en cualquier sitio menos en un minijuego, donde los dos
// dedos son para jugar. Hasta que se separan o se juntan un poco no cuenta
// como pellizco, para que un toque con dos dedos no mueva nada.
let pinch = null;
const spread = (touches) => Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY);

window.addEventListener('touchstart', (event) => {
  if (event.touches.length !== 2) {
    pinch = null;
    return;
  }
  const inGame = [...event.touches].some((touch) => touch.target instanceof Element && touch.target.closest('.game'));
  pinch = inGame ? null : { from: spread(event.touches), base: level, active: false };
}, { passive: true });

window.addEventListener('touchmove', (event) => {
  if (!pinch || event.touches.length !== 2) return;
  // El navegador no debe hacer su propio zoom ni desplazar nada a la vez.
  if (event.cancelable) event.preventDefault();
  const now = spread(event.touches);
  if (!pinch.active) {
    if (Math.abs(now - pinch.from) < 12) return;
    pinch.active = true;
    pinch.from = now;
    pinch.base = level;
  }
  setZoom(pinch.base * (now / pinch.from));
}, { passive: false });

const endPinch = (event) => {
  if (event.touches.length < 2) pinch = null;
};
window.addEventListener('touchend', endPinch, { passive: true });
window.addEventListener('touchcancel', endPinch, { passive: true });

// En Safari el pellizco llega también como gesto, y si no se frena hace
// zoom de la página entera.
for (const name of ['gesturestart', 'gesturechange', 'gestureend']) {
  window.addEventListener(name, (event) => event.preventDefault(), { passive: false });
}

// ── Ordenador ──
// Ctrl + rueda es también lo que manda el pellizco del panel táctil; con
// él, el zoom sigue a los dedos. Una vuelta de rueda mueve un poco.
window.addEventListener('wheel', (event) => {
  const overRoom = event.target instanceof Element && event.target.id === 'stage';
  if (!event.ctrlKey && !overRoom) return;
  event.preventDefault();
  const delta = event.deltaY * (event.deltaMode === 1 ? 33 : event.deltaMode === 2 ? 400 : 1);
  setZoom(level * Math.exp(-clamp(delta, -8, 8) / 100));
}, { passive: false });

window.addEventListener('keydown', (event) => {
  if (!(event.ctrlKey || event.metaKey) || event.altKey) return;
  if (event.key === '+' || event.key === '=') setZoom(level + STEP);
  else if (event.key === '-' || event.key === '_') setZoom(level - STEP);
  else if (event.key === '0') setZoom(ZOOM_DEFAULT);
  else return;
  event.preventDefault();
});
