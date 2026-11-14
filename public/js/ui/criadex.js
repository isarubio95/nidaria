// Criadex: el árbol genealógico como colección. Cada familia parte de
// su cría; de ella salen cuatro jóvenes y de cada joven, cuatro adultas.
// Aparte, las formas ocultas de cada familia. Las razas de perros y gatos
// se coleccionan en su pestaña, y en la última están los recuerdos de las
// criaturas que ya se fueron.

import { h } from '../dom.js';
import { store } from '../store.js';
import { FAMILIES, FAMILY_INFO, MONSTER_FAMILIES } from '/shared/dna.js';
import { SPECIES_INFO, breedsOf } from '/shared/breeds.js';
import { FORM_LIST, getForm, childrenOf, secretsOf, hintFor, BRANCH_INFO, STAGE_INFO } from '/shared/forms.js';
import { openSheet } from './sheet.js';
import { dialog } from './dialog.js';
import { tabs } from './widgets.js';
import { mountMemories } from './memories.js';
import { creatureThumb, canonGenes, breedGenes, lazyThumbs } from '../scene/thumbs.js';

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
      const of = MONSTER_FAMILIES.includes(form.family) ? `de la familia ${FAMILY_INFO[form.family].name}` : `de ${SPECIES_INFO[form.family].name.toLowerCase()}`;
      if (form.secret) text = `Una forma oculta ${of}. ${hintFor(form.id)}`;
      else if (form.family) text = `${STAGE_INFO[form.stage].name} ${of}. ${hintFor(form.id)}`;
      dialog({
        title: form.secret ? 'Forma oculta' : 'Forma sin descubrir',
        text,
        art: creatureThumb(form.id, genesFor(form), true),
        actions: [{ label: 'Entendido', value: true, tone: 'primary' }],
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
      actions: [{ label: 'Cerrar', value: true, tone: 'primary' }],
    });
  }

  const families = FAMILIES.map((family) => {
    const baby = getForm(`cria-${family}`);
    const columns = childrenOf(baby.id).map((young) => h('div', { class: 'dex-col' },
      tile(young, 'dex-young'),
      childrenOf(young.id).map((adult) => tile(adult)),
    ));
    const monster = MONSTER_FAMILIES.includes(family);
    return h('section', { class: 'dex-family' },
      h('h3', {}, monster ? `Monstruos · Familia ${FAMILY_INFO[family].name}` : SPECIES_INFO[family].plural),
      h('p', {}, monster ? FAMILY_INFO[family].blurb : 'Las formas por las que pasa según cómo lo críes, sea de la raza que sea.'),
      h('div', { class: 'dex-root' }, tile(baby)),
      h('div', { class: 'dex-grid' }, columns),
      h('h4', { class: 'dex-secret-title' }, 'Formas ocultas'),
      h('div', { class: 'dex-secrets' }, secretsOf(family).map((form) => tile(form))),
    );
  });

  // El huevo es la raíz común de todas las familias.
  const origin = h('section', { class: 'dex-family' },
    h('h3', {}, 'El origen'),
    h('p', {}, 'Todas las criaturas salen de un huevo. Su familia o su raza viene en el ADN.'),
    h('div', { class: 'dex-root' }, tile(getForm('huevo'))),
  );
  return [origin, ...families];
}

// Las razas descubiertas: cuentan al nacer una criatura de esa raza.
function breedsTab(jobs) {
  const known = store.state.criadex;
  const creature = store.state.creature;
  return ['perro', 'gato'].map((species) => {
    const list = breedsOf(species);
    const found = list.filter((breed) => known[`raza:${breed.id}`]).length;
    const tiles = list.map((breed) => {
      const isKnown = Boolean(known[`raza:${breed.id}`]);
      const mine = creature?.genes.breed === breed.id;
      const genes = mine ? creature.genes : breedGenes(species, breed.id, 0);
      const formId = `adulto-${species}-jugueton-tranquilo`;
      const img = h('img', { alt: '', width: '72', height: '72' });
      jobs.push({ img, make: () => creatureThumb(formId, genes, !isKnown) });
      return h('button', {
        class: 'dex', type: 'button',
        dataset: { known: String(isKnown), current: String(Boolean(mine && creature.alive)) },
        'aria-label': isKnown ? breed.name : 'Raza sin descubrir',
        onclick: () => dialog({
          title: isKnown ? breed.name : 'Raza sin descubrir',
          text: isKnown
            ? `La has criado ${times(known[`raza:${breed.id}`].times)}. Capas: ${breed.coats.map((coat) => coat.name.toLowerCase()).join(', ')}.`
            : `Sale de un huevo de ${SPECIES_INFO[species].name.toLowerCase()}, si hay suerte.`,
          art: creatureThumb(formId, genes, !isKnown),
          actions: [{ label: isKnown ? 'Cerrar' : 'Entendido', value: true, tone: 'primary' }],
        }),
      }, img, h('span', {}, isKnown ? breed.name : '¿?'));
    });
    return h('section', { class: 'dex-family' },
      h('h3', {}, SPECIES_INFO[species].plural),
      h('p', {}, `Has descubierto ${found} de ${list.length} razas. La raza la decide el huevo.`),
      h('div', { class: 'dex-breeds' }, tiles),
    );
  });
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

  const bar = tabs([{ id: 'formas', label: 'Formas' }, { id: 'razas', label: 'Razas' }, { id: 'recuerdos', label: 'Recuerdos' }], tab, (id) => {
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
      stop = lazyThumbs(jobs);
    } else if (tab === 'razas') {
      const jobs = [];
      body.replaceChildren(...breedsTab(jobs));
      sub.textContent = 'Perros y gatos de todas las razas. Cada huevo trae una.';
      stop = lazyThumbs(jobs);
    } else {
      sub.textContent = MEMORIES_SUB;
      stop = mountMemories(body);
    }
  }

  render();
  return sheet;
}
