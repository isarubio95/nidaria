-- Solo PostgreSQL (Supabase). Supabase publica el esquema `public` por su
-- API REST con las claves `anon` y `authenticated`. Activar RLS sin
-- políticas cierra ese acceso: solo el servidor, que se conecta como
-- propietario de las tablas, puede leerlas y escribirlas.

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE creatures ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory ENABLE ROW LEVEL SECURITY;
ALTER TABLE criadex ENABLE ROW LEVEL SECURITY;
ALTER TABLE plays ENABLE ROW LEVEL SECURITY;
ALTER TABLE schema_migrations ENABLE ROW LEVEL SECURITY;
