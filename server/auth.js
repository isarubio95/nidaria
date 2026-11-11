// Cuentas y sesiones. Contraseñas con scrypt; la sesión es un token
// aleatorio en una cookie HttpOnly del que solo se guarda el hash.

import crypto from 'node:crypto';
import { promisify } from 'node:util';
import { STARTING_COINS, STARTING_INVENTORY } from '../shared/catalog.js';
import { TICKETS_MAX } from '../shared/economy.js';

const scrypt = promisify(crypto.scrypt);
const COOKIE = 'nid_session';
const NAME_RULE = /^[\p{L}\p{N}_-]{3,20}$/u;

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = await scrypt(password, salt, 64);
  return `scrypt$${salt.toString('base64')}$${hash.toString('base64')}`;
}

async function verifyPassword(password, stored) {
  const [scheme, salt, hash] = stored.split('$');
  if (scheme !== 'scrypt') return false;
  const expected = Buffer.from(hash, 'base64');
  const actual = await scrypt(password, Buffer.from(salt, 'base64'), expected.length);
  return crypto.timingSafeEqual(expected, actual);
}

const hashToken = (token) => crypto.createHash('sha256').update(token).digest('hex');

function readCookie(req) {
  const header = req.headers.cookie ?? '';
  for (const part of header.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === COOKIE) return decodeURIComponent(rest.join('='));
  }
  return null;
}

export function createAuth(db, config) {
  const maxAge = config.sessionDays * 24 * 60 * 60;

  function setCookie(res, token) {
    const parts = [`${COOKIE}=${encodeURIComponent(token)}`, 'Path=/', 'HttpOnly', 'SameSite=Lax', `Max-Age=${maxAge}`];
    if (config.secureCookies) parts.push('Secure');
    res.setHeader('Set-Cookie', parts.join('; '));
  }

  function clearCookie(res) {
    res.setHeader('Set-Cookie', `${COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`);
  }

  async function openSession(res, userId) {
    const token = crypto.randomBytes(32).toString('base64url');
    const now = Date.now();
    await db.run(
      'INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)',
      [hashToken(token), userId, now, now + maxAge * 1000],
    );
    setCookie(res, token);
  }

  function checkCredentials(name, password) {
    if (typeof name !== 'string' || !NAME_RULE.test(name.trim())) {
      throw new HttpError(400, 'El nombre debe tener de 3 a 20 letras, números, guiones o guiones bajos.');
    }
    if (typeof password !== 'string' || password.length < 8 || password.length > 200) {
      throw new HttpError(400, 'La contraseña debe tener al menos 8 caracteres.');
    }
  }

  return {
    async register(res, name, password) {
      checkCredentials(name, password);
      const clean = name.trim();
      const key = clean.toLocaleLowerCase('es');
      const passHash = await hashPassword(password);
      const id = crypto.randomUUID();
      const now = Date.now();
      await db.tx(async () => {
        const taken = await db.get('SELECT id FROM users WHERE name_key = ?', [key]);
        if (taken) throw new HttpError(409, 'Ese nombre ya está cogido. Prueba con otro.');
        await db.run(
          `INSERT INTO users (id, name, name_key, pass_hash, coins, income_at, tickets, tickets_at, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [id, clean, key, passHash, STARTING_COINS, now, TICKETS_MAX, now, now],
        );
        for (const [itemId, qty] of Object.entries(STARTING_INVENTORY)) {
          await db.run('INSERT INTO inventory (user_id, item_id, qty) VALUES (?, ?, ?)', [id, itemId, qty]);
        }
      });
      await openSession(res, id);
      return id;
    },

    async login(res, name, password) {
      if (typeof name !== 'string' || typeof password !== 'string') {
        throw new HttpError(400, 'Escribe tu nombre y tu contraseña.');
      }
      const user = await db.get('SELECT id, pass_hash FROM users WHERE name_key = ?', [name.trim().toLocaleLowerCase('es')]);
      const ok = user ? await verifyPassword(password, user.pass_hash) : false;
      if (!ok) throw new HttpError(401, 'El nombre o la contraseña no coinciden.');
      await openSession(res, user.id);
      return user.id;
    },

    async logout(req, res) {
      const token = readCookie(req);
      if (token) await db.run('DELETE FROM sessions WHERE token_hash = ?', [hashToken(token)]);
      clearCookie(res);
    },

    async userIdFrom(req) {
      const token = readCookie(req);
      if (!token) return null;
      const session = await db.get('SELECT user_id, expires_at FROM sessions WHERE token_hash = ?', [hashToken(token)]);
      if (!session || session.expires_at < Date.now()) return null;
      return session.user_id;
    },
  };
}
