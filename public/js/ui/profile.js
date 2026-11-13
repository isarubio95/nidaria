// Ficha de la criatura, y debajo la cuenta del jugador.

import { h, duration } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { FAMILY_INFO, TRAITS } from '/shared/dna.js';
import { BRANCH_INFO } from '/shared/forms.js';
import { getItem } from '/shared/catalog.js';
import { openSheet, closeSheet } from './sheet.js';
import { toast } from './toast.js';
import { settingsSection } from './settings.js';
import { disablePush } from '../pwa.js';

const DAY_MS = 24 * 60 * 60 * 1000;

const fact = (label, value, note) => h('div', { class: 'fact' },
  h('dt', {}, label),
  h('dd', {}, value, note ? h('small', {}, note) : null),
);

export function openProfile() {
  // La parte de la criatura se repinta con cada cambio; los ajustes y la
  // cuenta se pintan una vez para no reiniciar los interruptores.
  const creaturePart = h('div');
  const settings = settingsSection();
  const state0 = store.state;
  const account = [
    h('h3', { class: 'section-title' }, 'Tu cuenta'),
    h('div', { class: 'row', style: { alignItems: 'center', justifyContent: 'space-between' } },
      h('p', { style: { fontWeight: '700' } }, `Has entrado como ${state0.user.name}.`),
      h('button', {
        class: 'btn btn-quiet btn-small', type: 'button',
        onclick: async () => {
          await closeSheet();
          try {
            // Este dispositivo deja de recibir avisos de la cuenta.
            await disablePush();
            await api.logout();
          } finally {
            store.clear();
          }
        },
      }, 'Salir'),
    ),
  ];
  const body = h('div', {}, creaturePart, settings.el, account);
  let off = null;
  const sheet = openSheet({
    title: 'Ficha',
    body,
    onClose: () => {
      off?.();
      settings.destroy();
    },
  });

  function render() {
    const state = store.state;
    const creature = state.creature;
    if (!creature) return;
    const now = store.now();
    const days = Math.floor(((now - creature.bornAt) * state.timeScale) / DAY_MS);

    const input = h('input', { id: 'renombrar', type: 'text', maxlength: '14', value: creature.name, autocomplete: 'off', 'aria-label': 'Nombre' });
    const rename = h('form', {
      class: 'row',
      onsubmit: async (event) => {
        event.preventDefault();
        const name = input.value.trim();
        if (!name || name === creature.name) return;
        const payload = await run(api.rename({ name }));
        if (payload) toast(`Ahora se llama ${payload.state.creature.name}.`);
      },
    },
      h('div', { class: 't-input', style: { flex: '1', minWidth: '0' } }, input),
      h('button', { class: 'btn btn-mint', type: 'submit' }, 'Cambiar'),
    );

    const facts = [
      fact('Forma', creature.formName, `Etapa: ${creature.stageName.toLowerCase()}`),
      fact('Familia', FAMILY_INFO[creature.genes.family].name, FAMILY_INFO[creature.genes.family].blurb),
      fact('Edad', days === 0 ? 'Menos de un día' : duration(days * DAY_MS), creature.generation > 1 ? `Generación ${creature.generation}` : null),
      fact(
        'Carácter',
        creature.genes.traits.map((trait, index) => (index ? TRAITS[trait].name.toLowerCase() : TRAITS[trait].name)).join(' y '),
        creature.genes.traits.map((trait) => TRAITS[trait].blurb).join(' '),
      ),
      fact('Le encanta', creature.known?.fav ? getItem(creature.genes.favFood).name : 'Por descubrir', creature.known?.fav ? null : 'Lo sabrás cuando se lo des de comer.'),
      fact('No soporta', creature.known?.hate ? getItem(creature.genes.hateFood).name : 'Por descubrir'),
      fact('ADN', h('span', { class: 'code num' }, creature.code), 'Su código es único.'),
    ];
    if (creature.tendency && creature.nextStageAt) {
      facts.splice(1, 0, fact(
        'Va camino de',
        `Ser ${BRANCH_INFO[creature.tendency].name.toLowerCase()}`,
        `Evoluciona en ${duration(creature.nextStageAt - now)}. ${BRANCH_INFO[creature.tendency].hint}`,
      ));
    }

    const dev = state.devTools ? [
      h('h3', { class: 'section-title' }, 'Pruebas'),
      h('div', { class: 'row' }, [[1, '1 hora'], [6, '6 horas'], [24, '1 día']].map(([hours, label]) => h('button', {
        class: 'btn btn-small btn-sun', type: 'button',
        onclick: async () => {
          await closeSheet();
          run(api.devSkip({ hours }));
        },
      }, `Adelantar ${label}`))),
    ] : [];

    creaturePart.replaceChildren(
      h('h3', { class: 'section-title' }, 'Nombre'),
      rename,
      h('h3', { class: 'section-title' }, 'Cómo es'),
      h('dl', { class: 'facts' }, facts),
      ...dev,
    );
  }

  off = store.onChange((state) => {
    if (state?.creature) render();
  });
  render();
  return sheet;
}
