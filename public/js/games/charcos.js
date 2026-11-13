// Salta charcos: la criatura corre sola; toca para saltar cada charco.

import { COLORS, fitCanvas, loop, rand, createPops } from './common.js';
import { sfx } from '../audio/sfx.js';

export async function create({ area, level, avatar, hud, onEnd }) {
  const canvas = document.createElement('canvas');
  area.append(canvas);
  const view = fitCanvas(canvas);
  const pops = createPops();

  let score = 0;
  let y = 0;
  let vy = 0;
  let nextIn = 1.2;
  let running = false;
  let ended = false;
  let splash = 0;
  let scroll = 0;
  const puddles = [];
  // Ventajas: las fuertes aguantan un charco y las traviesas saltan más.
  let shields = level.shield ?? 0;
  const lift = 620 * (level.jump ?? 1);

  const jump = () => {
    if (!running || y !== 0) return;
    vy = lift;
    sfx('salto');
  };
  const onPointer = (event) => {
    event.preventDefault();
    jump();
  };
  const onKey = (event) => {
    if (event.key === ' ' || event.key === 'ArrowUp') {
      event.preventDefault();
      jump();
    }
  };
  canvas.addEventListener('pointerdown', onPointer);
  window.addEventListener('keydown', onKey);
  const paintHud = () => hud({ score, target: level.target, lives: level.shield ? shields + 1 : undefined });
  paintHud();

  function finish() {
    if (ended) return;
    ended = true;
    running = false;
    setTimeout(() => onEnd(score), 500);
  }

  const stop = loop((dt) => {
    const { ctx, w, h } = view;
    const ground = Math.round(h * 0.8);
    const size = Math.min(96, w * 0.24);
    const px = w * 0.24;
    const speed = (level.speed + score * level.grow) * Math.min(1.8, Math.max(1, w / 420));

    if (running) {
      scroll += speed * dt;
      vy -= 1750 * dt;
      y = Math.max(0, y + vy * dt);
      if (y === 0) vy = 0;
      nextIn -= dt;
      if (nextIn <= 0) {
        nextIn = rand(level.gap[0], level.gap[1]);
        puddles.push({ x: w + 60, width: rand(54, 78) + Math.min(34, score * 1.4), passed: false });
      }
      for (let i = puddles.length - 1; i >= 0; i--) {
        const puddle = puddles[i];
        puddle.x -= speed * dt;
        const left = puddle.x - puddle.width / 2;
        const right = puddle.x + puddle.width / 2;
        if (!puddle.passed && y < 8 && px + size * 0.16 > left && px - size * 0.16 < right) {
          splash = 0.5;
          if (shields > 0) {
            // Este charco ya no cuenta ni moja otra vez.
            shields -= 1;
            puddle.passed = true;
            pops.add(px, ground - size, '¡Uf!', COLORS.blueEdge);
            sfx('fallo', { volume: 0.6 });
            paintHud();
          } else {
            pops.add(px, ground - size, '¡Chof!', COLORS.blueEdge);
            sfx('fallo');
            finish();
          }
        } else if (!puddle.passed && right < px - size * 0.2) {
          puddle.passed = true;
          score += 1;
          pops.add(px, ground - size - y - 8, '+1');
          sfx('punto');
          paintHud();
          if (score >= level.target) finish();
        }
        if (right < -80) puddles.splice(i, 1);
      }
    }
    splash = Math.max(0, splash - dt);

    ctx.fillStyle = COLORS.sky;
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = COLORS.paper;
    for (let i = 0; i < 4; i++) {
      const cx = ((i * 260 - scroll * 0.18) % (w + 260) + w + 260) % (w + 260) - 130;
      const cy = 50 + (i % 2) * 46;
      ctx.beginPath();
      ctx.ellipse(cx, cy, 44, 16, 0, 0, Math.PI * 2);
      ctx.ellipse(cx + 26, cy - 10, 30, 16, 0, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.fillStyle = COLORS.mint;
    for (let i = 0; i < 4; i++) {
      const hx = ((i * 300 - scroll * 0.4) % (w + 300) + w + 300) % (w + 300) - 150;
      ctx.beginPath();
      ctx.ellipse(hx, ground + 10, 170, 70 + (i % 2) * 26, 0, Math.PI, 0);
      ctx.fill();
    }
    ctx.fillStyle = COLORS.cream;
    ctx.fillRect(0, ground, w, h - ground);
    ctx.fillStyle = COLORS.track;
    for (let i = 0; i < Math.ceil(w / 90) + 2; i++) {
      const sx = ((i * 90 - scroll) % (w + 180) + w + 180) % (w + 180) - 90;
      ctx.fillRect(sx, ground + 26, 40, 6);
    }

    for (const puddle of puddles) {
      ctx.fillStyle = COLORS.blueEdge;
      ctx.beginPath();
      ctx.ellipse(puddle.x, ground + 5, puddle.width / 2, 9, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = COLORS.blue;
      ctx.beginPath();
      ctx.ellipse(puddle.x, ground + 3, puddle.width / 2 - 4, 6, 0, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.fillStyle = 'rgba(91, 58, 41, 0.16)';
    ctx.beginPath();
    ctx.ellipse(px, ground + 6, size * 0.34 * (1 - Math.min(0.5, y / 300)), 7, 0, 0, Math.PI * 2);
    ctx.fill();
    if (avatar) {
      const stretch = y > 0 ? 1.06 : 1 + Math.sin(scroll * 0.06) * 0.03;
      ctx.drawImage(avatar, px - (size / stretch) / 2, ground - size * 0.92 * stretch - y, size / stretch, size * stretch);
    }
    if (splash > 0) {
      ctx.fillStyle = COLORS.blue;
      for (let i = 0; i < 6; i++) {
        const a = Math.PI * (0.15 + i * 0.14);
        const d = (0.5 - splash) * 150;
        ctx.beginPath();
        ctx.arc(px + Math.cos(a) * d, ground - Math.sin(a) * d * 0.9, 6 * splash * 2, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    pops.draw(ctx, dt);
  });

  return {
    start() { running = true; },
    destroy() {
      stop();
      view.stop();
      window.removeEventListener('keydown', onKey);
      canvas.remove();
    },
  };
}
