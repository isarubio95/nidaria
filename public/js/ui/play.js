// Jugar: minijuegos (gastan energía de juego y dan monedas) y juguetes.

import { h, coin, fmt, duration } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { MINIGAMES, DIFFICULTIES, baseReward } from '/shared/economy.js';
import { itemsOfKind, TOY_COOLDOWN_MS } from '/shared/catalog.js';
import { canPlayGame } from '/shared/actions.js';
import { openSheet, closeSheet } from './sheet.js';
import { tabs } from './widgets.js';
import { openShop, effectChips } from './shop.js';
import { line } from './speech.js';
import { launchGame } from '../games/index.js';
import { itemThumb, lazyThumbs } from '../scene/thumbs.js';

const LEVELS = Object.entries(DIFFICULTIES).map(([id, info]) => ({ id, label: info.name }));

function remember(gameId, value) {
  try {
    if (value) localStorage.setItem(`nidaria:nivel:${gameId}`, value);
    return localStorage.getItem(`nidaria:nivel:${gameId}`) ?? 'facil';
  } catch {
    return value ?? 'facil';
  }
}

export function openPlay(room, say, initial = 'juegos') {
  let tab = initial;
  let timer = null;
  const body = h('div');
  const bar = tabs([{ id: 'juegos', label: 'Minijuegos' }, { id: 'juguetes', label: 'Juguetes' }], tab, (id) => {
    tab = id;
    render();
  }, 'Tipo de juego');

  const sheet = openSheet({
    title: 'Jugar',
    tabs: bar,
    body,
    onClose: () => clearInterval(timer),
  });

  function renderGames() {
    const pips = h('div', { class: 'pips', 'aria-hidden': 'true' });
    const note = h('p', { class: 'tickets-note' });
    const label = h('p', { class: 'tickets-label' });
    const blocked = canPlayGame(store.live().creature);
    const buttons = [];

    const paintTickets = () => {
      const { tickets, nextAt } = store.tickets();
      const max = store.state.user.ticketsMax;
      label.textContent = `Energía de juego: ${tickets} de ${max}`;
      pips.replaceChildren(...Array.from({ length: max }, (_, i) => h('span', { class: i < tickets ? 'pip is-on' : 'pip' })));
      note.textContent = nextAt ? `Otra en ${duration(nextAt - store.now())}. Cada partida gasta una.` : 'Está al máximo. Cada partida gasta una.';
      for (const button of buttons) button.disabled = tickets < 1 || Boolean(blocked);
    };

    const rows = MINIGAMES.map((game) => {
      let level = remember(game.id);
      const prize = h('span', { class: 'num' }, fmt(baseReward(game, level)));
      const start = h('button', {
        class: 'btn btn-pink', type: 'button',
        onclick: async () => {
          await closeSheet();
          launchGame(game.id, level, { room, say });
        },
      }, 'Jugar', coin(true), prize);
      buttons.push(start);
      const levels = tabs(LEVELS, level, (id) => {
        level = remember(game.id, id);
        prize.textContent = fmt(baseReward(game, level));
      }, `Dificultad de ${game.name}`);
      return h('article', { class: 'game-row' },
        h('div', {}, h('h3', {}, game.name), h('p', {}, game.blurb)),
        h('div', { class: 'game-controls' }, levels, start),
      );
    });

    body.replaceChildren(...[
      h('div', { class: 'tickets' }, h('div', {}, label, note), pips),
      blocked ? h('p', { class: 'form-error' }, blocked) : null,
      h('div', { class: 'games' }, rows),
    ].filter(Boolean));
    paintTickets();
    clearInterval(timer);
    timer = setInterval(paintTickets, 1000);
  }

  function renderToys() {
    clearInterval(timer);
    const toys = itemsOfKind('juguete').filter((item) => (store.state.inventory[item.id] ?? 0) > 0);
    if (!toys.length) {
      body.replaceChildren(h('div', { class: 'empty' },
        h('p', {}, 'Todavía no tienes juguetes. Jugar con uno divierte sin gastar energía de juego.'),
        h('button', { class: 'btn btn-pink', type: 'button', onclick: () => openShop('juguete') }, 'Ir a la tienda'),
      ));
      return;
    }
    const jobs = [];
    const cooldowns = store.state.creature.cooldowns ?? {};
    const wait = TOY_COOLDOWN_MS / store.state.timeScale;
    const tiles = toys.map((item) => {
      const img = h('img', { class: 'item-thumb', alt: '', width: '76', height: '76' });
      jobs.push({ img, make: () => itemThumb(item.id) });
      const left = (cooldowns[item.id] ?? 0) + wait - store.now();
      return h('button', {
        class: 'item', type: 'button', disabled: left > 0,
        onclick: async () => {
          closeSheet();
          const payload = await run(api.toy({ itemId: item.id }));
          if (!payload) return;
          await room.playToy(item.id);
          say(line('juega'));
        },
      },
        img,
        h('span', { class: 'item-name' }, item.name),
        left > 0 ? h('span', { class: 'item-tag' }, `Listo en ${duration(left)}`) : effectChips(item, 1),
      );
    });
    body.replaceChildren(h('div', { class: 'items' }, tiles));
    lazyThumbs(jobs, 3);
  }

  function render() {
    if (tab === 'juegos') renderGames();
    else renderToys();
  }
  render();
  return sheet;
}
