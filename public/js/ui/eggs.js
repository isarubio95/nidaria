// Elección de huevo: al empezar y cada vez que una criatura se va.

import { h } from '../dom.js';
import { api } from '../api.js';
import { run } from '../store.js';
import { FAMILY_INFO } from '/shared/dna.js';
import { eggThumb, showThumb } from '../scene/thumbs.js';

export function mountEggs(app, room, state) {
  const offer = state.eggOffer;
  const previous = state.creature;
  let selected = 0;
  room.showEggs(offer);

  const blurb = h('p', { class: 'lead' });
  const nameInput = h('input', { id: 'nombre-criatura', type: 'text', maxlength: '14', autocomplete: 'off', placeholder: 'Mota, Trasto, Nube…' });
  const nameBox = h('div', { class: 't-input' }, nameInput);
  const nameError = h('p', { class: 't-error-msg', role: 'alert', id: 'nombre-criatura-error' });
  nameInput.setAttribute('aria-describedby', 'nombre-criatura-error');
  const nameWrap = h('div', { class: 'field t-input-wrap' },
    h('label', { for: 'nombre-criatura' }, '¿Cómo se va a llamar?'), nameBox, nameError);
  nameInput.addEventListener('input', () => {
    nameWrap.classList.remove('is-error');
    nameBox.classList.remove('is-error');
  });

  const tiles = offer.map((egg, index) => h('button', {
    class: 'item egg', type: 'button', role: 'radio', 'aria-checked': 'false',
    onclick: () => select(index),
  },
    showThumb(h('img', { class: 'item-thumb', alt: '' }), eggThumb(egg)),
    h('span', { class: 'item-name' }, FAMILY_INFO[egg.family].name),
    egg.heir ? h('span', { class: 'item-tag' }, 'Heredero') : null,
  ));

  function select(index) {
    selected = index;
    tiles.forEach((tile, i) => tile.setAttribute('aria-checked', String(i === index)));
    const egg = offer[index];
    blurb.textContent = egg.heir && previous
      ? `Hereda rasgos de ${previous.name}. ${FAMILY_INFO[egg.family].blurb}`
      : `Familia ${FAMILY_INFO[egg.family].name}. ${FAMILY_INFO[egg.family].blurb}`;
    room.selectEgg(index);
  }

  const submit = h('button', { class: 'btn btn-pink btn-wide', type: 'submit' }, 'Empezar a incubar');
  const form = h('form', {
    class: 'card-screen', novalidate: true,
    onsubmit: async (event) => {
      event.preventDefault();
      const name = nameInput.value.trim();
      if (!name) {
        nameError.textContent = 'Ponle un nombre antes de empezar.';
        nameWrap.classList.add('is-error');
        nameBox.classList.add('is-error', 'is-shaking');
        setTimeout(() => nameBox.classList.remove('is-shaking'), 300);
        nameInput.focus();
        return;
      }
      submit.disabled = true;
      const payload = await run(api.pickEgg({ index: selected, name }));
      if (!payload) submit.disabled = false;
    },
  },
    h('h1', {}, 'Elige un huevo'),
    blurb,
    h('div', { class: 'eggs', role: 'radiogroup', 'aria-label': 'Huevos' }, tiles),
    nameWrap,
    submit,
  );
  app.replaceChildren(form);
  room.on('eggPick', select);
  select(0);

  return {
    focusTop: () => form.getBoundingClientRect().top,
    destroy() { room.on('eggPick', null); },
  };
}
