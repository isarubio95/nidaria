// Empezar una criatura: al principio y cada vez que una se va. Primero se
// elige especie (perro, gato o monstruo) en tres tarjetas grandes con un
// adulto de muestra; después aparece su huevo, ya decidido, y solo falta
// ponerle nombre. La raza no se sabe hasta que nace.

import { h } from '../dom.js';
import { api } from '../api.js';
import { run } from '../store.js';
import { SPECIES, SPECIES_INFO, breedsOf } from '/shared/breeds.js';
import { MONSTER_FAMILIES } from '/shared/dna.js';
import { FORM_LIST } from '/shared/forms.js';
import { creatureThumb, showThumb, breedGenes, canonGenes } from '../scene/thumbs.js';

// El adulto que sale en cada tarjeta.
const SAMPLE = {
  perro: { formId: 'adulto-perro-jugueton-tranquilo', genes: () => breedGenes('perro', 'husky', 0) },
  gato: { formId: 'adulto-gato-jugueton-tranquilo', genes: () => breedGenes('gato', 'siames', 0) },
  monstruo: { formId: 'adulto-pelusa-jugueton-jugueton', genes: () => canonGenes('pelusa') },
};

const monsterForms = FORM_LIST.filter((form) => MONSTER_FAMILIES.includes(form.family)).length;

const COUNT = {
  perro: `${breedsOf('perro').length} razas`,
  gato: `${breedsOf('gato').length} razas`,
  monstruo: `3 familias y ${monsterForms} formas`,
};

const EGG_TEXT = {
  perro: 'La raza es una sorpresa: la verás cuando nazca.',
  gato: 'La raza es una sorpresa: la verás cuando nazca.',
  monstruo: 'Puede salir de la familia pelusa, hoja o gota. Lo verás cuando nazca.',
};

const THUMB = 320;

// `relayout`: avisa de que la tarjeta ha cambiado de alto (la cámara se
// vuelve a encuadrar en el hueco libre).
export function mountEggs(app, room, state, relayout = () => {}) {
  const offer = state.eggOffer;
  const previous = state.creature;
  const bySpecies = Object.fromEntries(offer.map((egg) => [egg.species, egg]));
  let step = null;

  function chooseSpecies() {
    step = 'species';
    room.showEmpty();
    room.on('eggPick', null);
    const cards = SPECIES.map((species) => {
      const info = SPECIES_INFO[species];
      const egg = bySpecies[species];
      const sample = SAMPLE[species];
      const art = showThumb(h('img', { class: 'species-art', alt: '', width: String(THUMB), height: String(THUMB) }), creatureThumb(sample.formId, sample.genes(), false, { size: THUMB }));
      return h('button', {
        class: 'species-card', type: 'button', dataset: { species },
        onclick: () => showEgg(species),
      },
        h('span', { class: 'species-stage' }, art),
        h('span', { class: 'species-text' },
          h('span', { class: 'species-name' }, info.plural),
          h('span', { class: 'species-count num' }, COUNT[species]),
          h('span', { class: 'species-blurb' }, info.blurb),
          egg?.heir && previous ? h('span', { class: 'species-tag' }, `Heredero de ${previous.name}`) : null,
        ),
      );
    });
    const screen = h('div', { class: 'card-screen species-screen' },
      h('h1', {}, previous ? '¿Qué vas a criar ahora?' : '¿Qué quieres criar?'),
      h('p', { class: 'lead' }, 'Elige una especie y te llevas su huevo.'),
      h('div', { class: 'species', role: 'list' }, cards.map((card) => h('div', { role: 'listitem' }, card))),
    );
    app.replaceChildren(screen);
    current = screen;
    relayout();
  }

  function showEgg(species) {
    step = species;
    const egg = bySpecies[species];
    room.showEggs([egg]);
    room.selectEgg(0);
    room.on('eggPick', null);
    const info = SPECIES_INFO[species];

    const nameInput = h('input', { id: 'nombre-criatura', type: 'text', maxlength: '14', autocomplete: 'off', placeholder: species === 'perro' ? 'Toby, Luna, Rayo…' : species === 'gato' ? 'Misi, Simba, Nube…' : 'Chispa, Trasto, Brasa…' });
    const nameBox = h('div', { class: 't-input' }, nameInput);
    const nameError = h('p', { class: 't-error-msg', role: 'alert', id: 'nombre-criatura-error' });
    nameInput.setAttribute('aria-describedby', 'nombre-criatura-error');
    const nameWrap = h('div', { class: 'field t-input-wrap' },
      h('label', { for: 'nombre-criatura' }, '¿Cómo se va a llamar?'), nameBox, nameError);
    nameInput.addEventListener('input', () => {
      nameWrap.classList.remove('is-error');
      nameBox.classList.remove('is-error');
    });

    const submit = h('button', { class: 'btn btn-primary btn-wide', type: 'submit' }, 'Empezar a incubar');
    const lead = egg.heir && previous
      ? `Es el heredero de ${previous.name}: lleva parte de sus rasgos. ${EGG_TEXT[species]}`
      : EGG_TEXT[species];
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
        const payload = await run(api.pickEgg({ species, name }));
        if (!payload) submit.disabled = false;
      },
    },
      h('h1', {}, `Tu huevo de ${info.name.toLowerCase()}`),
      h('p', { class: 'lead' }, lead),
      nameWrap,
      submit,
      h('button', { class: 'text-link', type: 'button', onclick: chooseSpecies }, 'Elegir otra especie'),
    );
    app.replaceChildren(form);
    current = form;
    relayout();
  }

  let current = null;
  chooseSpecies();

  return {
    focusTop: () => current?.getBoundingClientRect().top ?? 0,
    get step() { return step; },
    destroy() { room.on('eggPick', null); },
  };
}
