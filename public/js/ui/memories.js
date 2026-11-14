// Recuerdos: las criaturas que ya se fueron, con su foto, cuánto vivieron
// y en qué se convirtieron. También pone los textos de la despedida.

import { h, duration } from '../dom.js';
import { api } from '../api.js';
import { run } from '../store.js';
import { bondLevel } from '/shared/bond.js';
import { dialog } from './dialog.js';
import { creatureThumb, lazyThumbs } from '../scene/thumbs.js';

const DAY_MS = 24 * 60 * 60 * 1000;

export const CAUSES = {
  hambre: 'Pasó hambre demasiado tiempo.',
  enfermedad: 'Estuvo enferma demasiado tiempo sin medicina.',
  abandono: 'Le faltaron cuidados demasiado tiempo.',
  vejez: 'Vivió su vida entera y se fue tranquila.',
};

const CAUSES_SHORT = {
  hambre: 'Se fue por pasar hambre.',
  enfermedad: 'Se fue enferma.',
  abandono: 'Se fue por falta de cuidados.',
  vejez: 'Se fue de vieja, tranquila.',
};

const BOND_PHRASES = ['', 'Te tenía cariño.', 'Confiaba en ti.', 'Erais inseparables.'];

export const bondPhrase = (bond) => BOND_PHRASES[bondLevel(bond ?? 0)];

export const livedSpan = (ms) => (ms < DAY_MS ? 'menos de un día' : duration(ms));

function open(entry) {
  const text = [
    CAUSES[entry.cause] ?? CAUSES.abandono,
    `Vivió ${livedSpan(entry.lived)} y llegó a ser ${entry.formName}.`,
    bondPhrase(entry.bond),
  ].filter(Boolean).join(' ');
  dialog({
    title: entry.name,
    text,
    art: creatureThumb(entry.formId, entry.genes),
    actions: [{ label: 'Cerrar', value: true, tone: 'primary' }],
  });
}

// Pinta la lista dentro de `el`. Devuelve una función para parar las
// miniaturas pendientes si se cierra antes de acabar.
export function mountMemories(el) {
  let stop = null;
  let gone = false;
  el.replaceChildren(h('p', { class: 'memory-note' }, 'Buscando recuerdos…'));

  run(api.memorial()).then((payload) => {
    if (gone) return;
    if (!payload) {
      el.replaceChildren(h('p', { class: 'memory-note' }, 'No se han podido cargar los recuerdos. Cierra y vuelve a intentarlo.'));
      return;
    }
    const list = payload.result.creatures;
    if (!list.length) {
      el.replaceChildren(h('p', { class: 'memory-note' },
        'Todavía no se ha ido ninguna criatura. Cuando una termine su vida, la recordarás aquí.'));
      return;
    }
    const jobs = [];
    const rows = list.map((entry) => {
      const img = h('img', { alt: '', width: '64', height: '64' });
      jobs.push({ img, make: () => creatureThumb(entry.formId, entry.genes) });
      const lived = `Vivió ${livedSpan(entry.lived)}${entry.generation > 1 ? ` · Generación ${entry.generation}` : ''}`;
      return h('li', {},
        h('button', { class: 'memory', type: 'button', onclick: () => open(entry) },
          img,
          h('span', { class: 'memory-text' },
            h('strong', {}, entry.name),
            h('span', {}, entry.formName),
            h('small', {}, lived),
            h('small', {}, CAUSES_SHORT[entry.cause] ?? CAUSES_SHORT.abandono),
            entry.heir ? h('small', {}, `Su heredera: ${entry.heir}`) : null,
          ),
        ));
    });
    el.replaceChildren(h('ul', { class: 'memories', 'aria-label': 'Criaturas que se fueron' }, rows));
    stop = lazyThumbs(jobs);
  });

  return () => {
    gone = true;
    stop?.();
  };
}
