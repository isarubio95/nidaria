// Avisos al móvil con Web Push. Guarda las suscripciones de cada
// navegador y, cada poco, mira qué jugadores tienen algo que avisar
// (hambre, enfermedad, una evolución, la racha…) y se lo manda.
//
// Las claves VAPID salen de las variables de entorno; si no están, se
// generan una vez y se guardan en la base de datos.

import webpush from 'web-push';
import { HttpError } from './auth.js';
import { simulate } from '../shared/needs.js';
import { localHour, DEFAULT_TIME_ZONE } from '../shared/daily.js';
import { alertsFor, cleanPrefs, isQuietHour, DEFAULT_PREFS } from './game/alerts.js';

// Solo se mandan avisos a los servicios de push de los navegadores. Así
// nadie puede usar el servidor para hacer peticiones a otras direcciones.
const PUSH_HOSTS = [
  /^fcm\.googleapis\.com$/,
  /^android\.googleapis\.com$/,
  /\.push\.services\.mozilla\.com$/,
  /\.notify\.windows\.com$/,
  /\.push\.apple\.com$/,
];
const KEY_RULE = /^[A-Za-z0-9_-]+={0,2}$/;
const MAX_SUBSCRIPTIONS = 10;
const SWEEP_MS = 30 * 1000;
const BATCH = 100;
// Un aviso viejo no sirve: si el móvil está apagado más de seis horas, se descarta.
const TTL_SECONDS = 6 * 60 * 60;

function cleanSubscription(body) {
  const endpoint = body?.endpoint;
  const p256dh = body?.keys?.p256dh;
  const auth = body?.keys?.auth;
  const bad = () => new HttpError(400, 'Este navegador no ha dado una suscripción válida.');
  if (typeof endpoint !== 'string' || endpoint.length > 1024) throw bad();
  let url;
  try {
    url = new URL(endpoint);
  } catch {
    throw bad();
  }
  if (url.protocol !== 'https:' || !PUSH_HOSTS.some((rule) => rule.test(url.hostname))) {
    throw new HttpError(400, 'Ese servicio de avisos no está admitido.');
  }
  if (typeof p256dh !== 'string' || p256dh.length < 40 || p256dh.length > 140 || !KEY_RULE.test(p256dh)) throw bad();
  if (typeof auth !== 'string' || auth.length < 8 || auth.length > 60 || !KEY_RULE.test(auth)) throw bad();
  return { endpoint, p256dh, auth };
}

export function createPush(db, config, { sender } = {}) {
  const scale = config.timeScale;
  // Cada jugador se vuelve a mirar a los cinco minutos de juego (el paso
  // de la simulación), y nunca más a menudo que cada barrido.
  const checkMs = Math.max(SWEEP_MS, (5 * 60 * 1000) / scale);
  const subject = config.vapidSubject || 'mailto:avisos@nidaria.invalid';
  let keysPromise = null;
  let timer = null;

  async function loadKeys() {
    if (config.vapidPublicKey && config.vapidPrivateKey) {
      return { publicKey: config.vapidPublicKey, privateKey: config.vapidPrivateKey };
    }
    const read = async () => {
      const row = await db.get("SELECT value FROM server_keys WHERE name = 'vapid'");
      return row ? JSON.parse(row.value) : null;
    };
    const stored = await read();
    if (stored) return stored;
    const fresh = webpush.generateVAPIDKeys();
    await db.run(
      "INSERT INTO server_keys (name, value) VALUES ('vapid', ?) ON CONFLICT (name) DO NOTHING",
      [JSON.stringify(fresh)],
    );
    // Si otro proceso las ha creado a la vez, valen las suyas.
    return read();
  }

  function keys() {
    keysPromise ??= loadKeys().catch((error) => {
      keysPromise = null;
      throw error;
    });
    return keysPromise;
  }

  const send = sender ?? (async (subscription, payload, options) => {
    const { publicKey, privateKey } = await keys();
    await webpush.sendNotification(subscription, payload, {
      ...options,
      vapidDetails: { subject, publicKey, privateKey },
    });
  });

  async function prefsOf(userId) {
    const row = await db.get('SELECT prefs FROM notify_state WHERE user_id = ?', [userId]);
    return row ? cleanPrefs(JSON.parse(row.prefs)) : { ...DEFAULT_PREFS };
  }

  async function ensureState(userId, now) {
    await db.run(
      `INSERT INTO notify_state (user_id, prefs, sent, check_at) VALUES (?, ?, '[]', ?)
       ON CONFLICT (user_id) DO NOTHING`,
      [userId, JSON.stringify(DEFAULT_PREFS), now],
    );
  }

  // Decide, dentro de una transacción, si hay que avisar a un jugador.
  // Devuelve el aviso o null; el envío se hace después, fuera.
  async function evaluate(userId, now) {
    const state = await db.get('SELECT prefs, sent FROM notify_state WHERE user_id = ?', [userId]);
    const user = await db.get('SELECT id, time_zone, tickets, tickets_at FROM users WHERE id = ?', [userId]);
    if (!state || !user) return null;
    const row = await db.get('SELECT data FROM creatures WHERE user_id = ? AND active = 1', [userId]);
    const saved = row ? JSON.parse(row.data) : null;
    // Solo se lee: la criatura se guarda cuando el jugador vuelve.
    const live = saved ? simulate(saved, now, { scale }).creature : null;
    const daily = await db.get('SELECT streak, streak_day FROM daily WHERE user_id = ?', [userId]);
    const timeZone = user.time_zone ?? DEFAULT_TIME_ZONE;
    const prefs = cleanPrefs(JSON.parse(state.prefs));

    const alerts = alertsFor({ saved, live, user, daily, now, timeZone, prefs, scale });
    const active = new Set(alerts.map((alert) => alert.key));
    const sent = JSON.parse(state.sent);
    // Lo que ya no está vigente se olvida: si vuelve a pasar, se avisa otra vez.
    const keep = sent.filter((key) => active.has(key));
    const fresh = alerts.filter((alert) => !sent.includes(alert.key));
    const quiet = prefs.quiet && isQuietHour(localHour(now, timeZone));

    let alert = null;
    if (fresh.length && !quiet) {
      // Un aviso cada vez: el más urgente. Los demás se dan por vistos,
      // porque al entrar el jugador los verá todos.
      alert = fresh[0];
      keep.push(...fresh.map((item) => item.key));
    }
    await db.run('UPDATE notify_state SET sent = ?, check_at = ? WHERE user_id = ?', [JSON.stringify(keep), now + checkMs, userId]);
    return alert ? { userId, alert } : null;
  }

  async function deliver({ userId, alert }, now) {
    const subscriptions = await db.all(
      `SELECT p.endpoint, p.p256dh, p.auth FROM push_subscriptions p
       JOIN sessions s ON s.token_hash = p.session_hash
       WHERE p.user_id = ? AND s.expires_at > ?`,
      [userId, now],
    );
    const payload = JSON.stringify({ title: alert.title, body: alert.body, tag: 'nidaria', group: alert.group });
    const options = { TTL: TTL_SECONDS, urgency: alert.group === 'care' ? 'high' : 'normal', topic: alert.group };
    let delivered = 0;
    for (const sub of subscriptions) {
      try {
        await send({ endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } }, payload, options);
        delivered += 1;
      } catch (error) {
        // 404 y 410: el navegador ya no quiere avisos de esta suscripción.
        if (error?.statusCode === 404 || error?.statusCode === 410) {
          await db.run('DELETE FROM push_subscriptions WHERE endpoint = ?', [sub.endpoint]);
        } else {
          console.error('Aviso no enviado:', error?.statusCode ?? '', error?.body ?? error?.message ?? error);
        }
      }
    }
    return delivered;
  }

  async function sweep(now = Date.now()) {
    const due = await db.all(
      `SELECT n.user_id FROM notify_state n
       WHERE n.check_at <= ? AND EXISTS (
         SELECT 1 FROM push_subscriptions p JOIN sessions s ON s.token_hash = p.session_hash
         WHERE p.user_id = n.user_id AND s.expires_at > ?
       )
       ORDER BY n.check_at LIMIT ${BATCH}`,
      [now, now],
    );
    const plans = [];
    for (const { user_id: userId } of due) {
      const plan = await db.tx(() => evaluate(userId, now));
      if (plan) plans.push(plan);
    }
    let delivered = 0;
    for (const plan of plans) delivered += await deliver(plan, now);
    return { checked: due.length, alerts: plans, delivered };
  }

  return {
    async publicKey() {
      return (await keys()).publicKey;
    },

    prefsOf,

    async setPrefs(userId, input) {
      const now = Date.now();
      await ensureState(userId, now);
      const prefs = cleanPrefs(input, await prefsOf(userId));
      await db.run('UPDATE notify_state SET prefs = ?, check_at = ? WHERE user_id = ?', [JSON.stringify(prefs), now, userId]);
      return prefs;
    },

    async subscribe(userId, sessionHash, body) {
      const sub = cleanSubscription(body);
      const now = Date.now();
      await db.tx(async () => {
        await db.run(
          `INSERT INTO push_subscriptions (endpoint, user_id, session_hash, p256dh, auth, created_at)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT (endpoint) DO UPDATE SET user_id = excluded.user_id, session_hash = excluded.session_hash,
             p256dh = excluded.p256dh, auth = excluded.auth`,
          [sub.endpoint, userId, sessionHash, sub.p256dh, sub.auth, now],
        );
        const extra = await db.all(
          'SELECT endpoint FROM push_subscriptions WHERE user_id = ? ORDER BY created_at DESC, endpoint',
          [userId],
        );
        for (const row of extra.slice(MAX_SUBSCRIPTIONS)) {
          await db.run('DELETE FROM push_subscriptions WHERE endpoint = ?', [row.endpoint]);
        }
        await ensureState(userId, now);
      });
      return { ok: true };
    },

    async unsubscribe(userId, endpoint) {
      if (typeof endpoint === 'string') {
        await db.run('DELETE FROM push_subscriptions WHERE endpoint = ? AND user_id = ?', [endpoint, userId]);
      }
      return { ok: true };
    },

    sweep,

    start() {
      if (timer) return;
      let running = false;
      const tick = async () => {
        if (running) return;
        running = true;
        try {
          await sweep();
        } catch (error) {
          console.error('Avisos:', error);
        } finally {
          running = false;
        }
      };
      keys().then(() => {
        timer = setInterval(tick, SWEEP_MS);
        timer.unref();
      }).catch((error) => console.error('No se han podido preparar las claves de los avisos:', error));
    },

    stop() {
      clearInterval(timer);
      timer = null;
    },
  };
}
