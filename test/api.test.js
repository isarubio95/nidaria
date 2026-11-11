import test from 'node:test';
import assert from 'node:assert/strict';
import { createSqliteDb } from '../server/db/index.js';
import { migrate } from '../server/db/migrate.js';
import { createApp } from '../server/app.js';

async function startServer(overrides = {}) {
  const db = createSqliteDb(':memory:');
  await migrate(db);
  const config = { timeScale: 1, devTools: true, secureCookies: false, sessionDays: 30, ...overrides };
  const server = createApp(db, config).listen(0);
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
    dropCookie: () => { cookie = ''; },
    close: () => new Promise((resolve) => server.close(() => { db.close(); resolve(); })),
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
