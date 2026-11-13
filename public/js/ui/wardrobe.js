// Armario: ponerle o quitarle la ropa que ya es suya.

import { h } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { SLOTS, itemsOfKind } from '/shared/catalog.js';
import { openSheet } from './sheet.js';
import { tabs } from './widgets.js';
import { openShop } from './shop.js';
import { itemThumb, lazyThumbs } from '../scene/thumbs.js';

export function openWardrobe() {
  let slot = SLOTS[0].id;
  const body = h('div');
  const bar = tabs(SLOTS.map(({ id, name }) => ({ id, label: name })), slot, (id) => {
    slot = id;
    render();
  }, 'Parte del cuerpo');

  let off = null;
  const sheet = openSheet({
    title: 'Armario',
    tabs: bar,
    body,
    foot: h('button', { class: 'btn btn-quiet btn-wide', type: 'button', onclick: () => openShop('ropa') }, 'Comprar ropa'),
    onClose: () => off?.(),
  });

  function render() {
    const creature = store.state.creature;
    const wearing = creature.equipped[slot];
    const owned = itemsOfKind('ropa').filter((item) => item.slot === slot && (store.state.inventory[item.id] ?? 0) > 0);
    if (!owned.length) {
      body.replaceChildren(h('div', { class: 'empty' },
        h('p', {}, 'No tienes nada para esta parte. En la tienda hay ropa de sobra.'),
      ));
      return;
    }
    const jobs = [];
    const put = (itemId) => run(api.equip({ slot, itemId }));
    const tiles = [
      h('button', { class: 'item', type: 'button', role: 'radio', 'aria-checked': String(!wearing), onclick: () => put(null) },
        h('span', { class: 'item-thumb', 'aria-hidden': 'true' }),
        h('span', { class: 'item-name' }, 'Nada'),
      ),
      ...owned.map((item) => {
        const img = h('img', { class: 'item-thumb', alt: '', width: '76', height: '76' });
        jobs.push({ img, make: () => itemThumb(item.id) });
        return h('button', { class: 'item', type: 'button', role: 'radio', 'aria-checked': String(wearing === item.id), onclick: () => put(item.id) },
          img,
          h('span', { class: 'item-name' }, item.name),
        );
      }),
    ];
    body.replaceChildren(h('div', { class: 'items', role: 'radiogroup', 'aria-label': 'Prendas' }, tiles));
    lazyThumbs(jobs);
  }

  off = store.onChange((state) => {
    if (state?.creature) render();
  });
  render();
  return sheet;
}
