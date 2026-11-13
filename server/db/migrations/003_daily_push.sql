-- Misiones del día, avisos al móvil y zona horaria del jugador.

-- Zona horaria IANA (p. ej. 'Europe/Madrid'). Decide cuándo cambian las
-- misiones y cuándo es de noche para no mandar avisos.
ALTER TABLE users ADD COLUMN time_zone TEXT;

-- `day` es la fecha local de las misiones guardadas ('AAAA-MM-DD').
-- `streak_day` es el último día en que se recogieron las tres.
CREATE TABLE daily (
  user_id TEXT PRIMARY KEY REFERENCES users (id),
  day TEXT NOT NULL,
  missions TEXT NOT NULL,
  streak INTEGER NOT NULL,
  streak_day TEXT,
  best INTEGER NOT NULL
);

-- Un navegador suscrito a los avisos. Va ligado a la sesión con la que se
-- suscribió: al salir de la cuenta o caducar la sesión deja de recibirlos.
CREATE TABLE push_subscriptions (
  endpoint TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users (id),
  session_hash TEXT NOT NULL,
  p256dh TEXT NOT NULL,
  auth TEXT NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE INDEX push_subscriptions_user ON push_subscriptions (user_id);
CREATE INDEX push_subscriptions_session ON push_subscriptions (session_hash);

-- Preferencias de avisos, avisos ya enviados que siguen vigentes (para no
-- repetirlos) y cuándo toca volver a mirar.
CREATE TABLE notify_state (
  user_id TEXT PRIMARY KEY REFERENCES users (id),
  prefs TEXT NOT NULL,
  sent TEXT NOT NULL,
  check_at BIGINT NOT NULL
);
CREATE INDEX notify_state_check ON notify_state (check_at);

-- Claves del servidor que se generan solas la primera vez, como las de
-- los avisos si no vienen en las variables de entorno.
CREATE TABLE server_keys (
  name TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
