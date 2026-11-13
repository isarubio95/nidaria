// Casa: cambiar el papel pintado, la alfombra y los muebles de la
// habitación con lo que ya es del jugador.

import { h } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { SPOTS, itemsOfKind } from '/shared/catalog.js';
import { openSheet } from './sheet.js';
import { tabs } from './widgets.js';
import { openShop } from './shop.js';
import { itemThumb, lazyThumbs } from '../scene/thumbs.js';
import { BASE_DECOR } from '../scene/decor.js';

export function openDecor(initial = SPOTS[0].id) {
  let spot = initial;
  let stopThumbs = null;
  const body = h('div');
  const bar = tabs(SPOTS.map(({ id, name }) => ({ id, label: name })), spot, (id) => {
    spot = id;
    render();
  }, 'Sitio de la habitación');

  let off = null;
  const sheet = openSheet({
    title: 'Casa',
    tabs: bar,
    body,
    foot: h('button', { class: 'btn btn-quiet btn-wide', type: 'button', onclick: () => openShop('casa') }, 'Comprar para la casa'),
    onClose: () => {
      off?.();
      stopThumbs?.();
    },
  });

  function render() {
    stopThumbs?.();
    const info = SPOTS.find((entry) => entry.id === spot);
    const current = store.state.decor?.[spot] ?? null;
    const owned = itemsOfKind('casa').filter((item) => item.spot === spot && (store.state.inventory[item.id] ?? 0) > 0);
    const jobs = [];
    const put = (itemId) => run(api.decorate({ spot, itemId }));
    const tile = (itemId, name, checked) => {
      const base = itemId ?? BASE_DECOR[spot];
      let thumb = h('span', { class: 'item-thumb', 'aria-hidden': 'true' });
      if (base) {
        thumb = h('img', { class: 'item-thumb', alt: '', width: '76', height: '76' });
        jobs.push({ img: thumb, make: () => itemThumb(base) });
      }
      return h('button', { class: 'item', type: 'button', role: 'radio', 'aria-checked': String(checked), onclick: () => put(itemId) },
        thumb,
        h('span', { class: 'item-name' }, name),
      );
    };
    const tiles = [
      tile(null, info.base ?? 'Nada', current === null),
      ...owned.map((item) => tile(item.id, item.name, current === item.id)),
    ];
    body.replaceChildren(...[
      h('div', { class: 'items', role: 'radiogroup', 'aria-label': info.name }, tiles),
      owned.length ? null : h('p', { class: 'sheet-note' }, 'En la tienda, en Casa, hay más cosas para este sitio.'),
    ].filter(Boolean));
    stopThumbs = lazyThumbs(jobs);
  }

  off = store.onChange((state) => {
    if (state) render();
  });
  render();
  return sheet;
}
