import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import pg from 'pg';
import { createSqliteDb } from '../server/db/index.js';
import { createPostgresDb, poolConfig } from '../server/db/postgres.js';
import { migrate } from '../server/db/migrate.js';
import { createApp } from '../server/app.js';
import { createPush } from '../server/push.js';

// Con TEST_DATABASE_URL las pruebas corren contra PostgreSQL, cada una en
// un esquema propio que se borra al acabar. Sin ella, SQLite en memoria.
const PG_URL = process.env.TEST_DATABASE_URL;

async function openDb() {
  if (!PG_URL) {
    const db = createSqliteDb(':memory:');
    return { db, drop: async () => db.close() };
  }
  const schema = `test_${crypto.randomBytes(6).toString('hex')}`;
  const admin = new pg.Client(poolConfig(PG_URL));
  await admin.connect();
  await admin.query(`CREATE SCHEMA ${schema}`);
  const url = new URL(PG_URL);
  url.searchParams.set('options', `-c search_path=${schema}`);
  const db = createPostgresDb(url.toString());
  return {
    db,
    drop: async () => {
      await db.close();
      await admin.query(`DROP SCHEMA ${schema} CASCADE`);
      await admin.end();
    },
  };
}

// `sender` sustituye al envío real de avisos.
async function startServer(overrides = {}, { sender } = {}) {
  const { db, drop } = await openDb();
  await migrate(db);
  const config = { timeScale: 1, devTools: true, secureCookies: false, sessionDays: 30, ...overrides };
  const push = createPush(db, config, { sender: sender ?? (async () => {}) });
  const server = createApp(db, config, { push }).listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  let cookie = '';
  const call = async (method, url, body) => {
    const res = await fetch(base + url, {
      method,
      headers: { 'content-type': 'application/json', cookie },
      body: body ? JSON.stringify(body) : undefined,
    });
    const set = res.headers.get('set-cookie');
    if (set) cookie = set.split(';')[0];
    return { status: res.status, data: await res.json() };
  };
  return {
    call,
    db,
    push,
    dropCookie: () => { cookie = ''; },
    close: () => new Promise((resolve) => server.close(resolve)).then(drop),
  };
}

test('registro, sesión y errores de cuenta', async () => {
  const app = await startServer();
  try {
    assert.equal((await app.call('GET', '/api/state')).status, 401);
    assert.equal((await app.call('POST', '/api/auth/register', { name: 'ab', password: 'secreta123' })).status, 400);
    assert.equal((await app.call('POST', '/api/auth/register', { name: 'Lucía', password: 'corta' })).status, 400);
    const created = await app.call('POST', '/api/auth/register', { name: 'Lucía', password: 'secreta123' });
    assert.equal(created.status, 200);
    assert.equal(created.data.state.user.coins, 60);
    assert.equal(created.data.state.eggOffer.length, 3);
    assert.equal(created.data.state.creature, null);
    assert.equal((await app.call('POST', '/api/auth/register', { name: 'lucía', password: 'secreta123' })).status, 409);
    await app.call('POST', '/api/auth/logout');
    assert.equal((await app.call('GET', '/api/state')).status, 401);
    assert.equal((await app.call('POST', '/api/auth/login', { name: 'Lucía', password: 'otra-cosa' })).status, 401);
    assert.equal((await app.call('POST', '/api/auth/login', { name: 'LUCÍA', password: 'secreta123' })).status, 200);
    assert.equal((await app.call('GET', '/api/state')).status, 200);
  } finally {
    await app.close();
  }
});

test('partida completa: huevo, cuidados, tienda y minijuego', async () => {
  const app = await startServer();
  try {
    await app.call('POST', '/api/auth/register', { name: 'Jugador', password: 'secreta123' });
    assert.equal((await app.call('POST', '/api/creature/feed', { itemId: 'pan' })).status, 400);

    const picked = await app.call('POST', '/api/egg/pick', { index: 1, name: 'Mota' });
    assert.equal(picked.status, 200);
    assert.equal(picked.data.state.creature.stage, 'huevo');
    assert.equal(picked.data.state.eggOffer, null);

    let hatched = null;
    for (let i = 0; i < 4 && !hatched; i++) {
      const warm = await app.call('POST', '/api/egg/warm', { taps: 20 });
      if (warm.data.result.hatched) hatched = warm.data;
    }
    assert.ok(hatched, 'debería eclosionar al darle calor');
    assert.equal(hatched.state.creature.stage, 'cria');
    assert.ok(hatched.events.some((event) => event.type === 'evolved'));
    assert.ok(hatched.state.criadex[hatched.state.creature.formId]);

    const fed = await app.call('POST', '/api/creature/feed', { itemId: 'pan' });
    assert.equal(fed.status, 200);
    assert.equal(fed.data.state.inventory.pan, 1);
    assert.ok(fed.data.state.creature.hunger > 45);
    assert.equal((await app.call('POST', '/api/creature/feed', { itemId: 'festin' })).status, 400);

    const bought = await app.call('POST', '/api/shop/buy', { itemId: 'manzana', qty: 2 });
    assert.equal(bought.data.state.user.coins, 52);
    assert.equal(bought.data.state.inventory.manzana, 5);
    const poor = await app.call('POST', '/api/shop/buy', { itemId: 'corona' });
    assert.equal(poor.status, 400);
    assert.match(poor.data.error, /Te faltan \d+ monedas/);

    assert.equal((await app.call('POST', '/api/income/collect')).status, 400);
    const skipped = await app.call('POST', '/api/dev/skip', { hours: 2 });
    assert.equal(skipped.data.state.user.incomePending, 12);
    assert.equal(skipped.data.state.creature.stage, 'joven');
    const collected = await app.call('POST', '/api/income/collect');
    assert.equal(collected.data.state.user.coins, 64);

    const hat = await app.call('POST', '/api/shop/buy', { itemId: 'panuelo' });
    assert.equal(hat.status, 200);
    assert.equal((await app.call('POST', '/api/shop/buy', { itemId: 'panuelo' })).status, 400);
    const worn = await app.call('POST', '/api/creature/equip', { slot: 'cuello', itemId: 'panuelo' });
    assert.equal(worn.data.state.creature.equipped.cuello, 'panuelo');
    assert.equal((await app.call('POST', '/api/creature/equip', { slot: 'cabeza', itemId: 'corona' })).status, 400);

    await app.call('POST', '/api/creature/feed', { itemId: 'manzana' });
    const before = (await app.call('GET', '/api/state')).data.state;
    const started = await app.call('POST', '/api/games/start', { gameId: 'eco', difficulty: 'dificil' });
    assert.equal(started.status, 200, JSON.stringify(started.data));
    assert.equal(started.data.state.user.tickets, before.user.tickets - 1);
    // Terminar al instante con la puntuación máxima no es creíble: no paga.
    const cheated = await app.call('POST', '/api/games/finish', { playId: started.data.result.playId, score: 9 });
    assert.equal(cheated.data.result.won, false);
    assert.equal(cheated.data.state.user.coins, before.user.coins);
    assert.equal((await app.call('POST', '/api/games/finish', { playId: started.data.result.playId, score: 9 })).status, 400);
  } finally {
    await app.close();
  }
});

test('la energía de juego se agota y se recarga', async () => {
  const app = await startServer();
  try {
    await app.call('POST', '/api/auth/register', { name: 'Cansado', password: 'secreta123' });
    await app.call('POST', '/api/egg/pick', { index: 0, name: 'Trasto' });
    for (let i = 0; i < 4; i++) await app.call('POST', '/api/egg/warm', { taps: 20 });
    let last = null;
    for (let i = 0; i < 5; i++) {
      last = await app.call('POST', '/api/games/start', { gameId: 'parejas', difficulty: 'facil' });
      assert.equal(last.status, 200, JSON.stringify(last.data));
      await app.call('POST', '/api/games/finish', { playId: last.data.result.playId, score: 0 });
    }
    assert.equal(last.data.state.user.tickets, 0);
    const empty = await app.call('POST', '/api/games/start', { gameId: 'parejas', difficulty: 'facil' });
    assert.equal(empty.status, 400);
    assert.match(empty.data.error, /energía/);
  } finally {
    await app.close();
  }
});

test('la muerte deja un huevo heredero', async () => {
  const app = await startServer();
  try {
    await app.call('POST', '/api/auth/register', { name: 'Olvido', password: 'secreta123' });
    await app.call('POST', '/api/egg/pick', { index: 2, name: 'Nube' });
    for (let i = 0; i < 4; i++) await app.call('POST', '/api/egg/warm', { taps: 20 });
    const gone = await app.call('POST', '/api/dev/skip', { hours: 24 * 6 });
    assert.equal(gone.data.state.creature.alive, false);
    assert.ok(gone.data.events.some((event) => event.type === 'died'));
    assert.equal(gone.data.state.eggOffer.length, 3);
    assert.equal(gone.data.state.eggOffer[0].heir, true);
    assert.equal((await app.call('POST', '/api/creature/feed', { itemId: 'pan' })).status, 400);
    const next = await app.call('POST', '/api/egg/pick', { index: 0, name: 'Nube II' });
    assert.equal(next.data.state.creature.generation, 2);
    assert.equal(next.data.state.creature.alive, true);
    assert.ok(Object.keys(next.data.state.criadex).length >= 3);
  } finally {
    await app.close();
  }
});

test('recuerdos: las criaturas que se fueron, con su heredera', async () => {
  const app = await startServer();
  try {
    await app.call('POST', '/api/auth/register', { name: 'Memoria', password: 'secreta123' });
    const empty = await app.call('GET', '/api/memorial');
    assert.equal(empty.status, 200);
    assert.deepEqual(empty.data.result.creatures, []);
    await app.call('POST', '/api/egg/pick', { index: 0, name: 'Bruma' });
    for (let i = 0; i < 4; i++) await app.call('POST', '/api/egg/warm', { taps: 20 });
    await app.call('POST', '/api/dev/skip', { hours: 24 * 6 });
    // Recién ida, ya sale aunque no se haya elegido otro huevo.
    const gone = await app.call('GET', '/api/memorial');
    assert.equal(gone.data.result.creatures.length, 1);
    const first = gone.data.result.creatures[0];
    assert.equal(first.name, 'Bruma');
    assert.ok(first.lived > 12 * 60 * 60 * 1000, `vivió ${first.lived} ms`);
    assert.ok(first.cause);
    assert.ok(first.formName);
    assert.equal(first.heir, null);
    await app.call('POST', '/api/egg/pick', { index: 0, name: 'Bruma II' });
    const later = await app.call('GET', '/api/memorial');
    assert.equal(later.data.result.creatures.length, 1);
    assert.equal(later.data.result.creatures[0].heir, 'Bruma II');
    assert.equal(later.data.state.creature.name, 'Bruma II');
  } finally {
    await app.close();
  }
});

test('el vínculo sube al cuidarla y avisa al cambiar de nivel', async () => {
  // Con el tiempo acelerado, las caricias cuentan casi seguidas.
  const app = await startServer({ timeScale: 3600 });
  try {
    await app.call('POST', '/api/auth/register', { name: 'Caricias', password: 'secreta123' });
    await app.call('POST', '/api/egg/pick', { index: 0, name: 'Mimo' });
    await new Promise((resolve) => setTimeout(resolve, 60));
    const born = await app.call('GET', '/api/state');
    assert.equal(born.data.state.creature.stage, 'cria');
    assert.equal(born.data.state.creature.bond, 0);
    let leveled = null;
    for (let i = 0; i < 40 && !leveled; i++) {
      await new Promise((resolve) => setTimeout(resolve, 12));
      const petted = await app.call('POST', '/api/creature/pet');
      leveled = petted.data.events.find((event) => event.type === 'bond') ?? null;
    }
    assert.ok(leveled, 'debería subir de nivel');
    assert.equal(leveled.level, 1);
    const state = (await app.call('GET', '/api/state')).data.state;
    assert.ok(state.creature.bond >= 20);
  } finally {
    await app.close();
  }
});

async function hatchedPlayer(app, name) {
  await app.call('POST', '/api/auth/register', { name, password: 'secreta123', timeZone: 'Europe/Madrid' });
  await app.call('POST', '/api/egg/pick', { index: 0, name: 'Pipo' });
  for (let i = 0; i < 4; i++) await app.call('POST', '/api/egg/warm', { taps: 20 });
  return (await app.call('GET', '/api/state')).data.state;
}

test('misiones del día: progreso, premio y racha', async () => {
  const app = await startServer();
  try {
    const first = await hatchedPlayer(app, 'Misionera');
    assert.equal(first.user.timeZone, 'Europe/Madrid');
    assert.equal(first.daily.missions.length, 3);
    assert.deepEqual(first.daily.missions.map((m) => m.reward), [15, 25, 40]);
    assert.ok(first.daily.missions.every((m) => m.text && m.progress === 0 && !m.claimed));
    assert.equal(first.daily.streak, 0);
    assert.ok(first.daily.resetAt > first.now && first.daily.resetAt <= first.now + 24 * 60 * 60 * 1000);
    assert.equal((await app.call('POST', '/api/settings', { timeZone: 'Marte/Olimpo' })).status, 400);

    // Misiones fijas para que la prueba no dependa del sorteo.
    const fixed = [
      { id: 'comer', goal: 3, progress: 0, reward: 15, claimed: false },
      { id: 'mimos', goal: 10, progress: 0, reward: 25, claimed: false },
      { id: 'bano', goal: 1, progress: 0, reward: 40, claimed: false },
    ];
    const userId = (await app.db.get('SELECT id FROM users WHERE name = ?', ['Misionera'])).id;
    await app.db.run('UPDATE daily SET missions = ? WHERE user_id = ?', [JSON.stringify(fixed), userId]);

    const early = await app.call('POST', '/api/daily/claim', { id: 'comer' });
    assert.equal(early.status, 400);
    assert.match(early.data.error, /no la has cumplido/);

    for (const itemId of ['pan', 'manzana', 'manzana']) {
      assert.equal((await app.call('POST', '/api/creature/feed', { itemId })).status, 200);
    }
    for (let i = 0; i < 10; i++) await app.call('POST', '/api/creature/pet');
    const bathed = await app.call('POST', '/api/creature/bathe', {});
    assert.equal(bathed.status, 200, JSON.stringify(bathed.data));
    assert.deepEqual(bathed.data.state.daily.missions.map((m) => m.progress), [3, 10, 1]);

    const coins = bathed.data.state.user.coins;
    const a = await app.call('POST', '/api/daily/claim', { id: 'comer' });
    assert.deepEqual(a.data.result, { id: 'comer', coins: 15, bonus: 0, streak: null });
    assert.equal((await app.call('POST', '/api/daily/claim', { id: 'comer' })).status, 400);
    await app.call('POST', '/api/daily/claim', { id: 'mimos' });
    const last = await app.call('POST', '/api/daily/claim', { id: 'bano' });
    assert.equal(last.data.result.bonus, 20);
    assert.equal(last.data.result.streak, 1);
    assert.equal(last.data.state.user.coins, coins + 15 + 25 + 40 + 20);
    assert.equal(last.data.state.daily.doneToday, true);
    assert.equal(last.data.state.daily.streak, 1);

    // Al día siguiente hay otras tres y la racha sigue viva.
    const tomorrow = await app.call('POST', '/api/dev/skip', { hours: 24 });
    const daily = tomorrow.data.state.daily;
    assert.equal(daily.doneToday, false);
    assert.equal(daily.streak, 1);
    assert.equal(daily.bonus, 30);
    assert.ok(daily.missions.every((m) => !m.claimed));

    // Un día sin completarlas rompe la racha.
    const broken = await app.call('POST', '/api/dev/skip', { hours: 24 });
    assert.equal(broken.data.state.daily.streak, 0);
    assert.equal(broken.data.state.daily.best, 1);
  } finally {
    await app.close();
  }
});

const SUBSCRIPTION = {
  endpoint: 'https://fcm.googleapis.com/fcm/send/prueba-123',
  keys: { p256dh: `B${'A'.repeat(86)}`, auth: 'A'.repeat(22) },
};

test('avisos: suscripción, aviso sin repetir y baja al salir', async () => {
  const sent = [];
  const app = await startServer({}, {
    sender: async (subscription, payload, options) => {
      sent.push({ endpoint: subscription.endpoint, ...JSON.parse(payload), options });
    },
  });
  try {
    const state = await hatchedPlayer(app, 'Avisada');
    assert.match(state.push.key, /^[A-Za-z0-9_-]{80,}$/);
    assert.equal(state.push.prefs.care, true);

    const evil = { ...SUBSCRIPTION, endpoint: 'https://intranet.example.com/push' };
    assert.equal((await app.call('POST', '/api/push/subscribe', evil)).status, 400);
    assert.equal((await app.call('POST', '/api/push/subscribe', { endpoint: SUBSCRIPTION.endpoint })).status, 400);
    assert.equal((await app.call('POST', '/api/push/subscribe', SUBSCRIPTION)).status, 200);
    // Sin silencio nocturno, para que la prueba no dependa de la hora.
    const quiet = await app.call('POST', '/api/settings', { notify: { quiet: false, games: false, bogus: true } });
    assert.equal(quiet.data.state.push.prefs.quiet, false);
    assert.equal(quiet.data.state.push.prefs.bogus, undefined);

    // Tres horas sin cuidarla: el hambre y el aburrimiento llegan a crítico.
    await app.call('POST', '/api/dev/skip', { hours: 3 });
    const now = Date.now();
    const first = await app.push.sweep(now);
    assert.equal(first.delivered, 1);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].endpoint, SUBSCRIPTION.endpoint);
    assert.match(sent[0].title, /^Pipo /);
    assert.equal(sent[0].group, 'care');
    assert.equal(sent[0].options.urgency, 'high');

    // Lo mismo no se vuelve a avisar.
    const again = await app.push.sweep(now + 6 * 60 * 1000);
    assert.equal(again.checked, 1);
    assert.equal(sent.length, 1);

    // Con los avisos de cuidados apagados, no sale nada aunque haya motivo.
    await app.call('POST', '/api/settings', { notify: { care: false } });
    await app.db.run("UPDATE notify_state SET sent = '[]'");
    await app.push.sweep(now + 12 * 60 * 1000);
    assert.equal(sent.length, 1);

    // Al salir de la cuenta, este navegador deja de recibirlos.
    await app.call('POST', '/api/auth/logout');
    assert.equal((await app.db.all('SELECT endpoint FROM push_subscriptions')).length, 0);
    assert.equal((await app.push.sweep(now + 24 * 60 * 1000)).checked, 0);
  } finally {
    await app.close();
  }
});

test('avisos: un servicio que da la suscripción por perdida la borra', async () => {
  const app = await startServer({}, {
    sender: async () => {
      throw Object.assign(new Error('Gone'), { statusCode: 410 });
    },
  });
  try {
    await hatchedPlayer(app, 'Perdida');
    await app.call('POST', '/api/push/subscribe', SUBSCRIPTION);
    await app.call('POST', '/api/settings', { notify: { quiet: false } });
    await app.call('POST', '/api/dev/skip', { hours: 3 });
    const result = await app.push.sweep(Date.now());
    assert.equal(result.alerts.length, 1);
    assert.equal(result.delivered, 0);
    assert.equal((await app.db.all('SELECT endpoint FROM push_subscriptions')).length, 0);
  } finally {
    await app.close();
  }
});
