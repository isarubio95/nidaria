// Acciones de cuidado. Cada una recibe la criatura ya simulada hasta
// `now`, la modifica y devuelve { ok, error?, reaction? }. Los errores
// van escritos para enseñarse tal cual al jugador.

import { clamp } from './rng.js';
import { getItem, TOY_COOLDOWN_MS, BATH_COOLDOWN_MS } from './catalog.js';
import { gainBond, BOND_GAINS } from './bond.js';
import { getForm, BRANCH_INFO } from './forms.js';

const fail = (error) => ({ ok: false, error });
const add = (creature, need, amount) => {
  creature[need] = clamp(creature[need] + amount, 0, 100);
};

function checkAwake(creature) {
  if (!creature.alive) return 'Ya no está aquí.';
  if (creature.stage === 'huevo') return 'Todavía no ha salido del huevo.';
  if (creature.asleep) return 'Está dormida. Despiértala primero.';
  return null;
}

export function feed(creature, itemId, now = Date.now(), scale = 1) {
  const blocked = checkAwake(creature);
  if (blocked) return fail(blocked);
  const item = getItem(itemId);
  if (!item || item.kind !== 'comida') return fail('Eso no se come.');
  if (creature.hunger >= 96) return fail('No tiene hambre ahora.');
  let fun = item.fun;
  let reaction = 'come';
  // `known` recuerda qué gustos ha descubierto ya el jugador.
  creature.known = creature.known ?? {};
  if (item.id === creature.genes.favFood) {
    fun += 10;
    reaction = 'favorita';
    creature.known.fav = true;
    creature.care.favs = (creature.care.favs ?? 0) + 1;
  } else if (item.id === creature.genes.hateFood) {
    fun -= 8;
    reaction = 'asco';
    creature.known.hate = true;
  }
  add(creature, 'hunger', item.hunger);
  add(creature, 'fun', fun);
  add(creature, 'health', item.health);
  creature.care.feeds += 1;
  if (item.sweet) creature.care.sweets += 1;
  // Lo que no le gusta no suma confianza.
  if (reaction !== 'asco') gainBond(creature, reaction === 'favorita' ? BOND_GAINS.favorite : BOND_GAINS.feed, now, scale);
  return { ok: true, reaction, item };
}

export function setAsleep(creature, asleep) {
  if (!creature.alive) return fail('Ya no está aquí.');
  if (creature.stage === 'huevo') return fail('Todavía no ha salido del huevo.');
  if (asleep === creature.asleep) return { ok: true, reaction: asleep ? 'duerme' : 'despierta' };
  if (asleep) {
    if (creature.energy >= 90) return fail('No tiene sueño todavía.');
    creature.asleep = true;
    return { ok: true, reaction: 'duerme' };
  }
  creature.asleep = false;
  if (creature.energy < 50) {
    add(creature, 'fun', -8);
    return { ok: true, reaction: 'grunona' };
  }
  return { ok: true, reaction: 'despierta' };
}

export function bathe(creature, itemId, now, scale = 1) {
  const blocked = checkAwake(creature);
  if (blocked) return fail(blocked);
  const last = creature.cooldowns.bano ?? 0;
  if (now - last < BATH_COOLDOWN_MS / scale) return fail('Acaba de bañarse.');
  if (creature.hygiene >= 96) return fail('Ya está limpia.');
  const wasDirty = creature.hygiene < 70;
  if (itemId) {
    const item = getItem(itemId);
    if (!item || !item.bath) return fail('Eso no sirve para bañarla.');
    creature.hygiene = 100;
    add(creature, 'fun', item.fun);
  } else {
    add(creature, 'hygiene', 55);
  }
  if (wasDirty) {
    creature.care.baths += 1;
    gainBond(creature, BOND_GAINS.bathe, now, scale);
  }
  creature.cooldowns.bano = now;
  return { ok: true, reaction: 'bano' };
}

export function heal(creature, itemId, now = Date.now(), scale = 1) {
  if (!creature.alive) return fail('Ya no está aquí.');
  if (creature.stage === 'huevo') return fail('Todavía no ha salido del huevo.');
  const item = getItem(itemId);
  if (!item || item.kind !== 'cuidado' || item.bath) return fail('Eso no la cura.');
  if (item.cures) {
    if (!creature.sick) return fail('No está enferma.');
    creature.sick = false;
  } else if (creature.health >= 100) {
    return fail('Ya tiene la salud al máximo.');
  }
  add(creature, 'health', item.health);
  creature.care.heals += 1;
  gainBond(creature, BOND_GAINS.heal, now, scale);
  return { ok: true, reaction: 'cura' };
}

export function pet(creature, now, scale = 1) {
  const blocked = checkAwake(creature);
  if (blocked) return fail(blocked);
  let gain = 1.5;
  if (creature.genes.traits.includes('mimoso')) gain *= 2;
  if (creature.genes.traits.includes('timido') && creature.pets < 20) gain *= 0.6;
  // Las caricias animan, pero no sustituyen a jugar: solo suben hasta 60.
  if (creature.fun < 60) creature.fun = Math.min(60, creature.fun + gain);
  if (now - creature.lastPetAt >= 30000 / scale) {
    creature.care.pets += 1;
    creature.lastPetAt = now;
    gainBond(creature, BOND_GAINS.pet * (creature.genes.traits.includes('mimoso') ? 1.5 : 1), now, scale);
  }
  creature.pets += 1;
  return { ok: true, reaction: 'caricia' };
}

export function playToy(creature, itemId, now, scale = 1) {
  const blocked = checkAwake(creature);
  if (blocked) return fail(blocked);
  const item = getItem(itemId);
  if (!item || item.kind !== 'juguete') return fail('Eso no es un juguete.');
  if (creature.sick) return fail('Está enferma y no tiene ganas de jugar.');
  if (creature.energy < item.energy + 4) return fail('Está demasiado cansada para jugar.');
  const last = creature.cooldowns[item.id] ?? 0;
  if (now - last < TOY_COOLDOWN_MS / scale) return fail('Se ha cansado de ese juguete. Prueba más tarde.');
  const bonus = creature.genes.traits.includes('curioso') ? 1.25 : 1;
  add(creature, 'fun', item.fun * bonus);
  add(creature, 'energy', -item.energy);
  creature.care.plays += 0.5;
  creature.cooldowns[item.id] = now;
  gainBond(creature, BOND_GAINS.toy, now, scale);
  return { ok: true, reaction: 'juega', item };
}

// Requisitos para empezar un minijuego. Devuelve el motivo o null.
export function canPlayGame(creature) {
  const blocked = checkAwake(creature);
  if (blocked) return blocked;
  if (creature.sick) return 'Está enferma y no tiene ganas de jugar.';
  if (creature.energy < 12) return 'Está demasiado cansada para jugar.';
  return null;
}

// Juegos de una rama: solo para criaturas que se han criado por ella, de
// jóvenes o de adultas. Devuelve el motivo o null.
export function gameLock(game, creature) {
  if (!game.branch) return null;
  if (creature && getForm(creature.formId).path.includes(game.branch)) return null;
  return `Solo lo juegan las criaturas ${BRANCH_INFO[game.branch].name.toLowerCase()}s.`;
}

export function applyGameResult(creature, won, now = Date.now(), scale = 1) {
  const bonus = creature.genes.traits.includes('curioso') ? 1.25 : 1;
  add(creature, 'energy', -7);
  add(creature, 'hunger', -4);
  add(creature, 'fun', (won ? 16 : 8) * bonus);
  creature.care.plays += 1;
  gainBond(creature, won ? BOND_GAINS.win : BOND_GAINS.game, now, scale);
}

export function equip(creature, slot, itemId) {
  if (!(slot in creature.equipped)) return fail('Ese sitio no existe.');
  if (itemId === null) {
    creature.equipped[slot] = null;
    return { ok: true };
  }
  const item = getItem(itemId);
  if (!item || item.kind !== 'ropa' || item.slot !== slot) return fail('Eso no va ahí.');
  creature.equipped[slot] = item.id;
  return { ok: true };
}
