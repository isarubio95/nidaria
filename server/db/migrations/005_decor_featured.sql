-- Decoración de la habitación y juego del día.

-- Lo que el jugador ha puesto en cada sitio de su habitación, en JSON
-- ({"pared": "papel-rayas", "rincon": "lampara"}). Sin valor, la de base.
-- Es del jugador y no de la criatura: sigue igual cuando llega otra.
ALTER TABLE users ADD COLUMN decor TEXT;

-- 1 si la partida empezó cuando su juego era el juego del día: paga más.
ALTER TABLE plays ADD COLUMN featured INTEGER NOT NULL DEFAULT 0;
