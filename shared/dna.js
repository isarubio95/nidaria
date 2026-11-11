// ADN de una criatura: un objeto de genes que fija familia, colores,
// proporciones y carácter. Los genes se heredan con mutaciones.

import { createRng, hashString, mix, clamp } from './rng.js';

export const FAMILIES = ['pelusa', 'hoja', 'gota'];

export const FAMILY_INFO = {
  pelusa: { name: 'Pelusa', blurb: 'Blandas y de pelo esponjoso.', hues: [[8, 48], [318, 352]] },
  hoja: { name: 'Hoja', blurb: 'Brotan con hojas y tallos.', hues: [[72, 158]] },
  gota: { name: 'Gota', blurb: 'Lisas y de agua.', hues: [[172, 232], [246, 286]] },
};

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

export function createGenes(seed, family) {
  const rng = createRng(seed);
  const fam = family ?? rng.pick(FAMILIES);
  const hue = pickHue(rng, fam);
  const first = rng.pick(TRAIT_IDS);
  let second = rng.pick(TRAIT_IDS);
  while (second === first) second = rng.pick(TRAIT_IDS);
  const fav = rng.pick(FOODS);
  let hate = rng.pick(FOODS);
  while (hate === fav) hate = rng.pick(FOODS);
  return {
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
}

// Huevo heredero: conserva casi todo de su progenitor y muta un poco.
export function inheritGenes(parent, seed) {
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
  return {
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
}

const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

// Código corto y legible que identifica un ADN (sin 0, 1, I ni O).
export function dnaCode(genes) {
  const stable = JSON.stringify([
    genes.family, genes.hue, genes.sat, genes.light, genes.hue2, genes.pattern,
    genes.patternAmt, genes.build, genes.eyeStyle, genes.traits, genes.seed,
  ]);
  let h = hashString(stable);
  let out = '';
  for (let i = 0; i < 6; i++) {
    out += ALPHABET[h % 32];
    h = (Math.floor(h / 32) ^ hashString(out + stable.length)) >>> 0;
  }
  return `${genes.family[0].toUpperCase()}-${out.slice(0, 3)}-${out.slice(3)}`;
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
