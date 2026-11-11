import { loadConfig } from './config.js';
import { createSqliteDb } from './db/index.js';
import { migrate } from './db/migrate.js';
import { createApp } from './app.js';

const config = loadConfig();
const db = createSqliteDb(config.dbFile);
await migrate(db);

const app = createApp(db, config);
const server = app.listen(config.port, config.host, () => {
  console.log(`Nidaria en http://localhost:${config.port}`);
  if (config.timeScale !== 1) console.log(`Tiempo del juego acelerado x${config.timeScale}`);
  if (config.devTools) console.log('Herramientas de desarrollo activadas');
});

const stop = () => server.close(() => {
  db.close();
  process.exit(0);
});
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
