// Cuentas y sesiones. Contraseñas con scrypt; la sesión es un token
// aleatorio en una cookie HttpOnly del que solo se guarda el hash.
// No hay correo: si se olvida la contraseña, la cuenta se recupera con un
// código que se enseña una sola vez al crearla.

import crypto from 'node:crypto';
import { promisify } from 'node:util';
import { STARTING_COINS, STARTING_INVENTORY } from '../shared/catalog.js';
import { TICKETS_MAX } from '../shared/economy.js';

const scrypt = promisify(crypto.scrypt);
const COOKIE = 'nid_session';
const NAME_RULE = /^[\p{L}\p{N}_-]{3,20}$/u;

// Código de recuperación: 16 caracteres del alfabeto de Crockford (sin I,
// L, O ni U, que se confunden), en cuatro grupos. Son 80 bits: no se
// puede adivinar probando.
const CODE_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const CODE_LENGTH = 16;

function newRecoveryCode() {
  let code = '';
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)];
  return code.match(/.{4}/g).join('-');
}

// Lo que escribe el jugador, sin guiones ni espacios y en mayúsculas. Las
// letras que se confunden con cifras cuentan como esas cifras. null si no
// tiene la forma de un código.
export function normalizeRecoveryCode(input) {
  if (typeof input !== 'string') return null;
  const clean = input.toUpperCase().replace(/[\s-]/g, '').replace(/O/g, '0').replace(/[IL]/g, '1');
  if (clean.length !== CODE_LENGTH) return null;
  for (const char of clean) if (!CODE_ALPHABET.includes(char)) return null;
  return clean;
}

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

  function checkPassword(password) {
    if (typeof password !== 'string' || password.length < 8 || password.length > 200) {
      throw new HttpError(400, 'La contraseña debe tener al menos 8 caracteres.');
    }
  }

  function checkCredentials(name, password) {
    if (typeof name !== 'string' || !NAME_RULE.test(name.trim())) {
      throw new HttpError(400, 'El nombre debe tener de 3 a 20 letras, números, guiones o guiones bajos.');
    }
    checkPassword(password);
  }

  // Un código nuevo y su hash. El código solo viaja en esta respuesta.
  async function recoveryPair() {
    const code = newRecoveryCode();
    return { code, hash: await hashPassword(normalizeRecoveryCode(code)) };
  }

  return {
    async register(res, name, password) {
      checkCredentials(name, password);
      const clean = name.trim();
      const key = clean.toLocaleLowerCase('es');
      const passHash = await hashPassword(password);
      const recovery = await recoveryPair();
      const id = crypto.randomUUID();
      const now = Date.now();
      await db.tx(async () => {
        const taken = await db.get('SELECT id FROM users WHERE name_key = ?', [key]);
        if (taken) throw new HttpError(409, 'Ese nombre ya está cogido. Prueba con otro.');
        await db.run(
          `INSERT INTO users (id, name, name_key, pass_hash, recovery_hash, coins, income_at, tickets, tickets_at, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [id, clean, key, passHash, recovery.hash, STARTING_COINS, now, TICKETS_MAX, now, now],
        );
        for (const [itemId, qty] of Object.entries(STARTING_INVENTORY)) {
          await db.run('INSERT INTO inventory (user_id, item_id, qty) VALUES (?, ?, ?)', [id, itemId, qty]);
        }
      });
      await openSession(res, id);
      return { userId: id, recoveryCode: recovery.code };
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

    // Nueva contraseña con el código de recuperación. El código se gasta:
    // se devuelve otro y se cierran todas las sesiones abiertas, por si la
    // cuenta estaba en manos de otro.
    async recover(res, name, code, password) {
      if (typeof name !== 'string' || typeof code !== 'string') {
        throw new HttpError(400, 'Escribe tu nombre y tu código de recuperación.');
      }
      checkPassword(password);
      const clean = normalizeRecoveryCode(code);
      if (!clean) throw new HttpError(400, 'El código tiene 16 letras y números, como ABCD-EFGH-JKMN-PQRS.');
      const user = await db.get('SELECT id, recovery_hash FROM users WHERE name_key = ?', [name.trim().toLocaleLowerCase('es')]);
      const ok = user?.recovery_hash ? await verifyPassword(clean, user.recovery_hash) : false;
      if (!ok) throw new HttpError(401, 'El nombre o el código no coinciden.');
      const passHash = await hashPassword(password);
      const recovery = await recoveryPair();
      await db.tx(async () => {
        // Si otra petición gastó el código mientras tanto, esta no vale.
        const { changes } = await db.run(
          'UPDATE users SET pass_hash = ?, recovery_hash = ? WHERE id = ? AND recovery_hash = ?',
          [passHash, recovery.hash, user.id, user.recovery_hash],
        );
        if (!changes) throw new HttpError(401, 'El nombre o el código no coinciden.');
        await db.run('DELETE FROM push_subscriptions WHERE user_id = ?', [user.id]);
        await db.run('DELETE FROM sessions WHERE user_id = ?', [user.id]);
      });
      await openSession(res, user.id);
      return { userId: user.id, recoveryCode: recovery.code };
    },

    // Código nuevo desde la ficha, para quien no lo guardó o lo ha perdido.
    // Pide la contraseña: con solo la sesión, quien la robara podría
    // quedarse con la cuenta. El código anterior deja de valer.
    async renewRecovery(userId, password) {
      if (typeof password !== 'string' || !password) throw new HttpError(400, 'Escribe tu contraseña.');
      const user = await db.get('SELECT pass_hash FROM users WHERE id = ?', [userId]);
      const ok = user ? await verifyPassword(password, user.pass_hash) : false;
      // 403 y no 401: el cliente entiende 401 como sesión caducada.
      if (!ok) throw new HttpError(403, 'La contraseña no es correcta.');
      const recovery = await recoveryPair();
      await db.run('UPDATE users SET recovery_hash = ? WHERE id = ?', [recovery.hash, userId]);
      return recovery.code;
    },

    async logout(req, res) {
      const token = readCookie(req);
      if (token) {
        const tokenHash = hashToken(token);
        // Este navegador deja de recibir avisos de la cuenta.
        await db.run('DELETE FROM push_subscriptions WHERE session_hash = ?', [tokenHash]);
        await db.run('DELETE FROM sessions WHERE token_hash = ?', [tokenHash]);
      }
      clearCookie(res);
    },

    // { userId, tokenHash } de la sesión de la petición, o null.
    async sessionFrom(req) {
      const token = readCookie(req);
      if (!token) return null;
      const tokenHash = hashToken(token);
      const session = await db.get('SELECT user_id, expires_at FROM sessions WHERE token_hash = ?', [tokenHash]);
      if (!session || session.expires_at < Date.now()) return null;
      return { userId: session.user_id, tokenHash };
    },
  };
}
