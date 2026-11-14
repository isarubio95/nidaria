// ADN de una criatura: un objeto de genes que fija familia, colores,
// proporciones y carácter. Los genes se heredan con mutaciones.

import { createRng, hashString, mix, clamp } from './rng.js';
import { breedsOf, getBreed } from './breeds.js';

// Familias de monstruo y de mascota. Cada familia es una raíz del árbol
// (ver forms.js); perros y gatos son una familia cada uno y su aspecto
// sale de la raza.
export const MONSTER_FAMILIES = ['pelusa', 'hoja', 'gota'];
export const PET_FAMILIES = ['perro', 'gato'];
export const FAMILIES = [...MONSTER_FAMILIES, ...PET_FAMILIES];

export const FAMILY_INFO = {
  pelusa: { name: 'Pelusa', blurb: 'Zorros de pelo encendido y mucho genio.', hues: [[4, 40], [196, 212]], species: 'monstruo' },
  hoja: { name: 'Hoja', blurb: 'Pequeños dinosaurios con hojas por cresta.', hues: [[78, 160]], species: 'monstruo' },
  gota: { name: 'Gota', blurb: 'Anfibios lisos, con aletas, de agua.', hues: [[186, 228], [232, 248]], species: 'monstruo' },
  perro: { name: 'Perro', blurb: 'Leal y con ganas de jugar.', hues: [], species: 'perro' },
  gato: { name: 'Gato', blurb: 'Curioso y muy suyo.', hues: [], species: 'gato' },
};

export const isPet = (family) => PET_FAMILIES.includes(family);

// Especie de una familia o de un ADN.
export function speciesOf(subject) {
  const family = typeof subject === 'string' ? subject : subject?.family;
  return FAMILY_INFO[family]?.species ?? 'monstruo';
}

// Raza y capa de una mascota, o null.
export function breedOf(genes) {
  return genes?.breed ? getBreed(genes.breed) : null;
}

// Nombre para enseñar: la raza en las mascotas, la familia en el resto.
export function kindName(genes) {
  return breedOf(genes)?.name ?? FAMILY_INFO[genes.family]?.name ?? '';
}

export const TRAITS = {
  gloton: { name: 'Glotona', blurb: 'Le entra hambre antes.' },
  dormilon: { name: 'Dormilona', blurb: 'Se cansa antes y duerme profundo.' },
  travieso: { name: 'Traviesa', blurb: 'Se aburre enseguida.' },
  mimoso: { name: 'Mimosa', blurb: 'Las caricias le alegran el doble.' },
  presumido: { name: 'Presumida', blurb: 'Odia estar sucia.' },
  fuerte: { name: 'Fuerte', blurb: 'Aguanta mejor cuando algo le falta.' },
  timido: { name: 'Tímida', blurb: 'Tarda en coger confianza.' },
  curioso: { name: 'Curiosa', blurb: 'Jugar le divierte más.' },
};

const TRAIT_IDS = Object.keys(TRAITS);
const PATTERNS = ['liso', 'motas', 'rayas', 'barriga', 'calcetines'];
const FOODS = ['manzana', 'zanahoria', 'pan', 'arroz', 'sopa', 'pescado', 'tarta', 'helado', 'galleta'];

function pickHue(rng, family) {
  const band = rng.pick(FAMILY_INFO[family].hues);
  return Math.round(rng.range(band[0], band[1]));
}

// Color en hexadecimal a tono, saturación y luz (0-360, 0-100, 0-100).
export function hexToHsl(hex) {
  const n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) / 255;
  const g = ((n >> 8) & 255) / 255;
  const b = (n & 255) / 255;
  const hi = Math.max(r, g, b);
  const lo = Math.min(r, g, b);
  const l = (hi + lo) / 2;
  let h = 0;
  let s = 0;
  if (hi !== lo) {
    const d = hi - lo;
    s = l > 0.5 ? d / (2 - hi - lo) : d / (hi + lo);
    if (hi === r) h = (g - b) / d + (g < b ? 6 : 0);
    else if (hi === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60;
  }
  return [Math.round(h) % 360, Math.round(s * 100), Math.round(l * 100)];
}

// Los colores de una mascota (para el huevo y la tarjeta) salen de su capa.
function coatTone(breed, coatIndex) {
  const coat = breed.coats[coatIndex];
  const [hue, sat, light] = hexToHsl(coat.base);
  const second = coat.paint.find(([, color]) => typeof color === 'string' && color !== coat.base);
  const hue2 = second ? hexToHsl(second[1])[0] : hue;
  return { hue, sat, light, hue2 };
}

function pickPet(rng, family, breedId) {
  const breed = breedId ? getBreed(breedId) : rng.pick(breedsOf(family));
  const coat = rng.int(0, breed.coats.length - 1);
  const eyes = rng.int(0, breed.eyes.length - 1);
  return { breed: breed.id, coat, eyes, ...coatTone(breed, coat) };
}

// `family` puede faltar: entonces sale una familia de monstruo al azar.
export function createGenes(seed, family) {
  const rng = createRng(seed);
  const fam = family ?? rng.pick(MONSTER_FAMILIES);
  const pet = isPet(fam);
  const hue = pet ? 0 : pickHue(rng, fam);
  const first = rng.pick(TRAIT_IDS);
  let second = rng.pick(TRAIT_IDS);
  while (second === first) second = rng.pick(TRAIT_IDS);
  const fav = rng.pick(FOODS);
  let hate = rng.pick(FOODS);
  while (hate === fav) hate = rng.pick(FOODS);
  const genes = {
    v: 1,
    seed: seed >>> 0,
    family: fam,
    hue,
    sat: Math.round(rng.range(52, 74)),
    light: Math.round(rng.range(62, 74)),
    hue2: Math.round((hue + rng.pick([-150, -40, -24, 24, 40, 150, 180]) + 360) % 360),
    pattern: rng.pick(PATTERNS),
    patternAmt: +rng.range(0.25, 1).toFixed(2),
    build: {
      round: +rng.range(0, 1).toFixed(2),
      head: +rng.range(0, 1).toFixed(2),
      ears: +rng.range(0, 1).toFixed(2),
      eyes: +rng.range(0, 1).toFixed(2),
      eyeGap: +rng.range(0, 1).toFixed(2),
      tail: +rng.range(0, 1).toFixed(2),
    },
    eyeStyle: rng.int(0, 2),
    traits: [first, second],
    favFood: fav,
    hateFood: hate,
  };
  if (pet) Object.assign(genes, pickPet(rng, fam));
  return genes;
}

// Huevo heredero: conserva casi todo de su progenitor y muta un poco.
// Si cambia de familia, `origin` recuerda la de su progenitor.
// Las mascotas no cambian de especie: lo que puede cambiar es la raza, y
// entonces `origin` guarda la raza del progenitor.
export function inheritGenes(parent, seed) {
  if (isPet(parent.family)) return inheritPet(parent, seed);
  // Semilla derivada: si usara la misma que `createGenes`, las dos
  // secuencias coincidirían y la mutación no sería independiente.
  const rng = createRng(mix(seed, 0x51ed270b));
  const fresh = createGenes(seed);
  const keepFamily = rng.chance(0.75);
  const family = keepFamily ? parent.family : fresh.family;
  const drift = (value, amount) => +clamp(value + rng.range(-amount, amount), 0, 1).toFixed(2);
  const build = {};
  for (const key of Object.keys(parent.build)) build[key] = drift(parent.build[key], 0.18);
  const inherited = rng.pick(parent.traits);
  let other = rng.pick(TRAIT_IDS);
  while (other === inherited) other = rng.pick(TRAIT_IDS);
  const genes = {
    ...fresh,
    family,
    hue: keepFamily ? Math.round((parent.hue + rng.range(-16, 16) + 360) % 360) : pickHue(rng, family),
    sat: Math.round(clamp(parent.sat + rng.range(-6, 6), 48, 78)),
    light: Math.round(clamp(parent.light + rng.range(-4, 4), 58, 76)),
    hue2: rng.chance(0.6) ? parent.hue2 : fresh.hue2,
    pattern: rng.chance(0.65) ? parent.pattern : fresh.pattern,
    patternAmt: drift(parent.patternAmt, 0.2),
    build,
    eyeStyle: rng.chance(0.7) ? parent.eyeStyle : fresh.eyeStyle,
    traits: [inherited, other],
    favFood: rng.chance(0.5) ? parent.favFood : fresh.favFood,
    hateFood: fresh.hateFood === fresh.favFood ? parent.hateFood : fresh.hateFood,
  };
  if (family !== parent.family) genes.origin = parent.family;
  return genes;
}

function inheritPet(parent, seed) {
  const rng = createRng(mix(seed, 0x51ed270b));
  const fresh = createGenes(seed, parent.family);
  const keepBreed = rng.chance(0.75) && getBreed(parent.breed);
  const drift = (value, amount) => +clamp(value + rng.range(-amount, amount), 0, 1).toFixed(2);
  const build = {};
  for (const key of Object.keys(parent.build)) build[key] = drift(parent.build[key], 0.18);
  const inherited = rng.pick(parent.traits);
  let other = rng.pick(TRAIT_IDS);
  while (other === inherited) other = rng.pick(TRAIT_IDS);
  const genes = {
    ...fresh,
    build,
    eyeStyle: rng.chance(0.7) ? parent.eyeStyle : fresh.eyeStyle,
    traits: [inherited, other],
    favFood: rng.chance(0.5) ? parent.favFood : fresh.favFood,
    hateFood: fresh.hateFood === fresh.favFood ? parent.hateFood : fresh.hateFood,
  };
  if (keepBreed) {
    const breed = getBreed(parent.breed);
    const coat = rng.chance(0.7) ? parent.coat : rng.int(0, breed.coats.length - 1);
    const eyes = rng.chance(0.7) ? parent.eyes : rng.int(0, breed.eyes.length - 1);
    Object.assign(genes, { breed: breed.id, coat, eyes }, coatTone(breed, coat));
  } else if (genes.breed !== parent.breed) {
    genes.origin = parent.breed;
  }
  if (genes.favFood === genes.hateFood) genes.hateFood = FOODS.find((food) => food !== genes.favFood);
  return genes;
}

const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
// Letra de cada familia en el código. Perro, C de can; gato, M de minino.
const CODE_LETTER = { pelusa: 'P', hoja: 'H', gota: 'G', perro: 'C', gato: 'M' };

// Código corto y legible que identifica un ADN (sin 0, 1, I ni O).
export function dnaCode(genes) {
  const fields = [
    genes.family, genes.hue, genes.sat, genes.light, genes.hue2, genes.pattern,
    genes.patternAmt, genes.build, genes.eyeStyle, genes.traits, genes.seed,
  ];
  if (genes.breed) fields.push(genes.breed, genes.coat, genes.eyes);
  const stable = JSON.stringify(fields);
  let h = hashString(stable);
  let out = '';
  for (let i = 0; i < 6; i++) {
    out += ALPHABET[h % 32];
    h = (Math.floor(h / 32) ^ hashString(out + stable.length)) >>> 0;
  }
  return `${CODE_LETTER[genes.family] ?? genes.family[0].toUpperCase()}-${out.slice(0, 3)}-${out.slice(3)}`;
}

export function hsl(h, s, l) {
  return `hsl(${h} ${s}% ${l}%)`;
}

export function genePalette(genes) {
  return {
    body: hsl(genes.hue, genes.sat, genes.light),
    bodyDark: hsl(genes.hue, clamp(genes.sat + 4, 0, 90), clamp(genes.light - 16, 20, 90)),
    bodyLight: hsl(genes.hue, clamp(genes.sat - 10, 0, 90), clamp(genes.light + 14, 20, 94)),
    accent: hsl(genes.hue2, clamp(genes.sat + 6, 0, 90), clamp(genes.light - 4, 20, 90)),
    belly: hsl(genes.hue, clamp(genes.sat - 22, 10, 90), clamp(genes.light + 20, 20, 95)),
  };
}
