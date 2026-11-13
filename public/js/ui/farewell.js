// Despedida: la criatura se ha ido. Explica por qué y da paso al huevo.

import { h } from '../dom.js';
import { CAUSES, bondPhrase, livedSpan } from './memories.js';

export function mountFarewell(app, state, onContinue) {
  const creature = state.creature;
  const lived = (creature.diedAt - creature.bornAt) * state.timeScale;
  const bond = bondPhrase(creature.bond);
  const button = h('button', { class: 'btn btn-pink btn-wide', type: 'button', onclick: onContinue }, 'Ver los huevos');
  const card = h('section', { class: 'card-screen', 'aria-labelledby': 'despedida' },
    h('h1', { id: 'despedida' }, `${creature.name} se ha ido`),
    h('p', { class: 'lead' }, `${CAUSES[creature.deathCause] ?? CAUSES.abandono} Vivió ${livedSpan(lived)} y llegó a ser ${creature.formName}.${bond ? ` ${bond}` : ''}`),
    h('p', { class: 'lead' }, 'Ha dejado un huevo que hereda parte de sus rasgos. Tus monedas y tus cosas siguen contigo, y podrás recordarla en el Criadex.'),
    button,
  );
  app.replaceChildren(card);
  button.focus({ preventScroll: true });
  return {
    focusTop: () => card.getBoundingClientRect().top,
    destroy() {},
  };
}
