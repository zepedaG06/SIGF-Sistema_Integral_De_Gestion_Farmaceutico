DO $$
BEGIN
  IF current_database() <> 'sigf_javafx_db' OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet THEN
    RAISE EXCEPTION 'Destino inválido: se requiere la base local sigf_javafx_db';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_stat_activity
             WHERE datname = current_database() AND pid <> pg_backend_pid()) THEN
    RAISE EXCEPTION 'Cierre SIGF y las demás conexiones a esta base antes de continuar';
  END IF;
END $$;
DROP SCHEMA public CASCADE;
CREATE SCHEMA public AUTHORIZATION pg_database_owner;
