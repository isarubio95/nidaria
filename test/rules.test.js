import test from 'node:test';
import assert from 'node:assert/strict';
import { createGenes, inheritGenes, dnaCode, FAMILIES } from '../shared/dna.js';
import {
  FORM_LIST, getForm, chooseBranch, nextFormId, emptyCare, childrenOf, secretsOf, hintFor,
} from '../shared/forms.js';
import { gainBond, bondLevel, lifeEnd, BOND_DAILY_CAP, BOND_LIFE_MS } from '../shared/bond.js';
import { simulate, newCreature, moodOf } from '../shared/needs.js';
import * as actions from '../shared/actions.js';
import {
  pendingIncome, ticketsNow, gameReward, baseReward, getGame, featuredGame, levelFor, perksFor,
  INCOME_CAP, TICKETS_MAX, TICKET_REGEN_MS, MINIGAMES, DIFFICULTIES, FEATURED_MULT, PERKS,
} from '../shared/economy.js';
import { TRAITS } from '../shared/dna.js';
import { dayAfter } from '../shared/daily.js';
import {
  ITEMS, getItem, itemsOfKind, SPOTS, CATEGORIES, cleanDecor, placeDecor,
} from '../shared/catalog.js';

const HOUR = 60 * 60 * 1000;
const T0 = 1_800_000_000_000;

function hatched(seed = 7, family = 'pelusa') {
  const egg = newCreature({ id: 'c1', name: 'Prueba', genes: createGenes(seed, family), now: T0 });
  return simulate(egg, T0 + 60 * 1000).creature;
}

test('el ADN es determinista y su código es estable', () => {
  const a = createGenes(1234);
  const b = createGenes(1234);
  assert.deepEqual(a, b);
  assert.equal(dnaCode(a), dnaCode(b));
  assert.match(dnaCode(a), /^[PHG]-[2-9A-Z]{3}-[2-9A-Z]{3}$/);
  assert.notEqual(dnaCode(a), dnaCode(createGenes(1235)));
});

test('el huevo heredero conserva un rasgo y casi siempre la familia', () => {
  const parent = createGenes(99, 'gota');
  let sameFamily = 0;
  for (let seed = 1; seed <= 200; seed++) {
    const child = inheritGenes(parent, seed);
    assert.ok(child.traits.some((trait) => parent.traits.includes(trait)));
    assert.notEqual(child.traits[0], child.traits[1]);
    if (child.family === 'gota') sameFamily++;
  }
  assert.ok(sameFamily > 140 && sameFamily < 200, `misma familia: ${sameFamily}`);
});

test('el árbol tiene 64 formas y 15 ocultas, con nombres únicos', () => {
  assert.equal(FORM_LIST.length, 1 + 3 + 12 + 48 + 15);
  assert.equal(new Set(FORM_LIST.map((form) => form.name)).size, FORM_LIST.length);
  for (const form of FORM_LIST) {
    if (form.parent) assert.ok(getForm(form.parent));
  }
  for (const family of FAMILIES) {
    assert.equal(childrenOf(`cria-${family}`).length, 4);
    const secrets = secretsOf(family);
    assert.equal(secrets.length, 5);
    for (const form of secrets) {
      assert.equal(form.stage, 'adulto');
      assert.ok(hintFor(form.id).length > 10);
    }
  }
});

test('la crianza decide la rama', () => {
  const genes = { traits: ['fuerte', 'timido'] };
  const base = { ...emptyCare(), hours: 24, sumHunger: 60 * 24, sumEnergy: 60 * 24, sumHygiene: 60 * 24, sumFun: 60 * 24 };
  assert.equal(chooseBranch({ ...base, sweets: 8, feeds: 10 }, genes), 'gloton');
  assert.equal(chooseBranch({ ...base, plays: 9, feeds: 5 }, genes), 'jugueton');
  assert.equal(chooseBranch({ ...base, pets: 20, baths: 4, feeds: 5 }, genes), 'tranquilo');
  assert.equal(chooseBranch({ ...base, mistakes: 6, feeds: 3 }, genes), 'arisco');
  const at = (formId, care, g = { ...genes, family: 'gota' }) => ({
    formId, care, genes: g, lifeCare: { hours: 1, sum: 160, mistakes: 1 },
  });
  assert.equal(nextFormId(at('huevo', base, { family: 'hoja' })), 'cria-hoja');
  assert.equal(nextFormId(at('joven-gota-arisco', { ...base, plays: 9 })), 'adulto-gota-arisco-jugueton');
});

// Una joven de 24 horas a punto de hacerse adulta.
function youngAt(care, { genes: geneChanges, ...extra } = {}) {
  const genes = { ...createGenes(5, 'hoja'), traits: ['fuerte', 'timido'], ...geneChanges };
  return {
    formId: 'joven-hoja-gloton',
    genes,
    bornNight: false,
    lifeCare: { hours: 1, sum: 240, mistakes: 0 },
    ...extra,
    care: {
      ...emptyCare(), hours: 24, sumHunger: 60 * 24, sumEnergy: 60 * 24, sumHygiene: 60 * 24, sumFun: 60 * 24,
      feeds: 5, mistakes: 2, ...care,
    },
  };
}

test('una crianza rara lleva a una forma oculta', () => {
  assert.equal(nextFormId(youngAt({})), 'adulto-hoja-gloton-arisco');
  // Radiante: ni un descuido en toda la vida y bien atendida.
  const perfect = { mistakes: 0, sumHunger: 80 * 24, sumEnergy: 80 * 24, sumHygiene: 80 * 24, sumFun: 80 * 24 };
  assert.equal(nextFormId(youngAt(perfect)), 'oculta-hoja-radiante');
  assert.notEqual(nextFormId(youngAt(perfect, { lifeCare: { hours: 1, sum: 240, mistakes: 1 } })), 'oculta-hoja-radiante');
  // Sibarita: de joven, al menos ocho comidas y todas su favorita.
  assert.equal(nextFormId(youngAt({ feeds: 9, favs: 9 })), 'oculta-hoja-sibarita');
  assert.notEqual(nextFormId(youngAt({ feeds: 9, favs: 8 })), 'oculta-hoja-sibarita');
  assert.notEqual(nextFormId(youngAt({ feeds: 4, favs: 4 })), 'oculta-hoja-sibarita');
  // Lunar: nacida de madrugada y criada tranquila.
  const calm = { pets: 20, baths: 4, mistakes: 0 };
  assert.equal(nextFormId(youngAt(calm, { bornNight: true })), 'oculta-hoja-lunar');
  assert.equal(nextFormId(youngAt(calm)), 'adulto-hoja-gloton-tranquilo');
  assert.equal(nextFormId(youngAt({ plays: 9, mistakes: 0 }, { bornNight: true })), 'adulto-hoja-gloton-jugueton');
  // Mestiza: heredera que cambió de familia, bien cuidada.
  const cared = { sumHunger: 70 * 24, sumEnergy: 70 * 24, sumHygiene: 70 * 24, sumFun: 70 * 24 };
  assert.equal(nextFormId(youngAt(cared, { genes: { origin: 'gota' } })), 'oculta-hoja-gota');
  const poor = { sumHunger: 40 * 24, sumEnergy: 40 * 24, sumHygiene: 40 * 24, sumFun: 40 * 24 };
  assert.equal(nextFormId(youngAt(poor, { genes: { origin: 'gota' } })), 'adulto-hoja-gloton-arisco');
  for (const id of ['oculta-hoja-radiante', 'oculta-hoja-sibarita', 'oculta-hoja-lunar', 'oculta-hoja-gota']) {
    assert.equal(getForm(id).family, 'hoja');
  }
});

test('el huevo heredero recuerda su familia de origen si cambia', () => {
  const parent = createGenes(99, 'gota');
  let changed = 0;
  for (let seed = 1; seed <= 200; seed++) {
    const child = inheritGenes(parent, seed);
    if (child.family === 'gota') assert.equal(child.origin, undefined);
    else {
      assert.equal(child.origin, 'gota');
      changed++;
    }
  }
  assert.ok(changed > 0);
});

test('el vínculo sube con los cuidados, con tope por día, y baja con descuidos', () => {
  const creature = hatched(7, 'pelusa');
  creature.genes = { ...creature.genes, traits: ['fuerte', 'curioso'] };
  const now = creature.tickAt;
  for (let i = 0; i < 60; i++) actions.pet(creature, now + i * 31000);
  assert.equal(creature.bond, BOND_DAILY_CAP);
  assert.equal(bondLevel(creature.bond), 1);
  // Al día siguiente de su vida vuelve a poder subir.
  const tomorrow = creature.bornAt + 25 * HOUR;
  gainBond(creature, 5, tomorrow);
  assert.equal(creature.bond, BOND_DAILY_CAP + 5);
  // A una tímida le cuesta el doble.
  const shy = hatched(7, 'pelusa');
  shy.genes = { ...shy.genes, traits: ['timido', 'fuerte'] };
  gainBond(shy, 4, shy.tickAt);
  assert.equal(shy.bond, 2);
  // Dejarla sola un día entero le hace perder la confianza.
  creature.hunger = 20;
  creature.fun = 20;
  const later = simulate(creature, tomorrow + 12 * HOUR).creature;
  assert.ok(later.bond < creature.bond, `${later.bond} frente a ${creature.bond}`);
});

test('el vínculo alarga la vida adulta', () => {
  const creature = hatched(11, 'gota');
  Object.assign(creature, {
    formId: 'adulto-gota-tranquilo-tranquilo', stage: 'adulto', stageAt: creature.tickAt,
    lifeEndsAt: creature.tickAt + 2 * HOUR, hunger: 100, energy: 100, hygiene: 100, fun: 100,
  });
  assert.equal(lifeEnd({ ...creature, bond: 100 }), creature.lifeEndsAt + BOND_LIFE_MS);
  const lonely = simulate({ ...creature, bond: 0 }, creature.tickAt + 3 * HOUR).creature;
  assert.equal(lonely.alive, false);
  assert.equal(lonely.deathCause, 'vejez');
  const loved = simulate({ ...creature, bond: 100 }, creature.tickAt + 3 * HOUR).creature;
  assert.equal(loved.alive, true);
});

test('el huevo eclosiona y la cría evoluciona a la hora', () => {
  const egg = newCreature({ id: 'c1', name: 'Prueba', genes: createGenes(7, 'hoja'), now: T0 });
  const early = simulate(egg, T0 + 30 * 1000);
  assert.equal(early.creature.stage, 'huevo');
  const born = simulate(egg, T0 + 50 * 1000);
  assert.equal(born.creature.formId, 'cria-hoja');
  assert.equal(born.events[0].type, 'evolved');
  const young = simulate(born.creature, T0 + 50 * 1000 + HOUR + 1000);
  assert.equal(young.creature.stage, 'joven');
  assert.deepEqual(young.creature.history.slice(0, 2), ['huevo', 'cria-hoja']);
});

test('simular en un salto o en muchos da casi lo mismo', () => {
  const start = hatched();
  const once = simulate(start, start.tickAt + 5 * HOUR).creature;
  let many = start;
  for (let i = 1; i <= 50; i++) many = simulate(many, start.tickAt + (5 * HOUR * i) / 50).creature;
  for (const need of ['hunger', 'energy', 'hygiene', 'fun']) {
    assert.ok(Math.abs(once[need] - many[need]) < 0.5, `${need}: ${once[need]} frente a ${many[need]}`);
  }
});

test('el abandono total mata entre uno y tres días después', () => {
  const start = hatched();
  let creature = start;
  let diedAfter = null;
  for (let h = 1; h <= 96; h++) {
    creature = simulate(creature, start.tickAt + h * HOUR).creature;
    if (!creature.alive) {
      diedAfter = h;
      break;
    }
  }
  assert.ok(diedAfter !== null, 'debería morir');
  assert.ok(diedAfter >= 20 && diedAfter <= 72, `murió a las ${diedAfter} h`);
  assert.equal(moodOf(creature), 'muerta');
  const later = simulate(creature, start.tickAt + 200 * HOUR);
  assert.equal(later.creature.diedAt, creature.diedAt);
});

test('una criatura bien cuidada llega a adulta y no muere', () => {
  let creature = hatched(11, 'gota');
  const start = creature.tickAt;
  for (let h = 1; h <= 30; h++) {
    creature = simulate(creature, start + h * HOUR).creature;
    assert.ok(creature.alive, `viva a las ${h} h`);
    if (creature.asleep) continue;
    if (creature.hunger < 70) actions.feed(creature, 'arroz');
    if (creature.fun < 60) creature.fun = 90;
    if (creature.hygiene < 60) actions.bathe(creature, null, start + h * HOUR);
    if (creature.energy < 30) actions.setAsleep(creature, true);
  }
  assert.equal(creature.stage, 'adulto');
  assert.ok(creature.lifeEndsAt > creature.stageAt);
  assert.ok(creature.health > 60);
});

test('acelerar el tiempo equivale a esperar más', () => {
  const egg = newCreature({ id: 'c1', name: 'Prueba', genes: createGenes(7, 'pelusa'), now: T0 });
  const slow = simulate(egg, T0 + 4 * HOUR).creature;
  const fast = simulate(egg, T0 + (4 * HOUR) / 60, { scale: 60 }).creature;
  assert.equal(slow.stage, 'joven');
  assert.equal(fast.stage, slow.stage);
  assert.equal(fast.formId, slow.formId);
  for (const need of ['hunger', 'energy', 'hygiene', 'fun', 'health']) {
    assert.ok(Math.abs(slow[need] - fast[need]) < 0.5, `${need}: ${slow[need]} frente a ${fast[need]}`);
  }
  assert.equal(pendingIncome(T0, T0 + HOUR / 60, 60), 6);
});

test('dormir recupera energía y despierta sola', () => {
  const creature = hatched();
  creature.energy = 20;
  assert.ok(actions.setAsleep(creature, true).ok);
  const rested = simulate(creature, creature.tickAt + 9 * HOUR).creature;
  assert.equal(rested.asleep, false);
  assert.ok(rested.energy > 80);
});

test('las acciones respetan sus reglas', () => {
  const creature = hatched();
  creature.hunger = 30;
  const fav = actions.feed(creature, creature.genes.favFood);
  assert.equal(fav.reaction, 'favorita');
  creature.hunger = 99;
  assert.equal(actions.feed(creature, 'pan').ok, false);
  assert.equal(actions.heal(creature, 'medicina').ok, false);
  creature.sick = true;
  assert.ok(actions.heal(creature, 'medicina').ok);
  assert.equal(creature.sick, false);
  creature.asleep = true;
  assert.equal(actions.pet(creature, T0).ok, false);
  creature.asleep = false;
  creature.fun = 59;
  for (let i = 0; i < 30; i++) actions.pet(creature, T0 + i);
  assert.ok(creature.fun <= 60);
  assert.ok(actions.equip(creature, 'cabeza', 'gorro').ok);
  assert.equal(actions.equip(creature, 'cara', 'gorro').ok, false);
});

test('el dinero pasivo tiene tope y la energía de juego se recarga', () => {
  assert.equal(pendingIncome(T0, T0 + HOUR), 6);
  assert.equal(pendingIncome(T0, T0 + 100 * HOUR), INCOME_CAP);
  assert.equal(ticketsNow(TICKETS_MAX, T0, T0 + HOUR).nextAt, null);
  const partial = ticketsNow(1, T0, T0 + TICKET_REGEN_MS * 2.5);
  assert.equal(partial.tickets, 3);
  assert.equal(partial.nextAt, T0 + TICKET_REGEN_MS * 3);
  assert.equal(ticketsNow(0, T0, T0 + 10 * HOUR).tickets, TICKETS_MAX);
});

test('a más dificultad, más recompensa; perder no paga', () => {
  for (const game of MINIGAMES) {
    const rewards = Object.keys(DIFFICULTIES).map((d) => gameReward(game, d, game.levels[d].target).coins);
    assert.ok(rewards[0] < rewards[1] && rewards[1] < rewards[2], `${game.id}: ${rewards}`);
    assert.equal(gameReward(game, 'facil', game.levels.facil.target - 1).coins, 0);
    assert.ok(game.levels.dificil.target <= game.maxScore);
  }
  const fruit = getGame('fruta');
  assert.equal(gameReward(fruit, 'dificil', 1000).coins, Math.round(baseReward(fruit, 'dificil') * 1.5));
  assert.ok(gameReward(fruit, 'facil', 12).coins > pendingIncome(T0, T0 + HOUR));
});

test('el catálogo es coherente', () => {
  assert.equal(new Set(ITEMS.map((item) => item.id)).size, ITEMS.length);
  for (const item of ITEMS) assert.ok(CATEGORIES.some((category) => category.id === item.kind), item.id);
  for (const family of FAMILIES) {
    const genes = createGenes(5, family);
    assert.ok(getItem(genes.favFood));
    assert.ok(getItem(genes.hateFood));
  }
});

test('la decoración: cada sitio tiene donde elegir y es lo más caro', () => {
  const decor = itemsOfKind('casa');
  for (const spot of SPOTS) assert.ok(decor.filter((item) => item.spot === spot.id).length >= 4, spot.id);
  for (const item of decor) {
    assert.ok(SPOTS.some((spot) => spot.id === item.spot), item.id);
    assert.ok(item.reusable, item.id);
  }
  const clothes = itemsOfKind('ropa').reduce((sum, item) => sum + item.price, 0);
  assert.ok(decor.reduce((sum, item) => sum + item.price, 0) > clothes * 3);

  const placed = cleanDecor(null);
  assert.deepEqual(placed, { pared: null, alfombra: null, rincon: null, ventana: null });
  assert.deepEqual(placeDecor(placed, 'rincon', 'baul'), { ok: true });
  assert.equal(placed.rincon, 'baul');
  assert.equal(placeDecor(placed, 'pared', 'baul').ok, false);
  assert.equal(placeDecor(placed, 'techo', null).ok, false);
  assert.equal(placeDecor(placed, 'pared', 'corona').ok, false);
  assert.deepEqual(placeDecor(placed, 'rincon', null), { ok: true });
  assert.equal(placed.rincon, null);
  // Lo guardado que ya no vale se descarta.
  assert.deepEqual(cleanDecor({ pared: 'papel-olas', rincon: 'papel-olas', ventana: 'nada', otro: 'x' }),
    { pared: 'papel-olas', alfombra: null, rincon: null, ventana: null });
});

test('el juego del día rota y paga más', () => {
  const pool = MINIGAMES.filter((game) => !game.branch).map((game) => game.id);
  let day = '2026-11-13';
  const seen = [];
  for (let i = 0; i < pool.length * 3; i++) {
    seen.push(featuredGame(day));
    day = dayAfter(day);
  }
  assert.deepEqual(new Set(seen), new Set(pool));
  for (let i = 1; i < seen.length; i++) assert.notEqual(seen[i], seen[i - 1]);
  assert.ok(!seen.includes('escondite'), 'el juego de una rama no sale: no todas pueden jugarlo');
  const eco = getGame('eco');
  assert.equal(baseReward(eco, 'normal', FEATURED_MULT), Math.round(eco.base * 2 * 1.5));
  const plain = gameReward(eco, 'dificil', 9).coins;
  assert.equal(gameReward(eco, 'dificil', 9, FEATURED_MULT).coins, baseReward(eco, 'dificil', FEATURED_MULT));
  assert.ok(gameReward(eco, 'dificil', 9, FEATURED_MULT).coins > plain);
  assert.equal(gameReward(eco, 'facil', 1, FEATURED_MULT).coins, 0);
});

test('cada rasgo ayuda en un juego sin cambiar el objetivo', () => {
  for (const trait of Object.keys(TRAITS)) {
    const perk = PERKS[trait];
    assert.ok(perk && getGame(perk.game), trait);
  }
  const genes = { ...createGenes(7), traits: ['gloton', 'timido'] };
  const fruit = getGame('fruta');
  assert.deepEqual(perksFor('fruta', genes).map((perk) => perk.trait), ['gloton', 'timido']);
  assert.deepEqual(perksFor('eco', genes), []);
  const level = levelFor(fruit, 'normal', genes);
  assert.equal(level.basket, 1.35);
  assert.equal(level.rotten, 0.12);
  assert.equal(level.target, fruit.levels.normal.target);
  assert.equal(fruit.levels.normal.rotten, 0.2, 'no toca el nivel original');
  assert.deepEqual(levelFor(fruit, 'normal', null), fruit.levels.normal);
  const sleepy = levelFor(getGame('eco'), 'facil', { ...genes, traits: ['dormilon', 'fuerte'] });
  assert.equal(sleepy.beat, 744);
});

test('el escondite solo lo juegan las juguetonas', () => {
  const game = getGame('escondite');
  assert.equal(game.branch, 'jugueton');
  const creature = hatched();
  assert.match(actions.gameLock(game, creature), /juguetonas/);
  assert.equal(actions.gameLock(getGame('eco'), creature), null);
  creature.formId = 'joven-pelusa-jugueton';
  assert.equal(actions.gameLock(game, creature), null);
  creature.formId = 'adulto-pelusa-jugueton-gloton';
  assert.equal(actions.gameLock(game, creature), null, 'de joven fue juguetona');
  creature.formId = 'adulto-pelusa-gloton-tranquilo';
  assert.ok(actions.gameLock(game, creature));
  creature.formId = 'oculta-pelusa-radiante';
  assert.ok(actions.gameLock(game, creature));
  for (const difficulty of Object.keys(DIFFICULTIES)) assert.ok(game.levels[difficulty].target <= game.maxScore);
});
