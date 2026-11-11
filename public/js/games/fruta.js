// Lluvia de fruta: mueve a la criatura para recoger la fruta buena.

import { COLORS, fitCanvas, loop, loadImage, pointerX, rand, createPops } from './common.js';
import { itemThumb } from '../scene/thumbs.js';

const FRUIT = ['manzana', 'zanahoria', 'pan', 'galleta', 'helado', 'pescado'];

// Versión podrida de una fruta: la misma silueta, teñida de verde gris.
function rot(img) {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 96;
  const ctx = canvas.getContext('2d');
  ctx.drawImage(img, 0, 0, 96, 96);
  ctx.globalCompositeOperation = 'source-atop';
  ctx.fillStyle = 'rgba(96, 104, 72, 0.82)';
  ctx.fillRect(0, 0, 96, 96);
  return canvas;
}

export async function create({ area, level, avatar, hud, onEnd }) {
  const canvas = document.createElement('canvas');
  area.append(canvas);
  const view = fitCanvas(canvas);
  const images = (await Promise.all(FRUIT.map((id) => loadImage(itemThumb(id))))).filter(Boolean);
  const rotten = images.map(rot);
  const pops = createPops();

  let score = 0;
  let lives = level.lives;
  let time = level.seconds;
  let playerX = 0.5;
  let targetX = 0.5;
  let spawnIn = 0.4;
  let shake = 0;
  let running = false;
  let ended = false;
  const drops = [];

  const move = (event) => {
    targetX = Math.min(0.94, Math.max(0.06, pointerX(event, canvas).x / view.w));
  };
  canvas.addEventListener('pointerdown', move);
  canvas.addEventListener('pointermove', move);
  const onKey = (event) => {
    if (event.key === 'ArrowLeft') targetX = Math.max(0.06, targetX - 0.12);
    else if (event.key === 'ArrowRight') targetX = Math.min(0.94, targetX + 0.12);
  };
  window.addEventListener('keydown', onKey);

  const paintHud = () => hud({ score, target: level.target, time: Math.ceil(time), lives });
  paintHud();

  function finish() {
    if (ended) return;
    ended = true;
    running = false;
    onEnd(score);
  }

  const stop = loop((dt) => {
    const { ctx, w, h } = view;
    const ground = h - 46;
    const size = Math.min(92, w * 0.24);
    const speed = level.fall * Math.max(1, h / 640);

    if (running) {
      time -= dt;
      spawnIn -= dt;
      if (spawnIn <= 0) {
        spawnIn = (level.every / 1000) * rand(0.75, 1.25);
        const bad = Math.random() < level.rotten;
        const index = Math.floor(Math.random() * images.length);
        drops.push({ x: rand(0.08, 0.92), y: -40, bad, img: bad ? rotten[index] : images[index], spin: rand(-1.5, 1.5), angle: 0 });
      }
      if (time <= 0) {
        time = 0;
        finish();
      }
      paintHud();
    }
    playerX += (targetX - playerX) * Math.min(1, dt * 14);
    shake = Math.max(0, shake - dt);

    ctx.fillStyle = COLORS.sky;
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = COLORS.cream;
    ctx.fillRect(0, ground, w, h - ground);
    ctx.fillStyle = COLORS.pink;
    ctx.beginPath();
    ctx.ellipse(w / 2, ground + 20, w * 0.46, 15, 0, 0, Math.PI * 2);
    ctx.fill();

    const px = playerX * w + (shake > 0 ? Math.sin(shake * 60) * 5 : 0);
    for (let i = drops.length - 1; i >= 0; i--) {
      const drop = drops[i];
      if (running) {
        drop.y += speed * dt;
        drop.angle += drop.spin * dt;
      }
      const dx = drop.x * w;
      if (running && drop.y > ground - size * 0.8 && drop.y < ground - size * 0.2 && Math.abs(dx - px) < size * 0.5) {
        drops.splice(i, 1);
        if (drop.bad) {
          lives -= 1;
          shake = 0.35;
          pops.add(dx, drop.y - 10, 'Puaj', COLORS.coralEdge);
          if (lives <= 0) finish();
        } else {
          score += 1;
          pops.add(dx, drop.y - 10, '+1');
        }
        paintHud();
        continue;
      }
      if (drop.y > h + 40) {
        drops.splice(i, 1);
        continue;
      }
      ctx.save();
      ctx.translate(dx, drop.y);
      ctx.rotate(drop.angle);
      ctx.drawImage(drop.img, -26, -26, 52, 52);
      ctx.restore();
    }

    ctx.fillStyle = 'rgba(91, 58, 41, 0.16)';
    ctx.beginPath();
    ctx.ellipse(px, ground + 6, size * 0.36, 8, 0, 0, Math.PI * 2);
    ctx.fill();
    if (avatar) ctx.drawImage(avatar, px - size / 2, ground - size * 0.92, size, size);
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
