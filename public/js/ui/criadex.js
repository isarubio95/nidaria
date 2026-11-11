// Criadex: el árbol genealógico como colección. Cada familia parte de
// su cría; de ella salen cuatro jóvenes y de cada joven, cuatro adultas.

import { h } from '../dom.js';
import { store } from '../store.js';
import { FAMILIES, FAMILY_INFO } from '/shared/dna.js';
import { FORM_LIST, getForm, childrenOf, hintFor, BRANCH_INFO, STAGE_INFO } from '/shared/forms.js';
import { openSheet } from './sheet.js';
import { dialog } from './dialog.js';
import { creatureThumb, canonGenes, lazyThumbs } from '../scene/thumbs.js';

export function openCriadex() {
  const state = store.state;
  const creature = state.creature;
  const known = state.criadex;
  const total = FORM_LIST.length;
  const found = FORM_LIST.filter((form) => known[form.id]).length;
  const jobs = [];

  // Las formas por las que ha pasado tu criatura se ven con sus colores.
  const genesFor = (form) => {
    if (creature && (creature.history.includes(form.id) || form.id === 'huevo')) return creature.genes;
    return canonGenes(form.family ?? creature?.genes.family ?? 'pelusa');
  };

  function tile(form, extra = '') {
    const isKnown = Boolean(known[form.id]);
    const img = h('img', { alt: '', width: '72', height: '72' });
    jobs.push({ img, make: () => creatureThumb(form.id, genesFor(form), !isKnown) });
    return h('button', {
      class: `dex ${extra}`.trim(), type: 'button',
      dataset: { known: String(isKnown), current: String(creature?.alive && creature.formId === form.id) },
      'aria-label': isKnown ? form.name : 'Forma sin descubrir',
      onclick: () => show(form, isKnown),
    }, img, h('span', {}, isKnown ? form.name : '¿?'));
  }

  function show(form, isKnown) {
    if (!isKnown) {
      dialog({
        title: 'Forma sin descubrir',
        text: form.family ? `${STAGE_INFO[form.stage].name} de la familia ${FAMILY_INFO[form.family].name}. ${hintFor(form.id)}` : 'Elige un huevo para descubrirla.',
        art: creatureThumb(form.id, genesFor(form), true),
        actions: [{ label: 'Entendido', value: true, tone: 'pink' }],
      });
      return;
    }
    const times = known[form.id].times;
    const how = form.branch ? `Rama ${BRANCH_INFO[form.branch].name.toLowerCase()}. ${BRANCH_INFO[form.branch].hint}` : '';
    dialog({
      title: form.name,
      text: `${form.stage === 'huevo' ? 'De aquí sale todo.' : how} La has tenido ${times === 1 ? 'una vez' : `${times} veces`}.`.trim(),
      art: creatureThumb(form.id, genesFor(form)),
      actions: [{ label: 'Cerrar', value: true, tone: 'pink' }],
    });
  }

  const families = FAMILIES.map((family) => {
    const baby = getForm(`cria-${family}`);
    const columns = childrenOf(baby.id).map((young) => h('div', { class: 'dex-col' },
      tile(young, 'dex-young'),
      childrenOf(young.id).map((adult) => tile(adult)),
    ));
    return h('section', { class: 'dex-family' },
      h('h3', {}, `Familia ${FAMILY_INFO[family].name}`),
      h('p', {}, FAMILY_INFO[family].blurb),
      h('div', { class: 'dex-root' }, tile(baby)),
      h('div', { class: 'dex-grid' }, columns),
    );
  });

  // El huevo es la raíz común de las tres familias.
  const origin = h('section', { class: 'dex-family' },
    h('h3', {}, 'El origen'),
    h('p', {}, 'Todas las criaturas salen de un huevo. Su familia viene en el ADN.'),
    h('div', { class: 'dex-root' }, tile(getForm('huevo'))),
  );

  let stop = null;
  const sheet = openSheet({
    title: 'Criadex',
    sub: `Has descubierto ${found} de ${total} formas. La crianza decide por qué rama sigue cada criatura.`,
    body: [origin, ...families],
    onClose: () => stop?.(),
  });
  stop = lazyThumbs(jobs, 2);
  return sheet;
}
