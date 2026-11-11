import test from 'node:test';
import assert from 'node:assert/strict';
import { createGenes, inheritGenes, dnaCode, FAMILIES } from '../shared/dna.js';
import { FORM_LIST, getForm, chooseBranch, nextFormId, emptyCare } from '../shared/forms.js';
import { simulate, newCreature, moodOf } from '../shared/needs.js';
import * as actions from '../shared/actions.js';
import {
  pendingIncome, ticketsNow, gameReward, baseReward, getGame,
  INCOME_CAP, TICKETS_MAX, TICKET_REGEN_MS, MINIGAMES, DIFFICULTIES,
} from '../shared/economy.js';
import { ITEMS, getItem } from '../shared/catalog.js';

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

test('el árbol tiene 64 formas con nombres únicos', () => {
  assert.equal(FORM_LIST.length, 1 + 3 + 12 + 48);
  assert.equal(new Set(FORM_LIST.map((form) => form.name)).size, FORM_LIST.length);
  for (const form of FORM_LIST) {
    if (form.parent) assert.ok(getForm(form.parent));
  }
});

test('la crianza decide la rama', () => {
  const genes = { traits: ['fuerte', 'timido'] };
  const base = { ...emptyCare(), hours: 24, sumHunger: 60 * 24, sumEnergy: 60 * 24, sumHygiene: 60 * 24, sumFun: 60 * 24 };
  assert.equal(chooseBranch({ ...base, sweets: 8, feeds: 10 }, genes), 'gloton');
  assert.equal(chooseBranch({ ...base, plays: 9, feeds: 5 }, genes), 'jugueton');
  assert.equal(chooseBranch({ ...base, pets: 20, baths: 4, feeds: 5 }, genes), 'tranquilo');
  assert.equal(chooseBranch({ ...base, mistakes: 6, feeds: 3 }, genes), 'arisco');
  assert.equal(nextFormId('huevo', base, { family: 'hoja' }), 'cria-hoja');
  assert.equal(nextFormId('joven-gota-arisco', { ...base, plays: 9 }, genes), 'adulto-gota-arisco-jugueton');
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
  for (const family of FAMILIES) {
    const genes = createGenes(5, family);
    assert.ok(getItem(genes.favFood));
    assert.ok(getItem(genes.hateFood));
  }
});
