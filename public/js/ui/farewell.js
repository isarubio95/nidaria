// Despedida: la criatura se ha ido. Explica por qué y da paso al huevo.

import { h } from '../dom.js';
import { CAUSES, bondPhrase, livedSpan } from './memories.js';
import { SPECIES_INFO } from '/shared/breeds.js';
import { speciesOf } from '/shared/dna.js';

export function mountFarewell(app, state, onContinue) {
  const creature = state.creature;
  const lived = (creature.diedAt - creature.bornAt) * state.timeScale;
  const bond = bondPhrase(creature.bond);
  const button = h('button', { class: 'btn btn-primary btn-wide', type: 'button', onclick: onContinue }, 'Empezar otra');
  const card = h('section', { class: 'card-screen', 'aria-labelledby': 'despedida' },
    h('h1', { id: 'despedida' }, `${creature.name} se ha ido`),
    h('p', { class: 'lead' }, `${CAUSES[creature.deathCause] ?? CAUSES.abandono} Vivió ${livedSpan(lived)} y llegó a ser ${creature.formName}.${bond ? ` ${bond}` : ''}`),
    h('p', { class: 'lead' }, `Ha dejado un huevo que hereda parte de sus rasgos: es el de ${SPECIES_INFO[speciesOf(creature.genes)].name.toLowerCase()}. Tus monedas y tus cosas siguen contigo, y podrás recordarla en el Criadex.`),
    button,
  );
  app.replaceChildren(card);
  button.focus({ preventScroll: true });
  return {
    focusTop: () => card.getBoundingClientRect().top,
    destroy() {},
  };
}
