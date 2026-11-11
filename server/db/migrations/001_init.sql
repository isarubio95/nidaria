-- Esquema inicial. Solo tipos y sintaxis que también acepta PostgreSQL:
-- las fechas son milisegundos en BIGINT, los booleanos 0/1 y los datos
-- compuestos van como JSON en TEXT.

CREATE TABLE users (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  name_key TEXT NOT NULL UNIQUE,
  pass_hash TEXT NOT NULL,
  coins INTEGER NOT NULL,
  income_at BIGINT NOT NULL,
  tickets INTEGER NOT NULL,
  tickets_at BIGINT NOT NULL,
  egg_offer TEXT,
  created_at BIGINT NOT NULL
);

CREATE TABLE sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users (id),
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL
);
CREATE INDEX sessions_user ON sessions (user_id);

-- `data` guarda la criatura completa. `active` marca la que el jugador
-- cuida ahora; las anteriores se conservan como historial.
CREATE TABLE creatures (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users (id),
  active INTEGER NOT NULL,
  alive INTEGER NOT NULL,
  form_id TEXT NOT NULL,
  data TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
CREATE INDEX creatures_user ON creatures (user_id, active);

CREATE TABLE inventory (
  user_id TEXT NOT NULL REFERENCES users (id),
  item_id TEXT NOT NULL,
  qty INTEGER NOT NULL,
  PRIMARY KEY (user_id, item_id)
);

CREATE TABLE criadex (
  user_id TEXT NOT NULL REFERENCES users (id),
  form_id TEXT NOT NULL,
  first_at BIGINT NOT NULL,
  times INTEGER NOT NULL,
  PRIMARY KEY (user_id, form_id)
);

CREATE TABLE plays (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users (id),
  game_id TEXT NOT NULL,
  difficulty TEXT NOT NULL,
  started_at BIGINT NOT NULL,
  finished_at BIGINT,
  score INTEGER,
  won INTEGER,
  reward INTEGER
);
CREATE INDEX plays_user ON plays (user_id, started_at);
