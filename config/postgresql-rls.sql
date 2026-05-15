-- Row Level Security (PostgreSQL) — portal de clientes
-- Ejecutar como superusuario en app_db después de crear el rol de aplicación.
-- La app debe hacer SET LOCAL app.current_client_id = '<id>' en cada transacción autenticada.

-- Rol dedicado (no usar postgres en producción)
-- CREATE ROLE allcenter_app LOGIN PASSWORD '...';
-- GRANT CONNECT ON DATABASE app_db TO allcenter_app;
-- GRANT USAGE ON SCHEMA public TO allcenter_app;

ALTER TABLE IF EXISTS client_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS client_refresh_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS client_users_self ON client_users;
CREATE POLICY client_users_self ON client_users
    FOR ALL
    USING (id = NULLIF(current_setting('app.current_client_id', true), '')::bigint)
    WITH CHECK (id = NULLIF(current_setting('app.current_client_id', true), '')::bigint);

DROP POLICY IF EXISTS client_refresh_tokens_self ON client_refresh_tokens;
CREATE POLICY client_refresh_tokens_self ON client_refresh_tokens
    FOR ALL
    USING (client_user_id = NULLIF(current_setting('app.current_client_id', true), '')::bigint)
    WITH CHECK (client_user_id = NULLIF(current_setting('app.current_client_id', true), '')::bigint);

-- Revocar acceso amplio al rol app si existía
-- REVOKE ALL ON client_users FROM PUBLIC;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON client_users TO allcenter_app;
