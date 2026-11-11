// Despedida: la criatura se ha ido. Explica por qué y da paso al huevo.

import { h, duration } from '../dom.js';

const CAUSES = {
  hambre: 'Pasó hambre demasiado tiempo.',
  enfermedad: 'Estuvo enferma demasiado tiempo sin medicina.',
  abandono: 'Le faltaron cuidados demasiado tiempo.',
  vejez: 'Vivió su vida entera y se fue tranquila.',
};
const DAY_MS = 24 * 60 * 60 * 1000;

export function mountFarewell(app, state, onContinue) {
  const creature = state.creature;
  const lived = (creature.diedAt - creature.bornAt) * state.timeScale;
  const span = lived < DAY_MS ? 'menos de un día' : duration(lived);
  const button = h('button', { class: 'btn btn-pink btn-wide', type: 'button', onclick: onContinue }, 'Ver los huevos');
  const card = h('section', { class: 'card-screen', 'aria-labelledby': 'despedida' },
    h('h1', { id: 'despedida' }, `${creature.name} se ha ido`),
    h('p', { class: 'lead' }, `${CAUSES[creature.deathCause] ?? CAUSES.abandono} Vivió ${span} y llegó a ser ${creature.formName}.`),
    h('p', { class: 'lead' }, 'Ha dejado un huevo que hereda parte de sus rasgos. Tus monedas, tus cosas y tu Criadex siguen contigo.'),
    button,
  );
  app.replaceChildren(card);
  button.focus({ preventScroll: true });
  return {
    focusTop: () => card.getBoundingClientRect().top,
    destroy() {},
  };
}
