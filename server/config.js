import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function loadConfig(env = process.env) {
  return {
    port: Number(env.PORT ?? 3000),
    host: env.HOST ?? '0.0.0.0',
    // Con DATABASE_URL se usa PostgreSQL (Supabase); sin ella, SQLite.
    databaseUrl: env.DATABASE_URL || null,
    // Certificado de Supabase para verificar la conexión cifrada.
    databaseCaFile: env.DATABASE_CA_FILE || null,
    dbFile: env.DB_FILE ?? path.join(ROOT, 'data', 'nidaria.db'),
    // Multiplica la velocidad del tiempo del juego. 60 hace que una hora
    // pase en un minuto: útil para probar evoluciones.
    timeScale: Math.max(1, Number(env.TIME_SCALE ?? 1)),
    // Activa el botón de adelantar el tiempo. Nunca en producción.
    devTools: env.DEV_TOOLS === '1',
    secureCookies: env.SECURE_COOKIES === '1',
    // Detrás de un proxy en la misma máquina (Caddy, nginx): toma la IP
    // real del jugador de X-Forwarded-For para el límite de peticiones.
    trustProxy: env.TRUST_PROXY === '1',
    // Claves de los avisos al móvil (Web Push). Si faltan, se generan y se
    // guardan en la base de datos. El contacto va en cada aviso enviado:
    // un mailto: o la dirección https del juego.
    vapidPublicKey: env.VAPID_PUBLIC_KEY || null,
    vapidPrivateKey: env.VAPID_PRIVATE_KEY || null,
    vapidSubject: env.VAPID_SUBJECT || null,
    sessionDays: 30,
  };
}
