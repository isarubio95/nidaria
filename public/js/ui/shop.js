// Tienda: pestañas por categoría, cuadrícula de artículos y, al elegir
// uno, una barra con sus efectos y el botón de compra.

import { h, icon, coin, fmt } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { CATEGORIES, SLOTS, SPOTS, itemsOfKind, getItem, MAX_STACK } from '/shared/catalog.js';
import { openSheet } from './sheet.js';
import { tabs } from './widgets.js';
import { toast } from './toast.js';
import { itemThumb, lazyThumbs } from '../scene/thumbs.js';
import { sfx } from '../audio/sfx.js';

const chip = (iconName, text) => h('span', { class: 'chip' }, icon(iconName), text);
const signed = (value) => (value > 0 ? `+${value}` : `−${Math.abs(value)}`);

// `limit` deja solo los primeros efectos, para las casillas pequeñas.
export function effectChips(item, limit = 9) {
  const chips = [];
  if (item.kind === 'comida') {
    chips.push(chip('food', `${signed(item.hunger)} comida`));
    if (item.fun) chips.push(chip('ball', `${signed(item.fun)} diversión`));
    if (item.health) chips.push(chip('heart', `${signed(item.health)} salud`));
  } else if (item.kind === 'cuidado') {
    if (item.cures) chips.push(chip('heal', 'Cura la enfermedad'));
    if (item.bath) chips.push(chip('drop', 'Higiene al máximo'));
    if (item.health) chips.push(chip('heart', `${signed(item.health)} salud`));
    if (item.fun) chips.push(chip('ball', `${signed(item.fun)} diversión`));
  } else if (item.kind === 'juguete') {
    chips.push(chip('ball', `${signed(item.fun)} diversión`));
    chips.push(chip('moon', item.energy ? `${signed(-item.energy)} energía` : 'No cansa'));
  } else if (item.kind === 'ropa') {
    chips.push(chip('hanger', SLOTS.find((slot) => slot.id === item.slot).name));
  } else if (item.kind === 'casa') {
    chips.push(chip('home', SPOTS.find((spot) => spot.id === item.spot).name));
  }
  return h('div', { class: 'chips' }, chips.slice(0, limit));
}

export function openShop(initial = 'comida') {
  let category = initial;
  let selected = null;
  let qty = 1;
  let stopThumbs = null;
  const grid = h('div', { class: 'items' });
  const bar = tabs(CATEGORIES.map(({ id, name }) => ({ id, label: name })), category, (id) => {
    category = id;
    selected = null;
    render();
  }, 'Categorías');

  let off = null;
  const sheet = openSheet({
    title: 'Tienda',
    tabs: bar,
    body: grid,
    onClose: () => {
      off?.();
      stopThumbs?.();
    },
  });

  const owned = (id) => store.state.inventory[id] ?? 0;

  function render() {
    stopThumbs?.();
    const jobs = [];
    const tiles = itemsOfKind(category).map((item) => {
      const img = h('img', { class: 'item-thumb', alt: '', width: '76', height: '76' });
      jobs.push({ img, make: () => itemThumb(item.id) });
      const count = owned(item.id);
      const mine = item.reusable && count > 0;
      return h('button', {
        class: 'item', type: 'button', 'aria-pressed': String(selected === item.id), dataset: { id: item.id },
        onclick: () => {
          selected = item.id;
          qty = 1;
          for (const tile of grid.children) tile.setAttribute('aria-pressed', String(tile.dataset.id === item.id));
          renderFoot();
        },
      },
        img,
        h('span', { class: 'item-name' }, item.name),
        mine
          ? h('span', { class: 'item-tag' }, 'Ya es tuyo')
          : h('span', { class: 'item-price num' }, coin(true), fmt(item.price)),
        !item.reusable && count > 0 ? h('span', { class: 'item-count num', 'aria-label': `Tienes ${count}` }, `×${count}`) : null,
      );
    });
    grid.replaceChildren(...tiles);
    stopThumbs = lazyThumbs(jobs);
    renderFoot();
  }

  function renderFoot() {
    const item = selected ? getItem(selected) : null;
    if (!item) return sheet.setFoot(null);
    const count = owned(item.id);
    const mine = item.reusable && count > 0;
    const room = MAX_STACK - count;
    qty = Math.max(1, Math.min(qty, room));
    const cost = item.price * qty;
    const short = cost - store.state.user.coins;

    const info = h('div', { class: 'buybar-info' },
      h('p', { class: 'buybar-name' }, item.name),
      h('p', { class: 'buybar-blurb' }, item.blurb),
      effectChips(item),
    );
    let action;
    if (mine && item.kind === 'ropa') {
      const wearing = store.state.creature?.equipped?.[item.slot] === item.id;
      action = h('button', {
        class: 'btn btn-mint', type: 'button', disabled: wearing || !store.state.creature?.alive || store.state.creature.stage === 'huevo',
        onclick: async () => {
          const payload = await run(api.equip({ slot: item.slot, itemId: item.id }));
          if (payload) toast(`${store.state.creature.name} lleva puesto: ${item.name}.`);
        },
      }, wearing ? 'Lo lleva puesto' : 'Ponérselo');
    } else if (mine && item.kind === 'casa') {
      const there = store.state.decor?.[item.spot] === item.id;
      action = h('button', {
        class: 'btn btn-mint', type: 'button', disabled: there,
        onclick: async () => {
          const payload = await run(api.decorate({ spot: item.spot, itemId: item.id }));
          if (payload) toast(`Has puesto en la habitación: ${item.name}.`);
        },
      }, there ? 'Ya está puesto' : 'Colocar');
    } else if (mine) {
      action = h('button', { class: 'btn', type: 'button', disabled: true }, 'Ya es tuyo');
    } else {
      action = h('button', {
        class: 'btn btn-primary', type: 'button', disabled: short > 0,
        onclick: async (event) => {
          event.currentTarget.disabled = true;
          const payload = await run(api.buy({ itemId: item.id, qty }));
          if (payload) sfx('monedas');
          if (payload) toast(qty > 1 ? `Has comprado ${qty} de ${item.name}.` : `Has comprado: ${item.name}.`);
        },
      }, short > 0 ? `Te faltan ${fmt(short)}` : ['Comprar', coin(true), h('span', { class: 'num' }, fmt(cost))]);
    }
    const stepper = item.reusable ? null : h('div', { class: 'stepper' },
      h('button', { type: 'button', 'aria-label': 'Uno menos', disabled: qty <= 1, onclick: () => { qty -= 1; renderFoot(); } }, '−'),
      h('output', { class: 'num', 'aria-label': 'Cantidad' }, String(qty)),
      h('button', { type: 'button', 'aria-label': 'Uno más', disabled: qty >= Math.min(room, 20), onclick: () => { qty += 1; renderFoot(); } }, '+'),
    );
    sheet.setFoot(h('div', { class: 'buybar' }, info, h('div', { class: 'buybar-row' }, stepper, action)));
  }

  off = store.onChange((state) => {
    if (state) render();
  });
  render();
  return sheet;
}
