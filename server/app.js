import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import express from 'express';
import { ROOT } from './config.js';
import { createAuth, HttpError } from './auth.js';
import { createGame } from './game/service.js';

// Límite de peticiones en memoria, por IP y ventana de un minuto.
function rateLimit(max) {
  const hits = new Map();
  setInterval(() => hits.clear(), 60 * 1000).unref();
  return (req, res, next) => {
    const key = req.ip ?? 'desconocida';
    const count = (hits.get(key) ?? 0) + 1;
    hits.set(key, count);
    if (count > max) {
      res.status(429).json({ error: 'Demasiadas peticiones seguidas. Espera un momento.' });
      return;
    }
    next();
  };
}

// La página lleva un mapa de importación en línea; su hash va en la
// política de seguridad para no tener que permitir scripts en línea.
function importMapHash() {
  const file = path.join(ROOT, 'public', 'index.html');
  if (!fs.existsSync(file)) return '';
  const match = fs.readFileSync(file, 'utf8').match(/<script type="importmap">([\s\S]*?)<\/script>/);
  if (!match) return '';
  return `'sha256-${crypto.createHash('sha256').update(match[1]).digest('base64')}'`;
}

export function createApp(db, config) {
  const app = express();
  const auth = createAuth(db, config);
  const game = createGame(db, config);

  app.disable('x-powered-by');
  const csp = [
    "default-src 'self'",
    `script-src 'self' ${importMapHash()}`.trim(),
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self'",
    "connect-src 'self'",
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
  app.use((req, res, next) => {
    res.setHeader('Content-Security-Policy', csp);
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'same-origin');
    next();
  });

  const vendor = { maxAge: '1d' };
  app.use('/vendor/three', express.static(path.join(ROOT, 'node_modules', 'three'), vendor));
  app.use('/vendor/gsap', express.static(path.join(ROOT, 'node_modules', 'gsap'), vendor));
  app.use('/vendor/fonts', express.static(path.join(ROOT, 'node_modules', '@fontsource', 'zen-maru-gothic', 'files'), vendor));
  app.use('/shared', express.static(path.join(ROOT, 'shared')));
  app.use(express.static(path.join(ROOT, 'public')));

  const api = express.Router();
  api.use(express.json({ limit: '10kb' }));
  api.use(rateLimit(600));

  // Envuelve un manejador asíncrono y responde con su resultado en JSON.
  const handle = (fn) => async (req, res, next) => {
    try {
      res.json(await fn(req, res));
    } catch (error) {
      next(error);
    }
  };

  const authLimit = rateLimit(20);
  api.post('/auth/register', authLimit, handle(async (req, res) => {
    const userId = await auth.register(res, req.body?.name, req.body?.password);
    return game.state(userId);
  }));
  api.post('/auth/login', authLimit, handle(async (req, res) => {
    const userId = await auth.login(res, req.body?.name, req.body?.password);
    return game.state(userId);
  }));
  api.post('/auth/logout', handle(async (req, res) => {
    await auth.logout(req, res);
    return { ok: true };
  }));

  api.use(async (req, res, next) => {
    try {
      const userId = await auth.userIdFrom(req);
      if (!userId) throw new HttpError(401, 'Entra con tu cuenta para jugar.');
      req.userId = userId;
      next();
    } catch (error) {
      next(error);
    }
  });

  const body = (req) => req.body ?? {};
  api.get('/state', handle((req) => game.state(req.userId)));
  api.post('/egg/pick', handle((req) => game.pickEgg(req.userId, Number(body(req).index), body(req).name)));
  api.post('/egg/warm', handle((req) => game.warmEgg(req.userId, body(req).taps)));
  api.post('/creature/feed', handle((req) => game.feed(req.userId, body(req).itemId)));
  api.post('/creature/sleep', handle((req) => game.sleep(req.userId, body(req).asleep)));
  api.post('/creature/bathe', handle((req) => game.bathe(req.userId, body(req).itemId)));
  api.post('/creature/heal', handle((req) => game.heal(req.userId, body(req).itemId)));
  api.post('/creature/pet', handle((req) => game.pet(req.userId)));
  api.post('/creature/toy', handle((req) => game.toy(req.userId, body(req).itemId)));
  api.post('/creature/rename', handle((req) => game.rename(req.userId, body(req).name)));
  api.post('/creature/equip', handle((req) => game.equip(req.userId, body(req).slot, body(req).itemId)));
  api.post('/shop/buy', handle((req) => game.buy(req.userId, body(req).itemId, body(req).qty)));
  api.post('/income/collect', handle((req) => game.collectIncome(req.userId)));
  api.post('/games/start', handle((req) => game.startGame(req.userId, body(req).gameId, body(req).difficulty)));
  api.post('/games/finish', handle((req) => game.finishGame(req.userId, body(req).playId, body(req).score)));
  api.post('/dev/skip', handle((req) => game.devSkip(req.userId, body(req).hours)));

  api.use((req, res) => res.status(404).json({ error: 'No existe.' }));
  // eslint-disable-next-line no-unused-vars
  api.use((error, req, res, next) => {
    if (error instanceof HttpError) {
      res.status(error.status).json({ error: error.message });
      return;
    }
    if (error?.type === 'entity.parse.failed' || error?.type === 'entity.too.large') {
      res.status(400).json({ error: 'La petición no se entiende.' });
      return;
    }
    console.error(error);
    res.status(500).json({ error: 'Algo ha fallado en el servidor. Inténtalo de nuevo.' });
  });

  app.use('/api', api);
  return app;
}
