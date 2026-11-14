// Al escondite: la criatura se mete bajo un cojín, los cojines se mezclan
// y hay que tocar el suyo. Solo lo juegan las criaturas juguetonas.

import { COLORS, fitCanvas, loop, createPops, pointerX } from './common.js';
import { sfx } from '../audio/sfx.js';

const easeInOut = (t) => (t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2);
const easeOut = (t) => 1 - (1 - t) ** 3;
const clamp01 = (t) => Math.min(1, Math.max(0, t));
const randInt = (n) => Math.floor(Math.random() * n);

// Tiempos de cada fase, en segundos.
const SHOW = { rise: 0.45, hold: 1.1, hide: 1.4, start: 1.65 };
const REVEAL = { found: 0.95, missed: 1.5 };

export async function create({ area, level, avatar, hud, onEnd }) {
  const canvas = document.createElement('canvas');
  area.append(canvas);
  const view = fitCanvas(canvas);
  const pops = createPops();
  const n = level.cushions;

  let score = 0;
  let lives = level.lives ?? 1;
  let round = 0;
  let running = false;
  let ended = false;
  // `slot[c]` es dónde está el cojín c (con decimales mientras se mueve).
  const slot = Array.from({ length: n }, (_, i) => i);
  const arc = new Array(n).fill(0);
  const lift = new Array(n).fill(0);
  let hidden = 0;
  let peek = 0;
  let phase = 'idle';
  let clock = 0;
  let swaps = [];
  let swapIndex = 0;
  // Los dos cojines del cambio en curso: se fijan al empezarlo, porque a
  // mitad de camino ya no se sabe por su sitio cuál es cuál.
  let moving = null;
  let swapTime = level.swapMs / 1000;
  let picked = -1;
  let message = 'Mira dónde se esconde';

  const paintHud = () => hud({ score, target: level.target, lives: (level.lives ?? 1) > 1 ? lives : undefined });
  paintHud();

  function finish() {
    if (ended) return;
    ended = true;
    running = false;
    setTimeout(() => onEnd(score), 600);
  }

  // Cada ronda se esconde en uno al azar y hay un cambio más, algo más rápido.
  function startRound() {
    hidden = randInt(n);
    lift.fill(0);
    peek = 0;
    picked = -1;
    swapTime = Math.max(level.swapMs * 0.6, level.swapMs * 0.94 ** round) / 1000;
    swaps = Array.from({ length: level.swaps + round }, () => {
      const a = randInt(n);
      let b = randInt(n - 1);
      if (b >= a) b += 1;
      return [a, b];
    });
    swapIndex = 0;
    moving = null;
    phase = 'show';
    clock = 0;
    message = 'Mira dónde se esconde';
  }

  const cushionAt = (place) => slot.findIndex((value) => Math.round(value) === place);

  function pick(cushion) {
    if (!running || phase !== 'pick' || cushion < 0) return;
    picked = cushion;
    phase = 'reveal';
    clock = 0;
    if (cushion === hidden) {
      score += 1;
      message = '¡La has encontrado!';
      sfx('punto');
    } else {
      lives -= 1;
      message = 'Ahí no estaba';
      sfx('fallo');
    }
    paintHud();
  }

  // Medidas que dependen del tamaño del lienzo.
  function layout() {
    const { w, h } = view;
    const spacing = Math.min(150, (w - 24) / n);
    const cw = spacing * 0.86;
    const ch = cw * 0.6;
    const ground = Math.round(h * 0.64);
    const size = Math.min(160, cw * 1.35);
    const xOf = (place) => w / 2 + (place - (n - 1) / 2) * spacing;
    return { w, h, spacing, cw, ch, ground, size, xOf };
  }

  const onPointer = (event) => {
    event.preventDefault();
    const { x, y } = pointerX(event, canvas);
    const { cw, ch, ground, xOf } = layout();
    if (y < ground - ch * 2.2 || y > ground + ch) return;
    let best = -1;
    let gap = cw * 0.62;
    for (let c = 0; c < n; c++) {
      const d = Math.abs(x - xOf(slot[c]));
      if (d < gap) {
        gap = d;
        best = c;
      }
    }
    pick(best);
  };
  const onKey = (event) => {
    const place = Number(event.key) - 1;
    if (Number.isInteger(place) && place >= 0 && place < n) pick(cushionAt(place));
  };
  canvas.addEventListener('pointerdown', onPointer);
  window.addEventListener('keydown', onKey);

  function update(dt) {
    clock += dt;
    if (phase === 'show') {
      if (clock < SHOW.rise) peek = easeOut(clock / SHOW.rise);
      else if (clock < SHOW.hold) peek = 1;
      else peek = 1 - clamp01((clock - SHOW.hold) / (SHOW.hide - SHOW.hold));
      if (clock >= SHOW.start) {
        phase = 'shuffle';
        clock = 0;
        message = '¡Que no se te escape!';
      }
    } else if (phase === 'shuffle') {
      const [a, b] = swaps[swapIndex];
      if (!moving) {
        moving = [cushionAt(a), cushionAt(b)];
        sfx('carta', { volume: 0.5 });
      }
      const [ca, cb] = moving;
      const t = clamp01(clock / swapTime);
      const k = easeInOut(t);
      slot[ca] = a + (b - a) * k;
      slot[cb] = b + (a - b) * k;
      arc[ca] = Math.sin(Math.PI * t);
      arc[cb] = -Math.sin(Math.PI * t) * 0.35;
      if (t >= 1) {
        slot[ca] = b;
        slot[cb] = a;
        arc[ca] = arc[cb] = 0;
        moving = null;
        swapIndex += 1;
        clock = 0;
        if (swapIndex >= swaps.length) {
          phase = 'pick';
          message = '¿Dónde está?';
        }
      }
    } else if (phase === 'reveal') {
      const found = picked === hidden;
      lift[picked] = easeOut(clamp01(clock / 0.25));
      if (!found) lift[hidden] = easeOut(clamp01((clock - 0.45) / 0.25));
      if (found && clock - dt < 0.2 && clock >= 0.2) {
        const { ground, ch, size, xOf } = layout();
        pops.add(xOf(slot[hidden]), ground - ch - size - 14, '+1');
      }
      if (clock >= (found ? REVEAL.found : REVEAL.missed)) {
        if (score >= level.target || lives <= 0) {
          phase = 'done';
          finish();
        } else {
          round += 1;
          startRound();
        }
      }
    }
  }

  function drawCushion(ctx, x, top, cw, ch) {
    ctx.fillStyle = COLORS.pinkEdge;
    ctx.beginPath();
    ctx.roundRect(x - cw / 2, top + 5, cw, ch, ch * 0.46);
    ctx.fill();
    ctx.fillStyle = COLORS.pink;
    ctx.beginPath();
    ctx.roundRect(x - cw / 2, top, cw, ch, ch * 0.46);
    ctx.fill();
    ctx.fillStyle = 'rgba(255, 253, 248, 0.35)';
    ctx.beginPath();
    ctx.ellipse(x - cw * 0.12, top + ch * 0.3, cw * 0.24, ch * 0.12, -0.08, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = COLORS.pinkEdge;
    ctx.beginPath();
    ctx.arc(x, top + ch * 0.52, ch * 0.08, 0, Math.PI * 2);
    ctx.fill();
  }

  const stop = loop((dt) => {
    if (running) update(dt);
    const { ctx } = view;
    const { w, h, cw, ch, ground, size, xOf } = layout();

    ctx.fillStyle = COLORS.sky;
    ctx.fillRect(0, 0, w, h);
    const floor = Math.round(h * 0.46);
    ctx.fillStyle = COLORS.cream;
    ctx.fillRect(0, floor, w, h - floor);
    ctx.fillStyle = COLORS.track;
    ctx.fillRect(0, floor, w, 6);
    ctx.fillStyle = COLORS.mint;
    ctx.beginPath();
    ctx.ellipse(w / 2, ground + 4, Math.min(w * 0.48, (n * cw) * 0.72), 26, 0, 0, Math.PI * 2);
    ctx.fill();

    ctx.font = '900 20px "Zen Maru Gothic", system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillStyle = COLORS.ink;
    if (phase !== 'idle') ctx.fillText(message, w / 2, Math.max(36, floor * 0.45));

    // Primero las sombras, luego la criatura (que asoma por detrás de su
    // cojín) y al final los cojines, los que van por delante los últimos.
    for (let c = 0; c < n; c++) {
      const x = xOf(slot[c]);
      ctx.fillStyle = 'rgba(91, 58, 41, 0.16)';
      ctx.beginPath();
      ctx.ellipse(x, ground + 4, cw * 0.46 * (1 - lift[c] * 0.3), 8, 0, 0, Math.PI * 2);
      ctx.fill();
    }
    // Escondida, solo asoma por encima de la mitad de su cojín; con el
    // cojín levantado se la ve entera, sentada en el suelo.
    const out = lift[hidden] > 0.02;
    if (avatar && (out || peek > 0)) {
      const x = xOf(slot[hidden]);
      const edge = out ? ground + 2 : ground - ch * 0.5;
      const top = out ? ground - size * 0.9 : edge - size * 0.92 * peek;
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, w, edge);
      ctx.clip();
      ctx.drawImage(avatar, x - size / 2, top, size, size);
      ctx.restore();
    }
    const order = [...Array(n).keys()].sort((a, b) => arc[b] - arc[a]);
    for (const c of order) {
      const x = xOf(slot[c]);
      const top = ground - ch - arc[c] * ch * 0.9 - lift[c] * size;
      drawCushion(ctx, x, top, cw, ch);
    }
    pops.draw(ctx, dt);
  });

  return {
    start() {
      running = true;
      startRound();
    },
    destroy() {
      stop();
      view.stop();
      canvas.removeEventListener('pointerdown', onPointer);
      window.removeEventListener('keydown', onKey);
      canvas.remove();
    },
  };
}
