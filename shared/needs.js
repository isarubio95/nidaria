// Simulación del paso del tiempo: cómo bajan las necesidades, cuándo
// enferma, evoluciona o muere una criatura. Función pura: el servidor la
// usa como autoridad y el navegador para mover las barras entre visitas.

import { clamp, roll } from './rng.js';
import {
  STAGE_INFO, ADULT_LIFE_MS, ADULT_LIFE_BONUS_MS,
  emptyCare, nextFormId, getForm,
} from './forms.js';

export const NEEDS = ['hunger', 'energy', 'hygiene', 'fun'];

export const NEED_INFO = {
  hunger: { name: 'Comida' },
  energy: { name: 'Energía' },
  hygiene: { name: 'Higiene' },
  fun: { name: 'Diversión' },
  health: { name: 'Salud' },
};

export const CRITICAL = 15;
export const LOW = 35;

const HOUR = 60 * 60 * 1000;
const STEP_MS = 5 * 60 * 1000;

// Puntos por hora. Negativo baja, positivo sube.
const RATES = {
  awake: { hunger: -6, energy: -4.2, hygiene: -3.2, fun: -5 },
  asleep: { hunger: -2.5, energy: 12.5, hygiene: -1.2, fun: -0.8 },
};

const STAGE_FACTOR = { huevo: 0, cria: 3, joven: 1.3, adulto: 1 };

const HATCH_NEEDS = { hunger: 45, energy: 72, hygiene: 64, fun: 40, health: 100 };

function traitFactors(genes) {
  const has = (trait) => genes.traits.includes(trait);
  return {
    hunger: has('gloton') ? 1.25 : 1,
    energy: has('dormilon') ? 1.2 : 1,
    hygiene: has('presumido') ? 1.25 : 1,
    fun: has('travieso') ? 1.25 : 1,
    healthLoss: has('fuerte') ? 0.7 : 1,
    sleepGain: has('dormilon') ? 1.1 : 1,
  };
}

export function stageDuration(stage, scale = 1) {
  const base = STAGE_INFO[stage].durationMs;
  return base === null ? null : base / scale;
}

export function nextStageAt(creature, scale = 1) {
  const duration = stageDuration(creature.stage, scale);
  return duration === null ? null : creature.stageAt + duration;
}

export function criticalNeeds(creature) {
  const list = [];
  for (const need of NEEDS) {
    if (need === 'energy' && creature.asleep) continue;
    if (creature[need] < CRITICAL) list.push(need);
  }
  return list;
}

// Estado de ánimo, de mejor a peor. Lo usa la interfaz para la cara,
// la postura y lo que dice la criatura.
export function moodOf(creature) {
  if (!creature.alive) return 'muerta';
  if (creature.stage === 'huevo') return 'huevo';
  if (creature.asleep) return 'dormida';
  if (creature.sick) return 'enferma';
  const critical = criticalNeeds(creature);
  if (critical.length >= 2 || creature.health < 30) return 'fatal';
  if (critical.length === 1) return 'mal';
  const mean = (creature.hunger + creature.energy + creature.hygiene + creature.fun) / 4;
  if (mean > 78 && creature.health > 70) return 'feliz';
  if (mean > 50) return 'bien';
  return 'regular';
}

function evolve(creature, at, scale, events) {
  const from = creature.formId;
  const to = nextFormId(creature.formId, creature.care, creature.genes);
  if (!to) return;
  const form = getForm(to);
  if (creature.stage === 'huevo') Object.assign(creature, HATCH_NEEDS);
  creature.lifeCare.hours += creature.care.hours;
  creature.lifeCare.sum += creature.care.sumHunger + creature.care.sumEnergy
    + creature.care.sumHygiene + creature.care.sumFun;
  creature.formId = to;
  creature.stage = form.stage;
  creature.stageAt = at;
  creature.care = emptyCare();
  creature.history = [...creature.history, to];
  if (form.stage === 'adulto') {
    const hours = Math.max(creature.lifeCare.hours, 0.0001);
    const quality = clamp(creature.lifeCare.sum / hours / 400, 0, 1);
    creature.lifeEndsAt = at + (ADULT_LIFE_MS + ADULT_LIFE_BONUS_MS * quality) / scale;
  }
  events.push({ type: 'evolved', from, to, at });
}

function die(creature, at, cause, events) {
  creature.alive = false;
  creature.diedAt = at;
  creature.deathCause = cause;
  creature.asleep = false;
  events.push({ type: 'died', cause, at });
}

function step(creature, from, to, scale, events) {
  if (creature.stage === 'huevo') return;
  const hours = ((to - from) * scale) / HOUR;
  const factors = traitFactors(creature.genes);
  const stageFactor = STAGE_FACTOR[creature.stage];
  const rates = creature.asleep ? RATES.asleep : RATES.awake;
  const before = criticalNeeds(creature).length;

  for (const need of NEEDS) {
    let rate = rates[need];
    if (rate < 0) rate *= stageFactor * factors[need];
    else rate *= factors.sleepGain;
    if (need === 'fun' && creature.sick) rate *= 1.3;
    creature[need] = clamp(creature[need] + rate * hours, 0, 100);
  }

  creature.care.hours += hours;
  creature.care.sumHunger += creature.hunger * hours;
  creature.care.sumEnergy += creature.energy * hours;
  creature.care.sumHygiene += creature.hygiene * hours;
  creature.care.sumFun += creature.fun * hours;

  const critical = criticalNeeds(creature);
  if (critical.length > before) creature.care.mistakes += critical.length - before;

  if (!creature.asleep && creature.energy <= 0) {
    creature.asleep = true;
    creature.care.mistakes += 1;
    events.push({ type: 'collapsed', at: to });
  } else if (creature.asleep && creature.energy >= 100) {
    creature.asleep = false;
    events.push({ type: 'woke', at: to });
  }

  // Una tirada de enfermedad por cada hora de juego cumplida.
  const hourBefore = Math.floor(((from - creature.bornAt) * scale) / HOUR);
  const hourAfter = Math.floor(((to - creature.bornAt) * scale) / HOUR);
  if (!creature.sick && hourAfter > hourBefore) {
    const chance = creature.hygiene < 20 ? 0.1 : 0.004;
    if (roll(creature.genes.seed, hourAfter) < chance) {
      creature.sick = true;
      events.push({ type: 'sick', at: to });
    }
  }

  let delta = 0;
  if (critical.length > 0 || creature.sick) {
    delta = -(2.2 * critical.length + (creature.sick ? 3 : 0)) * factors.healthLoss;
  } else {
    const mean = (creature.hunger + creature.energy + creature.hygiene + creature.fun) / 4;
    delta = mean > 55 ? 3 : 1;
  }
  creature.health = clamp(creature.health + delta * hours, 0, 100);

  if (creature.health <= 0) {
    const cause = creature.sick ? 'enfermedad' : creature.hunger < CRITICAL ? 'hambre' : 'abandono';
    die(creature, to, cause, events);
  }
}

// Avanza la criatura hasta `now`. No modifica el original.
export function simulate(original, now, { scale = 1 } = {}) {
  const creature = structuredClone(original);
  const events = [];
  let t = creature.tickAt;
  if (!creature.alive || now <= t) {
    creature.tickAt = Math.max(t, now);
    return { creature, events };
  }
  const stepMs = STEP_MS / scale;
  while (t < now && creature.alive) {
    let end = Math.min(now, t + stepMs);
    const evolveAt = nextStageAt(creature, scale);
    if (evolveAt !== null && evolveAt > t && evolveAt < end) end = evolveAt;
    if (creature.lifeEndsAt && creature.lifeEndsAt > t && creature.lifeEndsAt < end) end = creature.lifeEndsAt;
    step(creature, t, end, scale, events);
    t = end;
    if (!creature.alive) break;
    if (evolveAt !== null && t >= evolveAt) evolve(creature, t, scale, events);
    if (creature.lifeEndsAt && t >= creature.lifeEndsAt) die(creature, t, 'vejez', events);
  }
  creature.tickAt = now;
  return { creature, events };
}

export function newCreature({ id, name, genes, now, generation = 1, parentId = null }) {
  return {
    id, name, genes,
    formId: 'huevo', stage: 'huevo',
    bornAt: now, stageAt: now, lifeEndsAt: null,
    hunger: 100, energy: 100, hygiene: 100, fun: 100, health: 100,
    asleep: false, sick: false,
    alive: true, diedAt: null, deathCause: null,
    tickAt: now,
    care: emptyCare(),
    lifeCare: { hours: 0, sum: 0 },
    equipped: { cabeza: null, cara: null, cuello: null },
    cooldowns: {},
    history: ['huevo'],
    generation, parentId,
    pets: 0, lastPetAt: 0,
  };
}
