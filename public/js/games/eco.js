// Eco: mira la secuencia de luces y repítela. Cada ronda añade una.

import { h } from '../dom.js';
import { COLORS } from './common.js';

const PADS = [
  [COLORS.pink, COLORS.pinkEdge, 'Rosa'], [COLORS.mint, COLORS.mintEdge, 'Verde'],
  [COLORS.sun, COLORS.sunEdge, 'Amarillo'], [COLORS.blue, COLORS.blueEdge, 'Azul'],
];

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function create({ area, level, hud, onEnd }) {
  const sequence = [];
  let step = 0;
  let rounds = 0;
  let listening = false;
  let ended = false;
  let alive = true;

  const status = h('p', { class: 'eco-status', role: 'status' }, 'Mira bien');
  const pads = PADS.map(([color, edge, name], index) => {
    const pad = h('button', { class: 'eco-pad', type: 'button', 'aria-label': name, disabled: true });
    pad.style.setProperty('--pad', color);
    pad.style.setProperty('--pad-edge', edge);
    pad.addEventListener('click', () => press(index));
    return pad;
  });
  const board = h('div', { class: 'eco' }, status, pads);
  area.append(board);

  const paintHud = () => hud({ score: rounds, target: level.target });
  paintHud();

  const setEnabled = (on) => pads.forEach((pad) => { pad.disabled = !on; });

  async function flash(index, ms) {
    pads[index].classList.add('is-lit');
    await wait(ms);
    pads[index].classList.remove('is-lit');
  }

  async function playRound() {
    listening = false;
    setEnabled(false);
    status.textContent = 'Mira bien';
    sequence.push(Math.floor(Math.random() * 4));
    await wait(550);
    for (const index of sequence) {
      if (!alive) return;
      await flash(index, level.beat * 0.62);
      await wait(level.beat * 0.38);
    }
    if (!alive) return;
    step = 0;
    listening = true;
    setEnabled(true);
    status.textContent = 'Te toca';
  }

  function finish() {
    if (ended) return;
    ended = true;
    listening = false;
    setEnabled(false);
    setTimeout(() => onEnd(rounds), 600);
  }

  async function press(index) {
    if (!listening) return;
    flash(index, 160);
    if (index !== sequence[step]) {
      status.textContent = 'Esa no era';
      finish();
      return;
    }
    step += 1;
    if (step < sequence.length) return;
    rounds += 1;
    paintHud();
    if (rounds >= level.target) {
      status.textContent = '¡Secuencia completa!';
      finish();
      return;
    }
    status.textContent = '¡Bien!';
    listening = false;
    setEnabled(false);
    await wait(500);
    if (alive) playRound();
  }

  return {
    start() { playRound(); },
    destroy() {
      alive = false;
      board.remove();
    },
  };
}
