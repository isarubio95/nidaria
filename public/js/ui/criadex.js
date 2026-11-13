// Criadex: el árbol genealógico como colección. Cada familia parte de
// su cría; de ella salen cuatro jóvenes y de cada joven, cuatro adultas.
// Aparte, las formas ocultas de cada familia. En la otra pestaña, los
// recuerdos de las criaturas que ya se fueron.

import { h } from '../dom.js';
import { store } from '../store.js';
import { FAMILIES, FAMILY_INFO } from '/shared/dna.js';
import { FORM_LIST, getForm, childrenOf, secretsOf, hintFor, BRANCH_INFO, STAGE_INFO } from '/shared/forms.js';
import { openSheet } from './sheet.js';
import { dialog } from './dialog.js';
import { tabs } from './widgets.js';
import { mountMemories } from './memories.js';
import { creatureThumb, canonGenes, lazyThumbs } from '../scene/thumbs.js';

const times = (n) => (n === 1 ? 'una vez' : `${n} veces`);

function formsTab(jobs) {
  const state = store.state;
  const creature = state.creature;
  const known = state.criadex;

  // Las formas por las que ha pasado tu criatura se ven con sus colores.
  const genesFor = (form) => {
    if (creature && (creature.history.includes(form.id) || form.id === 'huevo')) return creature.genes;
    return canonGenes(form.family ?? creature?.genes.family ?? 'pelusa');
  };

  function tile(form, extra = '') {
    const isKnown = Boolean(known[form.id]);
    const img = h('img', { alt: '', width: '72', height: '72' });
    jobs.push({ img, make: () => creatureThumb(form.id, genesFor(form), !isKnown) });
    const unknownLabel = form.secret ? '?' : '¿?';
    return h('button', {
      class: `dex ${extra}`.trim(), type: 'button',
      dataset: { known: String(isKnown), current: String(creature?.alive && creature.formId === form.id) },
      'aria-label': isKnown ? form.name : form.secret ? 'Forma oculta sin descubrir' : 'Forma sin descubrir',
      onclick: () => show(form, isKnown),
    }, img, h('span', {}, isKnown ? form.name : unknownLabel));
  }

  function show(form, isKnown) {
    if (!isKnown) {
      let text = 'Elige un huevo para descubrirla.';
      if (form.secret) text = `Una forma oculta de la familia ${FAMILY_INFO[form.family].name}. ${hintFor(form.id)}`;
      else if (form.family) text = `${STAGE_INFO[form.stage].name} de la familia ${FAMILY_INFO[form.family].name}. ${hintFor(form.id)}`;
      dialog({
        title: form.secret ? 'Forma oculta' : 'Forma sin descubrir',
        text,
        art: creatureThumb(form.id, genesFor(form), true),
        actions: [{ label: 'Entendido', value: true, tone: 'pink' }],
      });
      return;
    }
    let how = '';
    if (form.secret) how = `Forma oculta. ${hintFor(form.id)}`;
    else if (form.branch) how = `Rama ${BRANCH_INFO[form.branch].name.toLowerCase()}. ${BRANCH_INFO[form.branch].hint}`;
    else if (form.stage === 'huevo') how = 'De aquí sale todo.';
    dialog({
      title: form.name,
      text: `${how} La has tenido ${times(known[form.id].times)}.`.trim(),
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
      h('h4', { class: 'dex-secret-title' }, 'Formas ocultas'),
      h('div', { class: 'dex-secrets' }, secretsOf(family).map((form) => tile(form))),
    );
  });

  // El huevo es la raíz común de las tres familias.
  const origin = h('section', { class: 'dex-family' },
    h('h3', {}, 'El origen'),
    h('p', {}, 'Todas las criaturas salen de un huevo. Su familia viene en el ADN.'),
    h('div', { class: 'dex-root' }, tile(getForm('huevo'))),
  );
  return [origin, ...families];
}

function formsSummary() {
  const known = store.state.criadex;
  const regular = FORM_LIST.filter((form) => !form.secret);
  const secret = FORM_LIST.filter((form) => form.secret);
  const found = regular.filter((form) => known[form.id]).length;
  const hidden = secret.filter((form) => known[form.id]).length;
  return `Has descubierto ${found} de ${regular.length} formas y ${hidden} de ${secret.length} ocultas. La crianza decide por qué rama sigue cada criatura.`;
}

const MEMORIES_SUB = 'Las criaturas que cuidaste y ya se fueron.';

export function openCriadex(initial = 'formas') {
  let tab = initial;
  let stop = null;
  const body = h('div');

  const bar = tabs([{ id: 'formas', label: 'Formas' }, { id: 'recuerdos', label: 'Recuerdos' }], tab, (id) => {
    tab = id;
    render();
  }, 'Secciones del Criadex');

  const sheet = openSheet({
    title: 'Criadex',
    sub: tab === 'formas' ? formsSummary() : MEMORIES_SUB,
    tabs: bar,
    body,
    onClose: () => stop?.(),
  });
  const sub = sheet.el.querySelector('.sheet-sub');

  function render() {
    stop?.();
    sheet.body.scrollTop = 0;
    if (tab === 'formas') {
      const jobs = [];
      body.replaceChildren(...formsTab(jobs));
      sub.textContent = formsSummary();
      stop = lazyThumbs(jobs, 2);
    } else {
      sub.textContent = MEMORIES_SUB;
      stop = mountMemories(body);
    }
  }

  render();
  return sheet;
}
