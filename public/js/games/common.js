// Utilidades comunes de los minijuegos de lienzo.

export const COLORS = {
  cream: '#fff8ec', paper: '#fffdf8', ink: '#5b3a29', inkSoft: '#84644f', track: '#f0e6d6',
  pink: '#ff8fb1', pinkEdge: '#e2648b', mint: '#7ed6a5', mintEdge: '#4fae7c',
  sun: '#ffd166', sunEdge: '#e0a038', sky: '#bfe3ff', skyEdge: '#8fbfe8',
  blue: '#9db7ff', blueEdge: '#6f8de6', coral: '#ff7a7a', coralEdge: '#df5252',
  grey: '#b9aea4', greyEdge: '#8f8378',
};

// `source` es una dirección o una promesa de ella (como las miniaturas).
export async function loadImage(source) {
  const url = await source;
  if (!url) return null;
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = url;
  });
}

// Ajusta el lienzo a su caja y a la densidad de la pantalla. Devuelve
// un objeto cuyo tamaño se mantiene al día.
export function fitCanvas(canvas) {
  const view = { ctx: canvas.getContext('2d'), w: 1, h: 1, stop: null };
  const resize = () => {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    view.w = Math.max(1, canvas.clientWidth);
    view.h = Math.max(1, canvas.clientHeight);
    canvas.width = Math.round(view.w * dpr);
    canvas.height = Math.round(view.h * dpr);
    view.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  };
  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();
  view.stop = () => observer.disconnect();
  return view;
}

// Bucle de dibujo con el tiempo en segundos. Devuelve cómo pararlo.
export function loop(step) {
  let last = 0;
  let id = 0;
  let stopped = false;
  const frame = (ms) => {
    if (stopped) return;
    const t = ms / 1000;
    const dt = last ? Math.min(0.05, t - last) : 0;
    last = t;
    step(dt, t);
    id = requestAnimationFrame(frame);
  };
  id = requestAnimationFrame(frame);
  return () => {
    stopped = true;
    cancelAnimationFrame(id);
  };
}

export function pointerX(event, canvas) {
  const rect = canvas.getBoundingClientRect();
  return { x: event.clientX - rect.left, y: event.clientY - rect.top };
}

export const rand = (a, b) => a + Math.random() * (b - a);

export function shuffle(list) {
  const copy = [...list];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

// Textos que suben y se desvanecen ("+1").
export function createPops() {
  const pops = [];
  return {
    add(x, y, text, color = COLORS.ink) {
      pops.push({ x, y, text, color, life: 0.7 });
    },
    draw(ctx, dt) {
      ctx.font = '900 20px "Zen Maru Gothic", system-ui, sans-serif';
      ctx.textAlign = 'center';
      for (let i = pops.length - 1; i >= 0; i--) {
        const pop = pops[i];
        pop.life -= dt;
        pop.y -= 46 * dt;
        if (pop.life <= 0) {
          pops.splice(i, 1);
          continue;
        }
        ctx.globalAlpha = Math.min(1, pop.life / 0.3);
        ctx.fillStyle = pop.color;
        ctx.fillText(pop.text, pop.x, pop.y);
      }
      ctx.globalAlpha = 1;
    },
  };
}
