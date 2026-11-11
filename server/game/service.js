// Lógica de partida en el servidor. Cada operación carga al jugador y a
// su criatura, la simula hasta ahora, aplica la acción y guarda el
// resultado, todo dentro de una transacción.

import crypto from 'node:crypto';
import { HttpError } from '../auth.js';
import { createGenes, inheritGenes, dnaCode, FAMILIES } from '../../shared/dna.js';
import { getForm, STAGE_INFO, chooseBranch } from '../../shared/forms.js';
import { simulate, newCreature, nextStageAt, moodOf } from '../../shared/needs.js';
import * as actions from '../../shared/actions.js';
import { getItem, MAX_STACK } from '../../shared/catalog.js';
import {
  pendingIncome, ticketsNow, getGame, gameReward, DIFFICULTIES,
  TICKETS_MAX, INCOME_CAP,
} from '../../shared/economy.js';

const randomSeed = () => crypto.randomBytes(4).readUInt32BE(0);
const NAME_RULE = /^[\p{L}\p{N} .'-]{1,14}$/u;

function cleanName(name) {
  if (typeof name !== 'string') throw new HttpError(400, 'Ponle un nombre.');
  const clean = name.trim().replace(/\s+/g, ' ');
  if (!NAME_RULE.test(clean)) throw new HttpError(400, 'El nombre puede tener hasta 14 letras o números.');
  return clean;
}

export function createGame(db, config) {
  const scale = config.timeScale;

  async function loadUser(userId) {
    const user = await db.get('SELECT * FROM users WHERE id = ?', [userId]);
    if (!user) throw new HttpError(401, 'La sesión ha caducado. Vuelve a entrar.');
    return user;
  }

  async function loadCreature(userId) {
    const row = await db.get('SELECT data FROM creatures WHERE user_id = ? AND active = 1', [userId]);
    return row ? JSON.parse(row.data) : null;
  }

  async function saveCreature(userId, creature, now) {
    await db.run(
      'UPDATE creatures SET data = ?, alive = ?, form_id = ?, updated_at = ? WHERE id = ? AND user_id = ?',
      [JSON.stringify(creature), creature.alive ? 1 : 0, creature.formId, now, creature.id, userId],
    );
  }

  async function recordForm(userId, formId, at) {
    await db.run(
      `INSERT INTO criadex (user_id, form_id, first_at, times) VALUES (?, ?, ?, 1)
       ON CONFLICT (user_id, form_id) DO UPDATE SET times = criadex.times + 1`,
      [userId, formId, at],
    );
  }

  async function addItem(userId, itemId, qty) {
    await db.run(
      `INSERT INTO inventory (user_id, item_id, qty) VALUES (?, ?, ?)
       ON CONFLICT (user_id, item_id) DO UPDATE SET qty = inventory.qty + excluded.qty`,
      [userId, itemId, qty],
    );
  }

  async function ownedQty(userId, itemId) {
    const row = await db.get('SELECT qty FROM inventory WHERE user_id = ? AND item_id = ?', [userId, itemId]);
    return row ? row.qty : 0;
  }

  async function consume(userId, itemId) {
    const qty = await ownedQty(userId, itemId);
    if (qty < 1) throw new HttpError(400, 'No te queda. Cómpralo en la tienda.');
    await db.run('UPDATE inventory SET qty = qty - 1 WHERE user_id = ? AND item_id = ?', [userId, itemId]);
  }

  // Tres huevos para elegir. Tras una muerte, el primero es el heredero.
  async function ensureEggOffer(user, deadCreature) {
    if (user.egg_offer) return JSON.parse(user.egg_offer);
    const offer = [];
    if (deadCreature) {
      const genes = inheritGenes(deadCreature.genes, randomSeed());
      offer.push({ genes, heir: true });
      for (const family of FAMILIES.filter((f) => f !== genes.family)) {
        offer.push({ genes: createGenes(randomSeed(), family), heir: false });
      }
    } else {
      for (const family of FAMILIES) offer.push({ genes: createGenes(randomSeed(), family), heir: false });
    }
    await db.run('UPDATE users SET egg_offer = ? WHERE id = ?', [JSON.stringify(offer), user.id]);
    return offer;
  }

  // Simula la criatura hasta ahora y deja constancia de lo ocurrido.
  async function advance(userId, creature, now) {
    if (!creature) return { creature: null, events: [] };
    const result = simulate(creature, now, { scale });
    for (const event of result.events) {
      if (event.type === 'evolved') await recordForm(userId, event.to, event.at);
    }
    return result;
  }

  async function snapshot(user, creature, now) {
    const tickets = ticketsNow(user.tickets, user.tickets_at, now, scale);
    const inventoryRows = await db.all('SELECT item_id, qty FROM inventory WHERE user_id = ? AND qty > 0', [user.id]);
    const criadexRows = await db.all('SELECT form_id, first_at, times FROM criadex WHERE user_id = ?', [user.id]);
    let eggOffer = null;
    if (!creature || !creature.alive) {
      const offer = await ensureEggOffer(user, creature);
      eggOffer = offer.map((egg, index) => ({
        index,
        heir: egg.heir,
        family: egg.genes.family,
        hue: egg.genes.hue,
        sat: egg.genes.sat,
        light: egg.genes.light,
        hue2: egg.genes.hue2,
      }));
    }
    let view = null;
    if (creature) {
      const form = getForm(creature.formId);
      view = {
        ...creature,
        code: dnaCode(creature.genes),
        formName: form.name,
        stageName: STAGE_INFO[creature.stage].name,
        nextStageAt: creature.alive ? nextStageAt(creature, scale) : null,
        mood: moodOf(creature),
        tendency: creature.alive && (creature.stage === 'cria' || creature.stage === 'joven')
          ? chooseBranch(creature.care, creature.genes) : null,
      };
    }
    return {
      now,
      timeScale: scale,
      devTools: config.devTools,
      user: {
        name: user.name,
        coins: user.coins,
        tickets: tickets.tickets,
        ticketsMax: TICKETS_MAX,
        ticketsNextAt: tickets.nextAt,
        incomePending: pendingIncome(user.income_at, now, scale),
        incomeCap: INCOME_CAP,
        incomeAt: user.income_at,
      },
      creature: view,
      eggOffer,
      inventory: Object.fromEntries(inventoryRows.map((row) => [row.item_id, row.qty])),
      criadex: Object.fromEntries(criadexRows.map((row) => [row.form_id, { firstAt: row.first_at, times: row.times }])),
    };
  }

  // Envoltorio común: `fn` recibe el contexto y puede modificar la
  // criatura y el usuario; lo que devuelva viaja como `result`.
  function operation(userId, fn) {
    return db.tx(async () => {
      const now = Date.now();
      const user = await loadUser(userId);
      const { creature, events } = await advance(userId, await loadCreature(userId), now);
      const ctx = { now, user, creature, events };
      const result = fn ? await fn(ctx) : null;
      if (ctx.creature) await saveCreature(userId, ctx.creature, now);
      const fresh = await loadUser(userId);
      return { state: await snapshot(fresh, ctx.creature, now), events: ctx.events, result };
    });
  }

  function needCreature(ctx) {
    if (!ctx.creature) throw new HttpError(400, 'Primero elige un huevo.');
    return ctx.creature;
  }

  function unwrap(outcome) {
    if (!outcome.ok) throw new HttpError(400, outcome.error);
    return outcome;
  }

  return {
    state: (userId) => operation(userId),

    pickEgg: (userId, index, name) => operation(userId, async (ctx) => {
      if (ctx.creature && ctx.creature.alive) throw new HttpError(400, 'Ya tienes una criatura.');
      const offer = await ensureEggOffer(ctx.user, ctx.creature);
      const egg = offer[index];
      if (!egg) throw new HttpError(400, 'Ese huevo no está.');
      const previous = ctx.creature;
      const creature = newCreature({
        id: crypto.randomUUID(),
        name: cleanName(name),
        genes: egg.genes,
        now: ctx.now,
        generation: previous && egg.heir ? previous.generation + 1 : 1,
        parentId: previous && egg.heir ? previous.id : null,
      });
      if (previous) await saveCreature(userId, previous, ctx.now);
      await db.run('UPDATE creatures SET active = 0 WHERE user_id = ?', [userId]);
      await db.run(
        `INSERT INTO creatures (id, user_id, active, alive, form_id, data, created_at, updated_at)
         VALUES (?, ?, 1, 1, ?, ?, ?, ?)`,
        [creature.id, userId, creature.formId, JSON.stringify(creature), ctx.now, ctx.now],
      );
      await db.run('UPDATE users SET egg_offer = NULL WHERE id = ?', [userId]);
      await recordForm(userId, 'huevo', ctx.now);
      ctx.creature = creature;
      return { picked: index };
    }),

    // Dar calor al huevo adelanta la eclosión segundo y medio por toque.
    warmEgg: (userId, taps) => operation(userId, async (ctx) => {
      const creature = needCreature(ctx);
      if (creature.stage !== 'huevo') return { hatched: true };
      const count = Math.max(1, Math.min(20, Number(taps) || 1));
      const shift = (count * 1500) / scale;
      creature.stageAt -= shift;
      // El huevo no tiene necesidades, así que repetir ese tramo no cambia
      // nada salvo permitir que la eclosión ocurra ya.
      creature.tickAt -= shift;
      const again = await advance(userId, creature, ctx.now);
      ctx.creature = again.creature;
      ctx.events.push(...again.events);
      return { hatched: ctx.creature.stage !== 'huevo' };
    }),

    feed: (userId, itemId) => operation(userId, async (ctx) => {
      const creature = needCreature(ctx);
      if (await ownedQty(userId, itemId) < 1) throw new HttpError(400, 'No te queda. Cómpralo en la tienda.');
      const outcome = unwrap(actions.feed(creature, itemId));
      await consume(userId, itemId);
      return { reaction: outcome.reaction, itemId };
    }),

    sleep: (userId, asleep) => operation(userId, async (ctx) => {
      const outcome = unwrap(actions.setAsleep(needCreature(ctx), Boolean(asleep)));
      return { reaction: outcome.reaction };
    }),

    bathe: (userId, itemId) => operation(userId, async (ctx) => {
      const creature = needCreature(ctx);
      if (itemId && await ownedQty(userId, itemId) < 1) throw new HttpError(400, 'No te queda jabón.');
      const outcome = unwrap(actions.bathe(creature, itemId ?? null, ctx.now, scale));
      if (itemId) await consume(userId, itemId);
      return { reaction: outcome.reaction };
    }),

    heal: (userId, itemId) => operation(userId, async (ctx) => {
      const creature = needCreature(ctx);
      if (await ownedQty(userId, itemId) < 1) throw new HttpError(400, 'No te queda. Cómpralo en la tienda.');
      const outcome = unwrap(actions.heal(creature, itemId));
      await consume(userId, itemId);
      return { reaction: outcome.reaction, itemId };
    }),

    pet: (userId) => operation(userId, async (ctx) => {
      const outcome = unwrap(actions.pet(needCreature(ctx), ctx.now, scale));
      return { reaction: outcome.reaction };
    }),

    toy: (userId, itemId) => operation(userId, async (ctx) => {
      const creature = needCreature(ctx);
      if (await ownedQty(userId, itemId) < 1) throw new HttpError(400, 'Ese juguete no es tuyo todavía.');
      const outcome = unwrap(actions.playToy(creature, itemId, ctx.now, scale));
      return { reaction: outcome.reaction, itemId };
    }),

    rename: (userId, name) => operation(userId, async (ctx) => {
      needCreature(ctx).name = cleanName(name);
      return null;
    }),

    equip: (userId, slot, itemId) => operation(userId, async (ctx) => {
      const creature = needCreature(ctx);
      if (itemId && await ownedQty(userId, itemId) < 1) throw new HttpError(400, 'Eso no es tuyo todavía.');
      unwrap(actions.equip(creature, slot, itemId ?? null));
      return null;
    }),

    buy: (userId, itemId, qty) => operation(userId, async (ctx) => {
      const item = getItem(itemId);
      if (!item) throw new HttpError(400, 'Ese artículo no existe.');
      const count = item.reusable ? 1 : Math.max(1, Math.min(MAX_STACK, Math.floor(Number(qty) || 1)));
      const owned = await ownedQty(userId, itemId);
      if (item.reusable && owned > 0) throw new HttpError(400, 'Ya lo tienes.');
      if (owned + count > MAX_STACK) throw new HttpError(400, 'No te caben más.');
      const cost = item.price * count;
      if (ctx.user.coins < cost) throw new HttpError(400, `Te faltan ${cost - ctx.user.coins} monedas.`);
      await db.run('UPDATE users SET coins = coins - ? WHERE id = ?', [cost, userId]);
      await addItem(userId, itemId, count);
      return { itemId, qty: count, cost };
    }),

    collectIncome: (userId) => operation(userId, async (ctx) => {
      const amount = pendingIncome(ctx.user.income_at, ctx.now, scale);
      if (amount < 1) throw new HttpError(400, 'Todavía no hay monedas que recoger.');
      await db.run('UPDATE users SET coins = coins + ?, income_at = ? WHERE id = ?', [amount, ctx.now, userId]);
      return { coins: amount };
    }),

    startGame: (userId, gameId, difficulty) => operation(userId, async (ctx) => {
      const game = getGame(gameId);
      if (!game || !DIFFICULTIES[difficulty]) throw new HttpError(400, 'Ese juego no existe.');
      const blocked = actions.canPlayGame(needCreature(ctx));
      if (blocked) throw new HttpError(400, blocked);
      const tickets = ticketsNow(ctx.user.tickets, ctx.user.tickets_at, ctx.now, scale);
      if (tickets.tickets < 1) throw new HttpError(400, 'No te queda energía de juego. Se recarga sola con el tiempo.');
      // Al gastar desde el máximo empieza a contar la recarga desde ahora.
      const from = tickets.tickets >= TICKETS_MAX ? ctx.now : tickets.ticketsAt;
      await db.run('UPDATE users SET tickets = ?, tickets_at = ? WHERE id = ?', [tickets.tickets - 1, from, userId]);
      const playId = crypto.randomUUID();
      await db.run(
        'INSERT INTO plays (id, user_id, game_id, difficulty, started_at) VALUES (?, ?, ?, ?, ?)',
        [playId, userId, gameId, difficulty, ctx.now],
      );
      return { playId, gameId, difficulty, seed: randomSeed() };
    }),

    finishGame: (userId, playId, score) => operation(userId, async (ctx) => {
      const play = await db.get('SELECT * FROM plays WHERE id = ? AND user_id = ?', [playId, userId]);
      if (!play || play.finished_at) throw new HttpError(400, 'Esa partida ya está cerrada.');
      const game = getGame(play.game_id);
      const points = Math.max(0, Math.floor(Number(score) || 0));
      const elapsed = ctx.now - play.started_at;
      // Una partida imposible (demasiado rápida o con demasiados puntos)
      // se cierra como perdida en lugar de pagarse.
      const credible = points <= game.maxScore && elapsed <= 10 * 60 * 1000;
      const outcome = credible ? gameReward(game, play.difficulty, points) : { won: false, coins: 0 };
      const paid = outcome.won && elapsed < game.minMs ? { won: false, coins: 0 } : outcome;
      await db.run(
        'UPDATE plays SET finished_at = ?, score = ?, won = ?, reward = ? WHERE id = ?',
        [ctx.now, points, paid.won ? 1 : 0, paid.coins, playId],
      );
      if (paid.coins > 0) await db.run('UPDATE users SET coins = coins + ? WHERE id = ?', [paid.coins, userId]);
      if (ctx.creature && ctx.creature.alive) actions.applyGameResult(ctx.creature, paid.won);
      return { won: paid.won, coins: paid.coins, score: points, target: game.levels[play.difficulty].target };
    }),

    // Solo con DEV_TOOLS=1: adelanta el reloj del jugador.
    devSkip: (userId, hours) => {
      if (!config.devTools) throw new HttpError(404, 'No existe.');
      const shift = Math.round((Math.max(0.01, Math.min(24 * 30, Number(hours) || 1)) * 60 * 60 * 1000) / scale);
      return db.tx(async () => {
        const creature = await loadCreature(userId);
        if (creature) {
          for (const key of ['bornAt', 'stageAt', 'tickAt', 'lastPetAt']) creature[key] -= shift;
          if (creature.lifeEndsAt) creature.lifeEndsAt -= shift;
          for (const key of Object.keys(creature.cooldowns)) creature.cooldowns[key] -= shift;
          await saveCreature(userId, creature, Date.now());
        }
        await db.run('UPDATE users SET income_at = income_at - ?, tickets_at = tickets_at - ? WHERE id = ?', [shift, shift, userId]);
      }).then(() => operation(userId));
    },
  };
}
