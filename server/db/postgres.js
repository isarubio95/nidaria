// Adaptador de PostgreSQL. Ofrece la misma interfaz que el de
// SQLite, así que las consultas no cambian: los `?` se convierten aquí en
// `$1`, `$2`…
//
// Las operaciones del juego llaman a `db.get` / `db.run` dentro de
// `db.tx(...)` sin recibir la conexión de la transacción. Para que esas
// llamadas vayan a la conexión correcta se usa AsyncLocalStorage.

import { AsyncLocalStorage } from 'node:async_hooks';
import pg from 'pg';

// BIGINT (milisegundos) llega como texto por defecto; aquí cabe en Number.
pg.types.setTypeParser(pg.types.builtins.INT8, (value) => Number(value));

// Conflictos de concurrencia que se resuelven repitiendo la transacción.
const RETRY_CODES = new Set(['40001', '40P01']);
const MAX_ATTEMPTS = 5;

// `?` -> `$n`. Las consultas del proyecto no llevan `?` dentro de textos.
function numbered(sql) {
  let n = 0;
  return sql.replace(/\?/g, () => `$${++n}`);
}

export function createPostgresDb(url) {
  const pool = new pg.Pool({ connectionString: url, max: 10, idleTimeoutMillis: 30_000 });
  // Un fallo de una conexión en reposo no debe tumbar el proceso.
  pool.on('error', (error) => console.error('PostgreSQL:', error.message));

  const current = new AsyncLocalStorage();
  const runner = () => current.getStore() ?? pool;
  const query = (sql, params) => runner().query(numbered(sql), params);

  const db = {
    dialect: 'postgres',
    async get(sql, params = []) {
      return (await query(sql, params)).rows[0] ?? null;
    },
    async all(sql, params = []) {
      return (await query(sql, params)).rows;
    },
    async run(sql, params = []) {
      return { changes: (await query(sql, params)).rowCount ?? 0 };
    },
    async exec(sql) {
      // Sin parámetros, admite varias sentencias seguidas.
      await runner().query(sql);
    },
    // SERIALIZABLE da la misma garantía que tenía SQLite (como si las
    // transacciones fueran de una en una). Si Postgres detecta un choque,
    // la transacción se repite entera.
    async tx(fn) {
      if (current.getStore()) return fn(db);
      for (let attempt = 1; ; attempt += 1) {
        const client = await pool.connect();
        let broken = false;
        try {
          await client.query('BEGIN ISOLATION LEVEL SERIALIZABLE');
          const value = await current.run(client, () => fn(db));
          await client.query('COMMIT');
          return value;
        } catch (error) {
          try {
            await client.query('ROLLBACK');
          } catch {
            broken = true;
          }
          if (RETRY_CODES.has(error.code) && attempt < MAX_ATTEMPTS) {
            await new Promise((resolve) => setTimeout(resolve, 10 * attempt + Math.random() * 20));
            continue;
          }
          throw error;
        } finally {
          client.release(broken);
        }
      }
    },
    close() {
      return pool.end();
    },
  };
  return db;
}
