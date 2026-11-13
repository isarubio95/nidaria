// Genera vectores de referencia con las reglas originales de shared/ para
// que las pruebas de Kotlin comprueben que calculan lo mismo.
// Uso: node tools/golden.mjs  (desde android/)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRng, hashString, mix, roll } from '../../shared/rng.js';
import { createGenes } from '../../shared/dna.js';
import { FORM_LIST, chooseBranch, branchScores, hintFor, nextFormId, emptyCare } from '../../shared/forms.js';
import { simulate, newCreature, moodOf } from '../../shared/needs.js';
import { pendingIncome, ticketsNow, MINIGAMES, DIFFICULTIES, baseReward } from '../../shared/economy.js';
import { ITEMS } from '../../shared/catalog.js';

const seeds = [1, 2, 7, 101, 202, 303, 12345, 987654321, 2654435769, 4294967295];
const rng = seeds.map((seed) => {
  const r = createRng(seed);
  return { seed, values: Array.from({ length: 8 }, () => r.next()) };
});

const hashes = ['', 'a', 'Nidaria', 'criatura-ñ-€', 'pelusa|hoja|gota'].map((text) => ({ text, hash: hashString(text) }));
const mixes = [[0, 0], [1, 2], [4294967295, 4294967295], [123456789, 42], [2654435769, 77]].map(([a, b]) => ({ a, b, mix: mix(a, b), roll: roll(a, b) }));

const genes = [];
for (const seed of [...seeds, 5, 99, 1000, 31337, 777777, 3000000000]) {
  for (const family of [null, 'pelusa', 'hoja', 'gota']) genes.push({ seed, family, genes: createGenes(seed, family ?? undefined) });
}

const forms = FORM_LIST.map((form) => ({
  ...form, secret: form.secret ?? null, origin: form.origin ?? null, hint: form.stage === 'huevo' ? '' : hintFor(form.id),
}));

const T0 = 1700000000000;
const sims = [];
const spans = [30e3, 50e3, 40 * 60e3, 2 * 3600e3, 9 * 3600e3, 30 * 3600e3, 3 * 86400e3, 12 * 86400e3, 25 * 86400e3];
for (const seed of [1, 101, 202, 303, 12345, 987654321]) {
  for (const scale of [1, 60]) {
    let creature = newCreature({ id: 'c', name: 'Prueba', genes: createGenes(seed), now: T0 });
    for (const span of spans) {
      const input = structuredClone(creature);
      const now = T0 + span / scale;
      const result = simulate(creature, now, { scale });
      sims.push({ scale, now, input, output: structuredClone(result.creature), events: result.events, mood: moodOf(result.creature) });
      creature = result.creature;
      // Un cuidado a medias para variar los caminos.
      if (creature.alive && span === 2 * 3600e3) {
        creature.hunger = 90; creature.fun = 85; creature.hygiene = 80; creature.energy = 60;
        creature.care.feeds = 5; creature.care.sweets = seed % 3; creature.care.plays = seed % 4;
      }
      if (creature.alive && span === 9 * 3600e3) creature.asleep = true;
    }
  }
}

// Adultas con el final de la vida cerca y distinto vínculo: el vínculo
// alarga la vida y los descuidos lo bajan (y con él, adelantan el final).
for (const seed of [7, 101, 4242]) {
  for (const scale of [1, 60]) {
    for (const bond of [0, 60, 100]) {
      const base = newCreature({ id: 'b', name: 'Vínculo', genes: createGenes(seed), now: T0 });
      Object.assign(base, {
        formId: `adulto-${base.genes.family}-tranquilo-tranquilo`, stage: 'adulto', stageAt: T0,
        history: ['huevo', `cria-${base.genes.family}`, `joven-${base.genes.family}-tranquilo`, `adulto-${base.genes.family}-tranquilo-tranquilo`],
        lifeEndsAt: T0 + (30 * 3600e3) / scale, bond,
        hunger: 40, energy: 30, hygiene: 50, fun: 25,
      });
      let creature = base;
      for (const span of [6 * 3600e3, 20 * 3600e3, 40 * 3600e3, 3 * 86400e3, 4 * 86400e3, 5 * 86400e3, 6 * 86400e3]) {
        const input = structuredClone(creature);
        const now = T0 + span / scale;
        const result = simulate(creature, now, { scale });
        sims.push({ scale, now, input, output: structuredClone(result.creature), events: result.events, mood: moodOf(result.creature) });
        creature = result.creature;
        // A partir de las 20 horas la cuidan entre tramo y tramo.
        if (creature.alive && span >= 20 * 3600e3) {
          Object.assign(creature, { hunger: 100, fun: 100, hygiene: 100, energy: 100, health: 100, sick: false, asleep: false });
        }
      }
    }
  }
}

// Jóvenes a punto de hacerse adultas: qué forma les toca, ocultas incluidas.
const youngCare = (extra) => ({
  ...emptyCare(), hours: 24, sumHunger: 60 * 24, sumEnergy: 60 * 24, sumHygiene: 60 * 24, sumFun: 60 * 24,
  feeds: 5, mistakes: 2, ...extra,
});
const perfect = { mistakes: 0, sumHunger: 80 * 24, sumEnergy: 80 * 24, sumHygiene: 80 * 24, sumFun: 80 * 24 };
const cared = { sumHunger: 70 * 24, sumEnergy: 70 * 24, sumHygiene: 70 * 24, sumFun: 70 * 24 };
const poor = { sumHunger: 40 * 24, sumEnergy: 40 * 24, sumHygiene: 40 * 24, sumFun: 40 * 24 };
const secretCases = [
  [{}, {}],
  [perfect, {}],
  [perfect, { lifeMistakes: 1 }],
  [{ ...perfect, sumFun: 30 * 24 }, {}],
  [{ feeds: 9, favs: 9 }, {}],
  [{ feeds: 9, favs: 8 }, {}],
  [{ feeds: 4, favs: 4 }, {}],
  [{ feeds: 8, favs: 8, mistakes: 0, ...perfect }, {}],
  [{ pets: 20, baths: 4, mistakes: 0 }, { bornNight: true }],
  [{ pets: 20, baths: 4, mistakes: 0 }, {}],
  [{ plays: 9, mistakes: 0 }, { bornNight: true }],
  [cared, { origin: 'other' }],
  [poor, { origin: 'other' }],
  [cared, { origin: 'same' }],
  [{ pets: 20, baths: 4, ...cared }, { bornNight: true, origin: 'other' }],
];
const secrets = [];
for (const seed of [5, 101, 31337]) {
  for (const b1 of ['gloton', 'arisco']) {
    for (const [care, opts] of secretCases) {
      const genes = createGenes(seed);
      if (opts.origin === 'other') genes.origin = genes.family === 'gota' ? 'hoja' : 'gota';
      if (opts.origin === 'same') genes.origin = genes.family;
      const creature = {
        formId: `joven-${genes.family}-${b1}`,
        genes,
        bornNight: Boolean(opts.bornNight),
        care: youngCare(care),
        lifeCare: { hours: 1, sum: 240, mistakes: opts.lifeMistakes ?? 0 },
      };
      secrets.push({ creature, next: nextFormId(creature) });
    }
  }
}

const cares = [
  { hours: 1, sumHunger: 80, sumEnergy: 70, sumHygiene: 75, sumFun: 72, feeds: 3, sweets: 2, plays: 1, pets: 4, baths: 1, heals: 0, mistakes: 0 },
  { hours: 24, sumHunger: 600, sumEnergy: 800, sumHygiene: 500, sumFun: 300, feeds: 9, sweets: 0, plays: 0.5, pets: 30, baths: 3, heals: 1, mistakes: 6 },
  { hours: 0.2, sumHunger: 5, sumEnergy: 6, sumHygiene: 7, sumFun: 4, feeds: 0, sweets: 0, plays: 0, pets: 0, baths: 0, heals: 0, mistakes: 3 },
  { hours: 10, sumHunger: 790, sumEnergy: 630, sumHygiene: 690, sumFun: 710, feeds: 4, sweets: 1, plays: 6, pets: 2, baths: 0, heals: 0, mistakes: 0 },
];
const branches = [];
for (const care of cares) {
  for (const seed of [1, 101, 12345]) {
    const g = createGenes(seed);
    branches.push({ care, genes: g, scores: branchScores(care, g), branch: chooseBranch(care, g) });
  }
}

const economy = {
  income: [[T0, T0, 1], [T0, T0 + 3600e3, 1], [T0, T0 + 5 * 3600e3 + 59e3, 1], [T0, T0 + 99 * 3600e3, 1], [T0, T0 + 61e3, 60], [T0 + 5, T0, 1]]
    .map(([at, now, scale]) => ({ at, now, scale, value: pendingIncome(at, now, scale) })),
  tickets: [[5, T0, T0 + 1, 1], [4, T0, T0 + 14 * 60e3, 1], [4, T0, T0 + 15 * 60e3, 1], [0, T0, T0 + 47 * 60e3, 1], [1, T0, T0 + 3 * 3600e3, 1], [2, T0, T0 + 16e3, 60], [3, T0 - 7, T0 + 31 * 60e3, 1]]
    .map(([tickets, at, now, scale]) => ({ tickets, at, now, scale, value: ticketsNow(tickets, at, now, scale) })),
  rewards: MINIGAMES.flatMap((game) => Object.keys(DIFFICULTIES).map((difficulty) => ({
    game: game.id, difficulty, reward: baseReward(game, difficulty), target: game.levels[difficulty].target,
  }))),
};

const items = ITEMS.map((item) => ({ id: item.id, name: item.name, price: item.price, kind: item.kind }));

const here = path.dirname(fileURLToPath(import.meta.url));
const file = path.join(here, '..', 'app', 'src', 'test', 'resources', 'golden.json');
fs.writeFileSync(file, JSON.stringify({ rng, hashes, mixes, genes, forms, sims, branches, secrets, economy, items }));
console.log('golden.json', fs.statSync(file).size, 'bytes;', sims.length, 'simulaciones');
