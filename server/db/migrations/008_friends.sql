-- Amigos, visitas y regalos.

-- Código para que otros te añadan, sin guion (ver shared/social.js). Se
-- crea la primera vez que hace falta, así que las cuentas de antes también
-- lo tienen.
ALTER TABLE users ADD COLUMN friend_code TEXT;
CREATE UNIQUE INDEX users_friend_code ON users (friend_code);

-- Solicitudes que esperan respuesta.
CREATE TABLE friend_requests (
  from_id TEXT NOT NULL REFERENCES users (id),
  to_id TEXT NOT NULL REFERENCES users (id),
  created_at BIGINT NOT NULL,
  PRIMARY KEY (from_id, to_id)
);
CREATE INDEX friend_requests_to ON friend_requests (to_id);

-- Una fila por cada lado de la amistad. `visited_at` es la última vez que
-- `friend_id` vino a ver la habitación de `user_id`.
CREATE TABLE friends (
  user_id TEXT NOT NULL REFERENCES users (id),
  friend_id TEXT NOT NULL REFERENCES users (id),
  created_at BIGINT NOT NULL,
  visited_at BIGINT,
  PRIMARY KEY (user_id, friend_id)
);

-- Regalos entre amigos. `day` es el día local de quien lo manda: uno por
-- amigo y día. Sin `opened_at`, sigue envuelto.
CREATE TABLE gifts (
  id TEXT PRIMARY KEY,
  from_id TEXT NOT NULL REFERENCES users (id),
  to_id TEXT NOT NULL REFERENCES users (id),
  item_id TEXT NOT NULL,
  day TEXT NOT NULL,
  sent_at BIGINT NOT NULL,
  opened_at BIGINT
);
CREATE UNIQUE INDEX gifts_daily ON gifts (from_id, to_id, day);
CREATE INDEX gifts_to ON gifts (to_id, opened_at);
