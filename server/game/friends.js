// Amigos, visitas y regalos en el servidor. Cada operación va dentro de
// `operation` (ver service.js): carga al jugador, aplica el cambio en la
// misma transacción y devuelve su estado con lo concreto en `result`.
//
// Las visitas no tocan la criatura del amigo: se simula una copia hasta
// ahora para enseñarla como está, y lo único que se guarda es cuándo vino.

import crypto from 'node:crypto';
import { HttpError } from '../auth.js';
import { getItem, MAX_STACK, cleanDecor } from '../../shared/catalog.js';
import { getForm, STAGE_INFO } from '../../shared/forms.js';
import { simulate, moodOf } from '../../shared/needs.js';
import { localDay, DEFAULT_TIME_ZONE } from '../../shared/daily.js';
import {
  MAX_FRIENDS, MAX_OUTGOING, MAX_UNOPENED, CODE_ALPHABET, CODE_LENGTH,
  canGift, normalizeFriendCode, formatFriendCode,
} from '../../shared/social.js';

function newCode() {
  let code = '';
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)];
  return code;
}

const count = (row) => Number(row?.n ?? 0);

export function createFriends(db, config, operation) {
  const scale = config.timeScale;

  // Código nuevo para el jugador. Si coincide con el de otro, prueba otra vez.
  async function assignCode(userId) {
    for (let attempt = 0; attempt < 5; attempt++) {
      const code = newCode();
      if (await db.get('SELECT id FROM users WHERE friend_code = ?', [code])) continue;
      await db.run('UPDATE users SET friend_code = ? WHERE id = ?', [code, userId]);
      return code;
    }
    throw new Error('No se ha podido crear un código de amigo libre.');
  }

  const friendRow = (userId, friendId) => db.get(
    'SELECT * FROM friends WHERE user_id = ? AND friend_id = ?',
    [userId, friendId],
  );
  const requestRow = (fromId, toId) => db.get(
    'SELECT * FROM friend_requests WHERE from_id = ? AND to_id = ?',
    [fromId, toId],
  );
  const friendCount = async (userId) => count(await db.get('SELECT COUNT(*) AS n FROM friends WHERE user_id = ?', [userId]));
  const today = (user, now) => localDay(now, user.time_zone ?? DEFAULT_TIME_ZONE);

  // El amigo con ese id, o un error si no lo es.
  async function needFriend(userId, friendId) {
    if (typeof friendId !== 'string' || !friendId) throw new HttpError(400, 'Falta decir a quién.');
    if (!(await friendRow(userId, friendId))) throw new HttpError(404, 'Esa persona ya no está entre tus amigos.');
    return db.get('SELECT id, name, decor FROM users WHERE id = ?', [friendId]);
  }

  // Une a los dos y borra las solicitudes que hubiera entre ellos.
  async function befriend(user, other, now) {
    if (await friendCount(user.id) >= MAX_FRIENDS) {
      throw new HttpError(400, `Ya tienes ${MAX_FRIENDS} amigos, que es el máximo. Quita a alguien para añadir a otra persona.`);
    }
    if (await friendCount(other.id) >= MAX_FRIENDS) {
      throw new HttpError(400, `${other.name} ya tiene el máximo de amigos.`);
    }
    for (const [a, b] of [[user.id, other.id], [other.id, user.id]]) {
      await db.run(
        'INSERT INTO friends (user_id, friend_id, created_at, visited_at) VALUES (?, ?, ?, NULL) ON CONFLICT (user_id, friend_id) DO NOTHING',
        [a, b, now],
      );
    }
    await db.run(
      'DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)',
      [user.id, other.id, other.id, user.id],
    );
  }

  // Lo que se ve de la criatura de un amigo en su lista: lo guardado, sin
  // simular, para que abrir la lista sea rápido. La visita la pone al día.
  function card(data) {
    if (!data) return null;
    const creature = JSON.parse(data);
    const form = getForm(creature.formId);
    return {
      name: creature.name,
      formId: creature.formId,
      formName: form.name,
      stage: creature.stage,
      stageName: STAGE_INFO[creature.stage].name,
      genes: creature.genes,
      alive: creature.alive,
    };
  }

  // La criatura de un amigo tal como está ahora mismo, para la visita.
  // Lleva lo que hace falta para dibujarla y saber qué le vendría bien.
  function visitView(data, now) {
    if (!data) return null;
    const { creature } = simulate(JSON.parse(data), now, { scale });
    if (!creature.alive) return null;
    const form = getForm(creature.formId);
    return {
      id: creature.id,
      name: creature.name,
      genes: creature.genes,
      formId: creature.formId,
      formName: form.name,
      stage: creature.stage,
      stageName: STAGE_INFO[creature.stage].name,
      equipped: creature.equipped,
      mood: moodOf(creature),
      alive: true,
      asleep: creature.asleep,
      sick: creature.sick,
      hunger: creature.hunger,
      energy: creature.energy,
      hygiene: creature.hygiene,
      fun: creature.fun,
      health: creature.health,
      bornAt: creature.bornAt,
      generation: creature.generation,
    };
  }

  async function giftedTo(userId, day) {
    const rows = await db.all('SELECT to_id FROM gifts WHERE from_id = ? AND day = ?', [userId, day]);
    return new Set(rows.map((row) => row.to_id));
  }

  return {
    // Lo que va en cada estado: el código y cuánto espera respuesta.
    async summary(user) {
      const code = user.friend_code ?? await assignCode(user.id);
      const requests = await db.get('SELECT COUNT(*) AS n FROM friend_requests WHERE to_id = ?', [user.id]);
      const gifts = await db.get('SELECT COUNT(*) AS n FROM gifts WHERE to_id = ? AND opened_at IS NULL', [user.id]);
      return { code: formatFriendCode(code), requests: count(requests), gifts: count(gifts) };
    },

    async hasFriend(userId) {
      return Boolean(await db.get('SELECT friend_id FROM friends WHERE user_id = ? LIMIT 1', [userId]));
    },

    // Todo lo de la hoja de amigos: la lista, las solicitudes y los regalos.
    list: (userId) => operation(userId, async (ctx) => {
      const me = ctx.user.id;
      const gifted = await giftedTo(me, today(ctx.user, ctx.now));
      const friends = await db.all(
        `SELECT u.id, u.name, f.created_at, f.visited_at, c.data
           FROM friends f
           JOIN users u ON u.id = f.friend_id
           LEFT JOIN creatures c ON c.user_id = u.id AND c.active = 1
          WHERE f.user_id = ?
          ORDER BY u.name_key`,
        [me],
      );
      const incoming = await db.all(
        `SELECT u.id, u.name, r.created_at FROM friend_requests r JOIN users u ON u.id = r.from_id
          WHERE r.to_id = ? ORDER BY r.created_at DESC LIMIT 50`,
        [me],
      );
      const outgoing = await db.all(
        `SELECT u.id, u.name, r.created_at FROM friend_requests r JOIN users u ON u.id = r.to_id
          WHERE r.from_id = ? ORDER BY r.created_at DESC`,
        [me],
      );
      const gifts = await db.all(
        `SELECT g.id, g.item_id, g.sent_at, u.name FROM gifts g JOIN users u ON u.id = g.from_id
          WHERE g.to_id = ? AND g.opened_at IS NULL ORDER BY g.sent_at`,
        [me],
      );
      return {
        max: MAX_FRIENDS,
        friends: friends.map((row) => ({
          id: row.id,
          name: row.name,
          since: row.created_at,
          // La última vez que vino a ver tu habitación.
          visitedAt: row.visited_at,
          giftedToday: gifted.has(row.id),
          creature: card(row.data),
        })),
        incoming: incoming.map((row) => ({ id: row.id, name: row.name, at: row.created_at })),
        outgoing: outgoing.map((row) => ({ id: row.id, name: row.name, at: row.created_at })),
        gifts: gifts.map((row) => ({ id: row.id, itemId: row.item_id, from: row.name, at: row.sent_at })),
      };
    }),

    // Añadir con el código de otro manda una solicitud. Si esa persona ya
    // te la había mandado a ti, quedáis como amigos sin más.
    add: (userId, input) => operation(userId, async (ctx) => {
      const code = normalizeFriendCode(input);
      if (!code) throw new HttpError(400, 'Un código de amigo tiene 8 letras y números, como K7QM-4XPA.');
      const me = ctx.user;
      const other = await db.get('SELECT id, name FROM users WHERE friend_code = ?', [code]);
      if (!other) throw new HttpError(404, 'No hay nadie con ese código. Revisa que esté bien escrito.');
      if (other.id === me.id) throw new HttpError(400, 'Ese es tu propio código. Pídele el suyo a quien quieras añadir.');
      if (await friendRow(me.id, other.id)) throw new HttpError(400, `${other.name} ya está entre tus amigos.`);
      if (await requestRow(other.id, me.id)) {
        await befriend(me, other, ctx.now);
        return { id: other.id, name: other.name, friends: true };
      }
      if (await requestRow(me.id, other.id)) {
        throw new HttpError(400, `Ya le has mandado una solicitud a ${other.name}. Falta que la acepte.`);
      }
      const waiting = count(await db.get('SELECT COUNT(*) AS n FROM friend_requests WHERE from_id = ?', [me.id]));
      if (waiting >= MAX_OUTGOING) {
        throw new HttpError(400, 'Tienes muchas solicitudes sin respuesta. Espera a que te contesten o cancela alguna.');
      }
      if (await friendCount(me.id) >= MAX_FRIENDS) {
        throw new HttpError(400, `Ya tienes ${MAX_FRIENDS} amigos, que es el máximo. Quita a alguien para añadir a otra persona.`);
      }
      await db.run('INSERT INTO friend_requests (from_id, to_id, created_at) VALUES (?, ?, ?)', [me.id, other.id, ctx.now]);
      return { id: other.id, name: other.name, friends: false };
    }),

    accept: (userId, fromId) => operation(userId, async (ctx) => {
      if (typeof fromId !== 'string' || !(await requestRow(fromId, ctx.user.id))) {
        throw new HttpError(400, 'Esa solicitud ya no está.');
      }
      const other = await db.get('SELECT id, name FROM users WHERE id = ?', [fromId]);
      await befriend(ctx.user, other, ctx.now);
      return { id: other.id, name: other.name, friends: true };
    }),

    // Quita lo que haya con esa persona: la amistad, la solicitud que te
    // mandó (rechazarla) o la que le mandaste tú (cancelarla).
    remove: (userId, otherId) => operation(userId, async (ctx) => {
      if (typeof otherId !== 'string' || !otherId) throw new HttpError(400, 'Falta decir a quién.');
      const me = ctx.user.id;
      const friends = await db.run(
        'DELETE FROM friends WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)',
        [me, otherId, otherId, me],
      );
      const requests = await db.run(
        'DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)',
        [me, otherId, otherId, me],
      );
      return { removed: friends.changes + requests.changes > 0 };
    }),

    // Ir a ver la habitación de un amigo.
    visit: (userId, friendId) => operation(userId, async (ctx) => {
      const friend = await needFriend(ctx.user.id, friendId);
      const row = await db.get('SELECT data FROM creatures WHERE user_id = ? AND active = 1', [friend.id]);
      await db.run('UPDATE friends SET visited_at = ? WHERE user_id = ? AND friend_id = ?', [ctx.now, friend.id, ctx.user.id]);
      ctx.track('visit');
      const gifted = await giftedTo(ctx.user.id, today(ctx.user, ctx.now));
      return {
        visit: {
          id: friend.id,
          name: friend.name,
          decor: cleanDecor(friend.decor ? JSON.parse(friend.decor) : null),
          creature: visitView(row?.data, ctx.now),
          giftedToday: gifted.has(friend.id),
        },
      };
    }),

    // Dejarle a un amigo algo tuyo. Uno al día por amigo.
    gift: (userId, friendId, itemId) => operation(userId, async (ctx) => {
      const me = ctx.user.id;
      const friend = await needFriend(me, friendId);
      if (!canGift(itemId)) throw new HttpError(400, 'Eso no se puede regalar. Se regala comida y cosas de cuidado.');
      const owned = await db.get('SELECT qty FROM inventory WHERE user_id = ? AND item_id = ?', [me, itemId]);
      if (!owned || owned.qty < 1) throw new HttpError(400, 'No te queda. Cómpralo en la tienda.');
      const day = today(ctx.user, ctx.now);
      if (await db.get('SELECT id FROM gifts WHERE from_id = ? AND to_id = ? AND day = ?', [me, friend.id, day])) {
        throw new HttpError(400, `Hoy ya le has dejado un regalo a ${friend.name}. Mañana podrás dejarle otro.`);
      }
      const waiting = count(await db.get('SELECT COUNT(*) AS n FROM gifts WHERE to_id = ? AND opened_at IS NULL', [friend.id]));
      if (waiting >= MAX_UNOPENED) {
        throw new HttpError(400, `${friend.name} tiene muchos regalos sin abrir. Espera a que los abra.`);
      }
      await db.run('UPDATE inventory SET qty = qty - 1 WHERE user_id = ? AND item_id = ?', [me, itemId]);
      await db.run(
        'INSERT INTO gifts (id, from_id, to_id, item_id, day, sent_at, opened_at) VALUES (?, ?, ?, ?, ?, ?, NULL)',
        [crypto.randomUUID(), me, friend.id, itemId, day, ctx.now],
      );
      return { itemId, to: friend.name };
    }),

    // Abrir un regalo lo pasa a tus cosas.
    open: (userId, giftId) => operation(userId, async (ctx) => {
      const me = ctx.user.id;
      const gift = typeof giftId === 'string' ? await db.get(
        'SELECT g.id, g.item_id, g.opened_at, u.name FROM gifts g JOIN users u ON u.id = g.from_id WHERE g.id = ? AND g.to_id = ?',
        [giftId, me],
      ) : null;
      if (!gift || gift.opened_at) throw new HttpError(400, 'Ese regalo ya está abierto.');
      const owned = await db.get('SELECT qty FROM inventory WHERE user_id = ? AND item_id = ?', [me, gift.item_id]);
      if ((owned?.qty ?? 0) + 1 > MAX_STACK) {
        throw new HttpError(400, `No te caben más de ${getItem(gift.item_id)?.name ?? 'eso'}. Gasta alguno y vuelve a abrirlo.`);
      }
      await db.run(
        `INSERT INTO inventory (user_id, item_id, qty) VALUES (?, ?, 1)
         ON CONFLICT (user_id, item_id) DO UPDATE SET qty = inventory.qty + 1`,
        [me, gift.item_id],
      );
      await db.run('UPDATE gifts SET opened_at = ? WHERE id = ?', [ctx.now, gift.id]);
      return { itemId: gift.item_id, from: gift.name };
    }),

    // Código nuevo, por si el anterior ha llegado a quien no debía. Los
    // amigos y las solicitudes se quedan como estaban.
    renewCode: (userId) => operation(userId, async (ctx) => {
      const code = await assignCode(ctx.user.id);
      return { code: formatFriendCode(code) };
    }),
  };
}
