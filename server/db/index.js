// Capa de datos. El resto del servidor solo conoce esta interfaz:
//   get(sql, params)  -> una fila o null
//   all(sql, params)  -> lista de filas
//   run(sql, params)  -> { changes }
//   tx(fn)            -> ejecuta fn dentro de una transacción
// Todo es asíncrono y el SQL usa `?` y tipos comunes, para que pasar de
// SQLite a PostgreSQL sea escribir otro adaptador y no tocar las consultas.

import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

export function createSqliteDb(file) {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
  const sqlite = new DatabaseSync(file);
  sqlite.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;');

  const statements = new Map();
  const prepare = (sql) => {
    let statement = statements.get(sql);
    if (!statement) {
      statement = sqlite.prepare(sql);
      statements.set(sql, statement);
    }
    return statement;
  };

  // Una sola conexión: las transacciones se encolan para que dos peticiones
  // no mezclen sus sentencias.
  let queue = Promise.resolve();

  const db = {
    dialect: 'sqlite',
    async get(sql, params = []) {
      return prepare(sql).get(...params) ?? null;
    },
    async all(sql, params = []) {
      return prepare(sql).all(...params);
    },
    async run(sql, params = []) {
      const info = prepare(sql).run(...params);
      return { changes: Number(info.changes) };
    },
    async exec(sql) {
      sqlite.exec(sql);
    },
    tx(fn) {
      const result = queue.then(async () => {
        sqlite.exec('BEGIN IMMEDIATE');
        try {
          const value = await fn(db);
          sqlite.exec('COMMIT');
          return value;
        } catch (error) {
          sqlite.exec('ROLLBACK');
          throw error;
        }
      });
      queue = result.catch(() => {});
      return result;
    },
    close() {
      sqlite.close();
    },
  };
  return db;
}
