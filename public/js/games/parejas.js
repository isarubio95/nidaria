// Parejas: destapa dos cartas iguales hasta encontrarlas todas.

import { h } from '../dom.js';
import { shuffle } from './common.js';
import { itemThumb } from '../scene/thumbs.js';

const FACES = ['manzana', 'helado', 'tarta', 'pescado', 'pelota', 'peonza', 'peluche', 'cometa', 'gorro', 'corona', 'zanahoria', 'galleta'];

export async function create({ area, level, hud, onEnd }) {
  const faces = shuffle(FACES).slice(0, level.pairs);
  const deck = shuffle([...faces, ...faces]);
  const rows = Math.ceil(deck.length / level.cols);
  let found = 0;
  let time = level.seconds;
  let open = [];
  let locked = false;
  let running = false;
  let ended = false;
  let timer = null;
  let peekTimer = null;

  const paintHud = () => hud({ score: found, target: level.pairs, time: Math.ceil(time) });

  function finish() {
    if (ended) return;
    ended = true;
    running = false;
    clearInterval(timer);
    setTimeout(() => onEnd(found), 450);
  }

  const cards = deck.map((face) => {
    const card = h('button', { class: 'pair-card', type: 'button', 'aria-label': 'Carta tapada', dataset: { open: 'false', done: 'false' } },
      h('span', { class: 'pair-face pair-back' }),
      h('span', { class: 'pair-face pair-front' }, h('img', { src: itemThumb(face), alt: '' })),
    );
    card.addEventListener('click', () => {
      if (!running || locked || card.dataset.open === 'true') return;
      card.dataset.open = 'true';
      card.setAttribute('aria-label', 'Carta destapada');
      open.push({ card, face });
      if (open.length < 2) return;
      const [a, b] = open;
      open = [];
      if (a.face === b.face) {
        a.card.dataset.done = b.card.dataset.done = 'true';
        a.card.disabled = b.card.disabled = true;
        found += 1;
        paintHud();
        if (found >= level.pairs) finish();
        return;
      }
      locked = true;
      setTimeout(() => {
        for (const item of [a, b]) {
          item.card.dataset.open = 'false';
          item.card.setAttribute('aria-label', 'Carta tapada');
        }
        locked = false;
      }, 750);
    });
    return card;
  });

  const grid = h('div', { class: 'pairs' }, cards);
  grid.style.gridTemplateColumns = `repeat(${level.cols}, minmax(0, 1fr))`;
  area.append(grid);

  // Las cartas llenan el hueco sin deformarse ni salirse.
  const fit = () => {
    const gap = 8;
    const freeW = area.clientWidth - 32;
    const freeH = area.clientHeight - 48;
    const byHeight = ((freeH - gap * (rows - 1)) / rows) * 0.75;
    const byWidth = (freeW - gap * (level.cols - 1)) / level.cols;
    const cardW = Math.max(40, Math.min(byHeight, byWidth, 120));
    grid.style.gridTemplateColumns = `repeat(${level.cols}, ${Math.floor(cardW)}px)`;
  };
  const observer = new ResizeObserver(fit);
  observer.observe(area);
  fit();
  paintHud();

  const tick = () => {
    timer = setInterval(() => {
      time -= 0.25;
      if (time <= 0) {
        time = 0;
        finish();
      }
      paintHud();
    }, 250);
  };

  return {
    start() {
      running = true;
      // Las curiosas ven todas las cartas un momento antes de empezar.
      if (!level.peek) {
        tick();
        return;
      }
      locked = true;
      for (const card of cards) card.dataset.open = 'true';
      peekTimer = setTimeout(() => {
        for (const card of cards) card.dataset.open = 'false';
        locked = false;
        tick();
      }, level.peek * 1000);
    },
    destroy() {
      clearTimeout(peekTimer);
      clearInterval(timer);
      observer.disconnect();
      grid.remove();
    },
  };
}
