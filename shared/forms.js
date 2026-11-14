// Árbol genealógico: todas las formas que puede tomar una criatura y la
// regla que decide, según la crianza, por qué rama evoluciona.

import { FAMILIES, MONSTER_FAMILIES, isPet } from './dna.js';

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

const BABY_NAMES = { pelusa: 'Pelusín', hoja: 'Brotín', gota: 'Gotín', perro: 'Cachorro', gato: 'Gatito' };

const YOUNG_NAMES = {
  pelusa: { gloton: 'Bolón', jugueton: 'Brincol', tranquilo: 'Arrullo', arisco: 'Zarzo' },
  hoja: { gloton: 'Calabazo', jugueton: 'Trepín', tranquilo: 'Musguel', arisco: 'Cardo' },
  gota: { gloton: 'Globul', jugueton: 'Chapotín', tranquilo: 'Remanso', arisco: 'Erizal' },
  perro: { gloton: 'Tragón', jugueton: 'Trasto', tranquilo: 'Bonachón', arisco: 'Gruñón' },
  gato: { gloton: 'Zampón', jugueton: 'Brincos', tranquilo: 'Ronroneo', arisco: 'Bufido' },
};

const ADULT_EPITHETS = {
  pelusa: { gloton: 'Goloso', jugueton: 'Saltarín', tranquilo: 'Sereno', arisco: 'Bravo' },
  hoja: { gloton: 'Frondoso', jugueton: 'Trepador', tranquilo: 'Florido', arisco: 'Espinoso' },
  gota: { gloton: 'Orondo', jugueton: 'Saltaolas', tranquilo: 'Manso', arisco: 'Bravío' },
  perro: { gloton: 'Grandote', jugueton: 'Atleta', tranquilo: 'Noble', arisco: 'Fiero' },
  gato: { gloton: 'Orondo', jugueton: 'Acróbata', tranquilo: 'Elegante', arisco: 'Salvaje' },
};

// Formas ocultas: adultas fuera de las cuatro ramas, para crianzas raras.
// Se comprueban en este orden al pasar de joven a adulta; la primera que
// se cumple gana. Las mestizas son una por cada pareja de familias.
export const SECRETS = ['radiante', 'sibarita', 'lunar', 'mestiza'];

export const SECRET_INFO = {
  radiante: {
    name: 'Radiante',
    hint: 'Solo aparece con una crianza perfecta: ni un descuido en toda su vida y siempre bien atendida.',
    reason: 'La has criado sin un solo descuido.',
  },
  sibarita: {
    name: 'Sibarita',
    hint: 'Dicen que de joven no quería comer nada más que una cosa: la que más le gusta.',
    reason: 'De joven solo ha comido su comida favorita.',
  },
  lunar: {
    name: 'Lunar',
    hint: 'Nace de un huevo elegido de madrugada y crece en calma.',
    reason: 'Nació de madrugada y la has criado tranquila.',
  },
  mestiza: {
    name: 'Mestiza',
    hint: 'Sale de un huevo heredero que cambió de familia, si se la cuida bien.',
    reason: 'Lleva sangre de dos familias y la has cuidado bien.',
  },
};

const SECRET_NAMES = {
  pelusa: { radiante: 'Nimbo Radiante', sibarita: 'Merengue Sibarita', lunar: 'Plumón Lunar' },
  hoja: { radiante: 'Girasol Radiante', sibarita: 'Higo Sibarita', lunar: 'Helecho Lunar' },
  gota: { radiante: 'Rocío Radiante', sibarita: 'Almíbar Sibarita', lunar: 'Marea Lunar' },
  perro: { radiante: 'Lucero Radiante', sibarita: 'Bollito Sibarita', lunar: 'Aullido Lunar', mestiza: 'Chucho Mestizo' },
  gato: { radiante: 'Michi Radiante', sibarita: 'Bombón Sibarita', lunar: 'Sombra Lunar', mestiza: 'Gato Callejero' },
};

// Familia de la criatura y familia de la que viene.
const MIXED_NAMES = {
  pelusa: { hoja: 'Musgopelo', gota: 'Espumón' },
  hoja: { pelusa: 'Cardolana', gota: 'Nenúfar' },
  gota: { pelusa: 'Nubarrón', hoja: 'Alguita' },
};

// Hora local (0-23) a la que hay que elegir el huevo para que cuente
// como nacida de madrugada.
export const NIGHT_HOURS = [0, 6];

// Calidad mínima de la crianza (media de las necesidades, de 0 a 1).
const RADIANT_QUALITY = 0.7;
const MIXED_QUALITY = 0.6;
const GOURMET_FEEDS = 8;

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
    // Las ocultas cuelgan de la cría: pueden salir de cualquier joven.
    const secret = (id, kind, name, origin = null) => add({
      id, stage: 'adulto', family, parent: babyId, branch: null, path: [], name, secret: kind, origin,
    });
    for (const kind of ['radiante', 'sibarita', 'lunar']) secret(`oculta-${family}-${kind}`, kind, SECRET_NAMES[family][kind]);
    // Mestizas: en los monstruos, una por familia de origen; en las
    // mascotas, una sola (cruce de dos razas).
    if (isPet(family)) {
      secret(`oculta-${family}-mestiza`, 'mestiza', SECRET_NAMES[family].mestiza);
    } else {
      for (const origin of MONSTER_FAMILIES.filter((f) => f !== family)) {
        secret(`oculta-${family}-${origin}`, 'mestiza', MIXED_NAMES[family][origin], origin);
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

// Hijas dentro del árbol normal: las ocultas van aparte.
export function childrenOf(id) {
  return FORM_LIST.filter((form) => form.parent === id && !form.secret);
}

export function secretsOf(family) {
  return FORM_LIST.filter((form) => form.family === family && form.secret);
}

export function emptyCare() {
  return {
    hours: 0,
    sumHunger: 0, sumEnergy: 0, sumHygiene: 0, sumFun: 0,
    feeds: 0, sweets: 0, plays: 0, pets: 0, baths: 0, heals: 0, mistakes: 0, favs: 0,
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

// Calidad de toda la vida hasta ahora (crías y jóvenes), de 0 a 1.
function lifeQuality(creature) {
  const { lifeCare, care } = creature;
  const hours = Math.max(lifeCare.hours + care.hours, 0.0001);
  const sum = lifeCare.sum + care.sumHunger + care.sumEnergy + care.sumHygiene + care.sumFun;
  return sum / hours / 400;
}

// Forma oculta que le toca al dejar de ser joven, o null. `branch` es la
// rama normal que seguiría.
export function secretFor(creature, branch) {
  const { care, genes } = creature;
  const family = genes.family;
  const mistakes = (creature.lifeCare.mistakes ?? 0) + care.mistakes;
  if (mistakes === 0 && lifeQuality(creature) >= RADIANT_QUALITY) return `oculta-${family}-radiante`;
  if (care.feeds >= GOURMET_FEEDS && (care.favs ?? 0) === care.feeds) return `oculta-${family}-sibarita`;
  if (creature.bornNight && branch === 'tranquilo') return `oculta-${family}-lunar`;
  if (isPet(family)) {
    if (genes.origin && genes.origin !== genes.breed && lifeQuality(creature) >= MIXED_QUALITY) return `oculta-${family}-mestiza`;
    return null;
  }
  if (genes.origin && genes.origin !== family && lifeQuality(creature) >= MIXED_QUALITY) {
    return `oculta-${family}-${genes.origin}`;
  }
  return null;
}

// Forma siguiente a partir de la actual, la crianza y el ADN.
export function nextFormId(creature) {
  const { care, genes } = creature;
  const form = getForm(creature.formId);
  if (form.stage === 'huevo') return `cria-${genes.family}`;
  if (form.stage === 'cria') return `joven-${form.family}-${chooseBranch(care, genes)}`;
  if (form.stage === 'joven') {
    const branch = chooseBranch(care, genes);
    return secretFor(creature, branch) ?? `adulto-${form.family}-${form.path[0]}-${branch}`;
  }
  return null;
}

export function nextStage(stage) {
  const index = STAGES.indexOf(stage);
  return index >= 0 && index < STAGES.length - 1 ? STAGES[index + 1] : null;
}

// Pista para el Criadex: cómo se llega a una forma aún no descubierta.
const PET_EGG = { perro: 'Nace de un huevo de perro.', gato: 'Nace de un huevo de gato.' };

export function hintFor(formId) {
  const form = getForm(formId);
  if (form.stage === 'cria') return PET_EGG[form.family] ?? `Nace de un huevo de la familia ${form.family}.`;
  if (form.secret === 'mestiza' && isPet(form.family)) return 'Sale de un huevo heredero que cambió de raza, si se le cuida bien.';
  if (form.secret) return SECRET_INFO[form.secret].hint;
  if (!form.branch) return '';
  return BRANCH_INFO[form.branch].hint;
}
