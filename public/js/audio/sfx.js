// Reproduce los efectos de sonido con Web Audio. Los sonidos se generan
// (ver sounds.js) en los ratos libres tras el primer toque, o al sonar si
// llegan antes, y se guardan para las siguientes veces.
//
// Los navegadores no dejan sonar nada hasta que el jugador toca la
// página, así que el audio se despierta con el primer toque. El ajuste es
// de este dispositivo y viene activado.

import { renderSound, SOUND_NAMES } from './sounds.js';

const KEY = 'nidaria:sonido';
const buffers = new Map();
let ctx = null;
let master = null;
let resuming = null;
let enabled = readPref();

function readPref() {
  try {
    return localStorage.getItem(KEY) !== 'off';
  } catch {
    return true;
  }
}

function bufferOf(name) {
  let buffer = buffers.get(name);
  if (!buffer) {
    const samples = renderSound(name, ctx.sampleRate);
    if (!samples) return null;
    buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate);
    buffer.copyToChannel(samples, 0);
    buffers.set(name, buffer);
  }
  return buffer;
}

// Genera los sonidos de uno en uno en los ratos libres, para que el
// primero de cada tipo no dé un tirón en mitad de una animación.
function prewarm(names = [...SOUND_NAMES]) {
  const idle = window.requestIdleCallback ?? ((fn) => setTimeout(fn, 60));
  const next = () => {
    const name = names.shift();
    if (!name) return;
    bufferOf(name);
    idle(next);
  };
  idle(next);
}

function wake() {
  if (!enabled) return;
  if (!ctx) {
    const Context = window.AudioContext ?? window.webkitAudioContext;
    if (!Context) return;
    ctx = new Context();
    master = ctx.createGain();
    master.gain.value = 0.9;
    master.connect(ctx.destination);
    prewarm();
  }
  if (ctx.state !== 'running' && !resuming) {
    resuming = ctx.resume().catch(() => {}).finally(() => { resuming = null; });
  }
}

// Safari cuenta como gesto el final del toque y no el principio: se
// escuchan los dos.
for (const type of ['pointerdown', 'pointerup', 'keydown']) {
  window.addEventListener(type, wake, { capture: true, passive: true });
}

export const soundOn = () => enabled;

export function setSound(on) {
  enabled = on;
  try {
    localStorage.setItem(KEY, on ? 'on' : 'off');
  } catch {
    // Sin almacenamiento, el ajuste dura lo que dure la visita.
  }
  if (on) {
    wake();
    sfx('toque');
  }
}

// sfx('monedas'), sfx('punto', { volume: 0.6 }), sfx('crac', { delay: 0.3 })
// Mientras el audio se está despertando (el primer toque), el sonido se
// programa igual y suena en cuanto despierta. Si está parado y nadie lo
// despierta, no se programa: si no, sonarían todos juntos más tarde.
export function sfx(name, { volume = 1, delay = 0 } = {}) {
  if (!enabled || !ctx || document.hidden) return;
  if (ctx.state !== 'running' && !resuming) return;
  const buffer = bufferOf(name);
  if (!buffer) return;
  const source = ctx.createBufferSource();
  source.buffer = buffer;
  if (volume === 1) {
    source.connect(master);
  } else {
    const gain = ctx.createGain();
    gain.gain.value = volume;
    source.connect(gain).connect(master);
  }
  source.start(ctx.currentTime + Math.max(0, delay));
}
