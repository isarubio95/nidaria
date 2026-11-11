// Explota burbujas: toca las de color antes de que se vayan.

import { COLORS, fitCanvas, loop, pointerX, rand, createPops } from './common.js';

const PALETTE = [
  [COLORS.pink, COLORS.pinkEdge], [COLORS.mint, COLORS.mintEdge], [COLORS.sun, COLORS.sunEdge],
  [COLORS.blue, COLORS.blueEdge], [COLORS.coral, COLORS.coralEdge],
];

export async function create({ area, level, hud, onEnd }) {
  const canvas = document.createElement('canvas');
  area.append(canvas);
  const view = fitCanvas(canvas);
  const pops = createPops();

  let score = 0;
  let time = level.seconds;
  let spawnIn = 0.3;
  let running = false;
  let ended = false;
  const bubbles = [];
  const bursts = [];

  const paintHud = () => hud({ score, target: level.target, time: Math.ceil(time) });
  paintHud();

  canvas.addEventListener('pointerdown', (event) => {
    if (!running) return;
    const { x, y } = pointerX(event, canvas);
    for (let i = bubbles.length - 1; i >= 0; i--) {
      const bubble = bubbles[i];
      if (Math.hypot(x - bubble.x, y - bubble.y) > bubble.r + 8) continue;
      bubbles.splice(i, 1);
      bursts.push({ x: bubble.x, y: bubble.y, r: bubble.r, color: bubble.color, life: 0.25 });
      if (bubble.grey) {
        score = Math.max(0, score - 2);
        pops.add(bubble.x, bubble.y - bubble.r, '−2', COLORS.coralEdge);
      } else {
        score += 1;
        pops.add(bubble.x, bubble.y - bubble.r, '+1');
      }
      paintHud();
      return;
    }
  });

  function finish() {
    if (ended) return;
    ended = true;
    running = false;
    onEnd(score);
  }

  const stop = loop((dt, t) => {
    const { ctx, w, h } = view;
    if (running) {
      time -= dt;
      spawnIn -= dt;
      if (spawnIn <= 0) {
        spawnIn = (level.every / 1000) * rand(0.8, 1.2);
        const r = rand(30, 42);
        let spot = null;
        for (let tries = 0; tries < 12 && !spot; tries++) {
          const candidate = { x: rand(r + 10, w - r - 10), y: rand(r + 10, h - r - 24) };
          if (bubbles.every((other) => Math.hypot(other.x - candidate.x, other.y - candidate.y) > other.r + r + 6)) spot = candidate;
        }
        if (spot) {
          const grey = Math.random() < level.grey;
          const [color, edge] = grey ? [COLORS.grey, COLORS.greyEdge] : PALETTE[Math.floor(Math.random() * PALETTE.length)];
          bubbles.push({ ...spot, r, grey, color, edge, age: 0, life: (level.life / 1000) * (grey ? 1.2 : 1), phase: rand(0, 6) });
        }
      }
      if (time <= 0) {
        time = 0;
        finish();
      }
      paintHud();
    }

    ctx.fillStyle = COLORS.sky;
    ctx.fillRect(0, 0, w, h);

    for (let i = bubbles.length - 1; i >= 0; i--) {
      const bubble = bubbles[i];
      if (running) bubble.age += dt;
      if (bubble.age >= bubble.life) {
        bubbles.splice(i, 1);
        continue;
      }
      const grow = Math.min(1, bubble.age / 0.14);
      const fade = Math.min(1, (bubble.life - bubble.age) / 0.25);
      const r = bubble.r * (0.6 + 0.4 * grow) * (0.85 + 0.15 * fade);
      const y = bubble.y + Math.sin(t * 2 + bubble.phase) * 4;
      ctx.globalAlpha = fade;
      ctx.fillStyle = bubble.edge;
      ctx.beginPath();
      ctx.arc(bubble.x, y + 3, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = bubble.color;
      ctx.beginPath();
      ctx.arc(bubble.x, y, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
      ctx.beginPath();
      ctx.ellipse(bubble.x - r * 0.34, y - r * 0.38, r * 0.2, r * 0.12, -0.6, 0, Math.PI * 2);
      ctx.fill();
      // Arco que se consume: cuánto le queda.
      ctx.strokeStyle = COLORS.paper;
      ctx.lineWidth = 4;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.arc(bubble.x, y, r + 6, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * (1 - bubble.age / bubble.life));
      ctx.stroke();
      if (bubble.grey) {
        ctx.strokeStyle = COLORS.paper;
        ctx.lineWidth = 5;
        ctx.beginPath();
        ctx.moveTo(bubble.x - r * 0.32, y - r * 0.32);
        ctx.lineTo(bubble.x + r * 0.32, y + r * 0.32);
        ctx.moveTo(bubble.x + r * 0.32, y - r * 0.32);
        ctx.lineTo(bubble.x - r * 0.32, y + r * 0.32);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
    }

    for (let i = bursts.length - 1; i >= 0; i--) {
      const burst = bursts[i];
      burst.life -= dt;
      if (burst.life <= 0) {
        bursts.splice(i, 1);
        continue;
      }
      const k = 1 - burst.life / 0.25;
      ctx.strokeStyle = burst.color;
      ctx.globalAlpha = 1 - k;
      ctx.lineWidth = 5;
      ctx.beginPath();
      ctx.arc(burst.x, burst.y, burst.r * (1 + k * 0.6), 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 1;
    }
    pops.draw(ctx, dt);
  });

  return {
    start() { running = true; },
    destroy() {
      stop();
      view.stop();
      canvas.remove();
    },
  };
}
