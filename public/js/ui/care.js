// Hojas de cuidado: elegir qué comida darle y con qué curarla.

import { h } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { itemsOfKind } from '/shared/catalog.js';
import { openSheet, closeSheet } from './sheet.js';
import { openShop, effectChips } from './shop.js';
import { line } from './speech.js';
import { itemThumb, lazyThumbs } from '../scene/thumbs.js';

// Casillas de lo que el jugador tiene de `items`, con cuántos le quedan.
export function ownedTiles(items, onPick) {
  const jobs = [];
  const tiles = items.map((item) => {
    const img = h('img', { class: 'item-thumb', alt: '', width: '76', height: '76' });
    jobs.push({ img, make: () => itemThumb(item.id) });
    return h('button', { class: 'item', type: 'button', onclick: () => onPick(item) },
      img,
      h('span', { class: 'item-name' }, item.name),
      effectChips(item, 1),
      h('span', { class: 'item-count num', 'aria-label': `Tienes ${store.state.inventory[item.id]}` }, `×${store.state.inventory[item.id]}`),
    );
  });
  lazyThumbs(jobs);
  return tiles;
}

function empty(text, category) {
  return h('div', { class: 'empty' },
    h('p', {}, text),
    h('button', { class: 'btn btn-pink', type: 'button', onclick: () => openShop(category) }, 'Ir a la tienda'),
  );
}

export function openFood(room, say) {
  const items = itemsOfKind('comida').filter((item) => (store.state.inventory[item.id] ?? 0) > 0);
  const body = items.length
    ? h('div', { class: 'items' }, ownedTiles(items, async (item) => {
      closeSheet();
      const payload = await run(api.feed({ itemId: item.id }));
      if (!payload) return;
      const reaction = payload.result.reaction;
      await room.feed(item.id, reaction);
      say(line(reaction));
    }))
    : empty('No te queda comida. Compra algo y vuelve.', 'comida');
  return openSheet({
    title: 'Comer',
    sub: items.length ? 'Elige qué le das. Cada criatura tiene una comida favorita y otra que no soporta.' : null,
    body,
    foot: items.length ? h('button', { class: 'btn btn-quiet btn-wide', type: 'button', onclick: () => openShop('comida') }, 'Comprar más comida') : null,
  });
}

export function openHeal(room, say) {
  const creature = store.state.creature;
  const items = itemsOfKind('cuidado').filter((item) => !item.bath && (store.state.inventory[item.id] ?? 0) > 0);
  const body = items.length
    ? h('div', { class: 'items' }, ownedTiles(items, async (item) => {
      closeSheet();
      const payload = await run(api.heal({ itemId: item.id }));
      if (!payload) return;
      room.heal();
      say(line('cura'));
    }))
    : empty(creature.sick ? 'Está enferma y no tienes medicina. Cómprala cuanto antes.' : 'No tienes medicina ni vitaminas.', 'cuidado');
  return openSheet({
    title: 'Curar',
    sub: creature.sick
      ? `${creature.name} está enferma: pierde salud hasta que tome medicina.`
      : 'La medicina cura la enfermedad. Las vitaminas suben la salud.',
    body,
    foot: items.length ? h('button', { class: 'btn btn-quiet btn-wide', type: 'button', onclick: () => openShop('cuidado') }, 'Comprar más') : null,
  });
}
