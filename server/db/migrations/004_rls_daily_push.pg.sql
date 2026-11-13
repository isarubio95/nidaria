-- Solo PostgreSQL (Supabase). Las tablas nuevas, cerradas a la API REST
-- igual que las demás (ver 002_rls.pg.sql). `server_keys` guarda la clave
-- privada de los avisos: no debe poder leerla nadie más que el servidor.

ALTER TABLE daily ENABLE ROW LEVEL SECURITY;
ALTER TABLE push_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE notify_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE server_keys ENABLE ROW LEVEL SECURITY;
