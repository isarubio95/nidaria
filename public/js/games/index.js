// Lanza un minijuego: abre la partida en el servidor, monta la pantalla
// del juego, envía la puntuación al terminar y enseña el resultado.

import { gsap } from 'gsap';
import { h, icon, coin, fmt, reducedMotion } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { getGame, DIFFICULTIES, FEATURED_MULT, baseReward, levelFor, perksFor } from '/shared/economy.js';
import { TRAITS } from '/shared/dna.js';
import { dialog } from '../ui/dialog.js';
import { line } from '../ui/speech.js';
import { creatureThumb } from '../scene/thumbs.js';
import { loadImage } from './common.js';
import { sfx } from '../audio/sfx.js';

const MODULES = {
  fruta: () => import('./fruta.js'),
  parejas: () => import('./parejas.js'),
  charcos: () => import('./charcos.js'),
  eco: () => import('./eco.js'),
  burbujas: () => import('./burbujas.js'),
  escondite: () => import('./escondite.js'),
};

const root = document.getElementById('games');
const app = document.getElementById('app');

export async function launchGame(gameId, difficulty, { room, say }) {
  const game = getGame(gameId);
  const started = await run(api.startGame({ gameId, difficulty }));
  if (!started) return;
  const { playId, featured } = started.result;
  const creature = store.state.creature;
  // Las ventajas del carácter de la criatura ya van dentro del nivel.
  const level = levelFor(game, difficulty, creature.genes);
  const perks = perksFor(gameId, creature.genes);

  const scoreText = h('span', { class: 'num' });
  const timeText = h('span', { class: 'num' });
  const livesText = h('span', { class: 'num' });
  const scoreChip = h('span', { class: 'stat', 'aria-label': 'Puntos y objetivo' }, icon('target'), scoreText);
  const timeChip = h('span', { class: 'stat', hidden: true, 'aria-label': 'Segundos' }, icon('clock'), timeText);
  const livesChip = h('span', { class: 'stat', hidden: true, 'aria-label': 'Vidas' }, icon('heart'), livesText);
  const hud = ({ score, target, time, lives }) => {
    scoreText.textContent = `${score} / ${target}`;
    if (time !== undefined) {
      timeChip.hidden = false;
      timeText.textContent = `${time} s`;
    }
    if (lives !== undefined) {
      livesChip.hidden = false;
      livesText.textContent = String(lives);
    }
  };

  const area = h('div', { class: 'game-area' });
  const startButton = h('button', { class: 'btn btn-pink btn-wide', type: 'button' }, 'Empezar');
  const cover = h('div', { class: 'game-cover' },
    h('div', { class: 'dialog' },
      h('h2', {}, game.name),
      h('p', {}, game.blurb),
      h('p', {}, `Dificultad ${DIFFICULTIES[difficulty].name.toLowerCase()}: llega a ${level.target} para ganar ${fmt(baseReward(game, difficulty, featured ? FEATURED_MULT : 1))} monedas.`),
      featured ? h('p', { class: 'cover-note' }, 'Hoy es el juego del día: paga un 50 % más.') : null,
      perks.map(({ trait, text }) => h('p', { class: 'cover-note' }, h('strong', {}, `Por ser ${TRAITS[trait].name.toLowerCase()}: `), text.charAt(0).toLowerCase() + text.slice(1))),
      h('div', { class: 'dialog-actions' }, startButton),
    ),
  );
  const leave = h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Salir del juego' }, icon('back'));
  const screen = h('section', { class: 'game', role: 'dialog', 'aria-modal': 'true', 'aria-label': game.name },
    h('header', { class: 'game-top' }, leave, h('h2', {}, game.name), h('div', { class: 'game-stats' }, scoreChip, timeChip, livesChip)),
    area,
  );
  area.append(cover);
  root.append(screen);
  app.inert = true;
  room.setPaused(true);
  void screen.offsetWidth;
  screen.classList.add('is-open');

  let instance = null;
  let closed = false;

  async function close(score, abandoned = false) {
    if (closed) return;
    closed = true;
    const finished = await run(api.finishGame({ playId, score }));
    instance?.destroy();
    screen.classList.remove('is-open');
    room.setPaused(false);
    app.inert = false;
    setTimeout(() => screen.remove(), 260);
    if (!finished || abandoned) return;
    const { won, coins, target } = finished.result;

    sfx(won ? 'gana' : 'pierde');
    const amount = h('span', { class: 'num' }, '0');
    const reward = won ? h('div', { class: 'reward' }, coin(), amount) : null;
    const shown = dialog({
      title: won ? '¡Has ganado!' : 'No ha podido ser',
      text: won
        ? `Has hecho ${score} y el objetivo era ${target}.${finished.result.featured ? ' Como es el juego del día, el premio sube un 50 %.' : ''}`
        : `Has hecho ${score} y hacían falta ${target}. La energía de juego se ha gastado igual.`,
      extra: reward,
      actions: [{ label: won ? 'Recoger' : 'Volver', value: true, tone: won ? 'sun' : 'pink' }],
    });
    if (won) {
      const counter = { value: 0 };
      gsap.to(counter, {
        value: coins,
        duration: reducedMotion() ? 0 : 0.9,
        ease: 'power2.out',
        onUpdate: () => { amount.textContent = `+${fmt(counter.value)}`; },
      });
    }
    await shown;
    room.cheer(won);
    say(line(won ? 'gana' : 'pierde'));
  }

  leave.addEventListener('click', async () => {
    const sure = await dialog({
      title: '¿Salir del juego?',
      text: 'Perderás esta partida y la energía de juego que ha costado.',
      actions: [{ label: 'Seguir jugando', value: false, tone: 'pink' }, { label: 'Salir', value: true }],
    });
    if (sure) close(0, true);
  });

  // El botón responde desde el primer momento: si el juego aún se está
  // cargando, recuerda el toque y empieza en cuanto esté listo.
  let ready = false;
  let wanted = false;
  const begin = () => {
    cover.hidden = true;
    instance.start();
  };
  startButton.addEventListener('click', () => {
    if (ready) {
      begin();
      return;
    }
    wanted = true;
    startButton.disabled = true;
    startButton.textContent = 'Cargando…';
  });
  startButton.focus({ preventScroll: true });

  const [module, avatar] = await Promise.all([
    MODULES[gameId](),
    loadImage(creatureThumb(creature.formId, creature.genes)),
  ]);
  const created = await module.create({ area, level, avatar, hud, onEnd: (score) => close(score) });
  if (closed) {
    created.destroy();
    return;
  }
  instance = created;
  ready = true;
  if (wanted) begin();
}
