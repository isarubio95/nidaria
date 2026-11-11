import fs from 'node:fs';
import path from 'node:path';
import { ROOT } from '../config.js';

const DIR = path.join(ROOT, 'server', 'db', 'migrations');

// Aplica, en orden, los ficheros .sql que aún no se hayan aplicado.
export async function migrate(db) {
  await db.exec('CREATE TABLE IF NOT EXISTS schema_migrations (id TEXT PRIMARY KEY, applied_at BIGINT NOT NULL)');
  const done = new Set((await db.all('SELECT id FROM schema_migrations')).map((row) => row.id));
  const files = fs.readdirSync(DIR).filter((name) => name.endsWith('.sql')).sort();
  for (const file of files) {
    if (done.has(file)) continue;
    const sql = fs.readFileSync(path.join(DIR, file), 'utf8');
    await db.tx(async () => {
      await db.exec(sql);
      await db.run('INSERT INTO schema_migrations (id, applied_at) VALUES (?, ?)', [file, Date.now()]);
    });
  }
}
