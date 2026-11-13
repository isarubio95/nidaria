// Vínculo: la confianza entre la criatura y quien la cuida. Sube con los
// cuidados, con un tope por día de juego para que no se pueda forzar en
// una tarde, y baja con cada descuido. Desbloquea frases, trucos y más
// vida adulta.

export const BOND_MAX = 100;

// Lo más que puede subir en un día de juego, contado desde que nació.
export const BOND_DAILY_CAP = 20;

// Lo que resta cada descuido (una necesidad que se pone crítica o caer
// rendida de cansancio).
export const BOND_MISTAKE = 3;

// Vida adulta extra con el vínculo al máximo. Crece en proporción.
export const BOND_LIFE_MS = 4 * 24 * 60 * 60 * 1000;

// Por debajo de este vínculo, a una criatura tímida le cuesta el doble.
export const SHY_UNTIL = 50;

const DAY_MS = 24 * 60 * 60 * 1000;

// `reached` es el aviso al llegar al nivel; {name} es la criatura.
export const BOND_LEVELS = [
  { min: 0, name: 'Os estáis conociendo', unlocks: '', reached: '' },
  { min: 20, name: 'Te tiene cariño', unlocks: 'Te dice cosas bonitas.', reached: '{name} ya te tiene cariño.' },
  {
    min: 50, name: 'Confía en ti', unlocks: 'Si la tocas, a veces te enseña un truco.',
    reached: '{name} ya confía en ti. Tócala y a veces te enseñará un truco.',
  },
  {
    min: 80, name: 'Inseparables', unlocks: 'Sabe un truco nuevo y vivirá más tiempo.',
    reached: '{name} y tú ya sois inseparables.',
  },
];

// Lo que dice según el vínculo, a partir del nivel 1. {player} es quien
// la cuida.
export const BOND_LINES = {
  1: ['Me alegra verte.', 'Me gusta que estés aquí.'],
  2: ['¡{player}! Te estaba esperando.', 'Contigo estoy a gusto.'],
  3: ['Eres mi persona favorita, {player}.', 'No te vayas, ¿vale?'],
};

export const TRICK_LINES = ['¡Mira lo que sé hacer!', '¡Ta-chán!'];

// Trucos que sabe a cada nivel: el giro desde el 2, los saltitos en el 3.
export const TRICKS = { 2: ['giro'], 3: ['giro', 'saltitos'] };

// Probabilidad de hacer un truco al tocarla, y espera mínima entre dos.
export const TRICK_CHANCE = 0.4;
export const TRICK_GAP_MS = 20 * 1000;

// Cuánto suma cada cuidado antes del tope diario.
export const BOND_GAINS = {
  pet: 1,
  feed: 1,
  favorite: 2,
  bathe: 1.5,
  heal: 2,
  toy: 1.5,
  game: 2,
  win: 3,
};

export const bondOf = (creature) => creature.bond ?? 0;

export function bondLevel(bond) {
  let level = 0;
  for (let i = 0; i < BOND_LEVELS.length; i++) if (bond >= BOND_LEVELS[i].min) level = i;
  return level;
}

// Suma vínculo por un cuidado. Devuelve lo que ha subido de verdad.
export function gainBond(creature, amount, now, scale = 1) {
  if (!creature.alive || amount <= 0) return 0;
  const day = Math.floor(((now - creature.bornAt) * scale) / DAY_MS);
  if (creature.bondDay !== day) {
    creature.bondDay = day;
    creature.bondToday = 0;
  }
  const bond = bondOf(creature);
  let gain = amount;
  if (creature.genes.traits.includes('timido') && bond < SHY_UNTIL) gain *= 0.5;
  gain = Math.min(gain, BOND_DAILY_CAP - (creature.bondToday ?? 0), BOND_MAX - bond);
  if (gain <= 0) return 0;
  creature.bond = +(bond + gain).toFixed(3);
  creature.bondToday = +((creature.bondToday ?? 0) + gain).toFixed(3);
  return gain;
}

// Resta vínculo por descuidos. Lo usa la simulación.
export function loseBond(creature, mistakes) {
  if (mistakes <= 0) return;
  creature.bond = Math.max(0, bondOf(creature) - BOND_MISTAKE * mistakes);
}

// Fin real de la vida adulta: el previsto al hacerse adulta, más lo que
// le da el vínculo de ahora. Si el vínculo baja, el final se adelanta.
export function lifeEnd(creature, scale = 1) {
  if (!creature.lifeEndsAt) return null;
  return creature.lifeEndsAt + (BOND_LIFE_MS * (bondOf(creature) / BOND_MAX)) / scale;
}
