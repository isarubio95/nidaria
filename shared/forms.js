// Árbol genealógico: todas las formas que puede tomar una criatura y la
// regla que decide, según la crianza, por qué rama evoluciona.

import { FAMILIES } from './dna.js';

export const STAGES = ['huevo', 'cria', 'joven', 'adulto'];

export const STAGE_INFO = {
  huevo: { name: 'Huevo', durationMs: 45 * 1000 },
  cria: { name: 'Cría', durationMs: 60 * 60 * 1000 },
  joven: { name: 'Joven', durationMs: 24 * 60 * 60 * 1000 },
  adulto: { name: 'Adulta', durationMs: null },
};

// Vida de una adulta: 14 días, más hasta 7 si ha estado bien cuidada.
export const ADULT_LIFE_MS = 14 * 24 * 60 * 60 * 1000;
export const ADULT_LIFE_BONUS_MS = 7 * 24 * 60 * 60 * 1000;

export const BRANCHES = ['gloton', 'jugueton', 'tranquilo', 'arisco'];

export const BRANCH_INFO = {
  gloton: { name: 'Glotona', hint: 'Dale muchos dulces y tenla siempre bien comida.' },
  jugueton: { name: 'Juguetona', hint: 'Juega mucho con ella: minijuegos y juguetes.' },
  tranquilo: { name: 'Tranquila', hint: 'Mímala, báñala y déjala dormir bien.' },
  arisco: { name: 'Arisca', hint: 'Sale cuando pasa hambre, sueño o aburrimiento a menudo.' },
};

const BABY_NAMES = { pelusa: 'Pelusín', hoja: 'Brotín', gota: 'Gotín' };

const YOUNG_NAMES = {
  pelusa: { gloton: 'Bolón', jugueton: 'Brincol', tranquilo: 'Arrullo', arisco: 'Zarzo' },
  hoja: { gloton: 'Calabazo', jugueton: 'Trepín', tranquilo: 'Musguel', arisco: 'Cardo' },
  gota: { gloton: 'Globul', jugueton: 'Chapotín', tranquilo: 'Remanso', arisco: 'Erizal' },
};

const ADULT_EPITHETS = {
  pelusa: { gloton: 'Goloso', jugueton: 'Saltarín', tranquilo: 'Sereno', arisco: 'Bravo' },
  hoja: { gloton: 'Frondoso', jugueton: 'Trepador', tranquilo: 'Florido', arisco: 'Espinoso' },
  gota: { gloton: 'Orondo', jugueton: 'Saltaolas', tranquilo: 'Manso', arisco: 'Bravío' },
};

function buildForms() {
  const forms = new Map();
  const add = (form) => forms.set(form.id, form);
  add({ id: 'huevo', stage: 'huevo', family: null, parent: null, branch: null, path: [], name: 'Huevo' });
  for (const family of FAMILIES) {
    const babyId = `cria-${family}`;
    add({ id: babyId, stage: 'cria', family, parent: 'huevo', branch: null, path: [], name: BABY_NAMES[family] });
    for (const b1 of BRANCHES) {
      const youngId = `joven-${family}-${b1}`;
      const youngName = YOUNG_NAMES[family][b1];
      add({ id: youngId, stage: 'joven', family, parent: babyId, branch: b1, path: [b1], name: youngName });
      for (const b2 of BRANCHES) {
        add({
          id: `adulto-${family}-${b1}-${b2}`,
          stage: 'adulto',
          family,
          parent: youngId,
          branch: b2,
          path: [b1, b2],
          name: `${youngName} ${ADULT_EPITHETS[family][b2]}`,
        });
      }
    }
  }
  return forms;
}

export const FORMS = buildForms();
export const FORM_LIST = [...FORMS.values()];

export function getForm(id) {
  const form = FORMS.get(id);
  if (!form) throw new Error(`Forma desconocida: ${id}`);
  return form;
}

export function childrenOf(id) {
  return FORM_LIST.filter((form) => form.parent === id);
}

export function emptyCare() {
  return {
    hours: 0,
    sumHunger: 0, sumEnergy: 0, sumHygiene: 0, sumFun: 0,
    feeds: 0, sweets: 0, plays: 0, pets: 0, baths: 0, heals: 0, mistakes: 0,
  };
}

export function careAverages(care) {
  const hours = Math.max(care.hours, 0.0001);
  return {
    hunger: care.sumHunger / hours,
    energy: care.sumEnergy / hours,
    hygiene: care.sumHygiene / hours,
    fun: care.sumFun / hours,
  };
}

// Puntúa cada rama con lo que el jugador ha hecho en esta etapa. Las
// cuentas se dividen entre las horas para que una etapa larga no pese más.
export function branchScores(care, genes) {
  const avg = careAverages(care);
  const perDay = (count) => (count / Math.max(care.hours, 0.5)) * 24;
  const mean = (avg.hunger + avg.energy + avg.hygiene + avg.fun) / 4;
  const scores = {
    gloton: perDay(care.sweets) * 1.4 + perDay(care.feeds) * 0.25 + (avg.hunger > 78 ? 3 : 0),
    jugueton: perDay(care.plays) * 1.1 + (avg.fun > 70 ? 3 : 0),
    tranquilo: perDay(care.pets) * 0.35 + perDay(care.baths) * 1.2
      + (avg.energy > 62 ? 2.5 : 0) + (avg.hygiene > 68 ? 2.5 : 0),
    arisco: perDay(care.mistakes) * 2.2 + (mean < 38 ? 8 : 0),
  };
  const traits = genes?.traits ?? [];
  if (traits.includes('gloton')) scores.gloton += 1;
  if (traits.includes('travieso') || traits.includes('curioso')) scores.jugueton += 1;
  if (traits.includes('mimoso') || traits.includes('dormilon')) scores.tranquilo += 1;
  return scores;
}

export function chooseBranch(care, genes) {
  const scores = branchScores(care, genes);
  let best = 'tranquilo';
  let bestScore = -Infinity;
  for (const branch of ['tranquilo', 'jugueton', 'gloton', 'arisco']) {
    if (scores[branch] > bestScore) {
      best = branch;
      bestScore = scores[branch];
    }
  }
  return best;
}

// Forma siguiente a partir de la actual, la crianza y el ADN.
export function nextFormId(formId, care, genes) {
  const form = getForm(formId);
  if (form.stage === 'huevo') return `cria-${genes.family}`;
  if (form.stage === 'cria') return `joven-${form.family}-${chooseBranch(care, genes)}`;
  if (form.stage === 'joven') return `adulto-${form.family}-${form.path[0]}-${chooseBranch(care, genes)}`;
  return null;
}

export function nextStage(stage) {
  const index = STAGES.indexOf(stage);
  return index >= 0 && index < STAGES.length - 1 ? STAGES[index + 1] : null;
}

// Pista para el Criadex: cómo se llega a una forma aún no descubierta.
export function hintFor(formId) {
  const form = getForm(formId);
  if (form.stage === 'cria') return `Nace de un huevo de la familia ${form.family}.`;
  if (!form.branch) return '';
  return BRANCH_INFO[form.branch].hint;
}
