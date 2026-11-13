import { loadConfig } from './config.js';
import { createSqliteDb } from './db/index.js';
import { createPostgresDb } from './db/postgres.js';
import { migrate } from './db/migrate.js';
import { createApp } from './app.js';
import { createPush } from './push.js';

const config = loadConfig();
const db = config.databaseUrl
  ? createPostgresDb(config.databaseUrl, { caFile: config.databaseCaFile })
  : createSqliteDb(config.dbFile);
await migrate(db);
console.log(db.dialect === 'postgres' ? 'Base de datos: PostgreSQL' : `Base de datos: SQLite (${config.dbFile})`);

const push = createPush(db, config);
const app = createApp(db, config, { push });
const server = app.listen(config.port, config.host, () => {
  console.log(`Nidaria en http://localhost:${config.port}`);
  push.start();
  if (config.timeScale !== 1) console.log(`Tiempo del juego acelerado x${config.timeScale}`);
  if (config.devTools) console.log('Herramientas de desarrollo activadas');
});

const stop = () => server.close(async () => {
  push.stop();
  await db.close();
  process.exit(0);
});
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
