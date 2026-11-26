-- El borrado directo se sustituye por ResetSigf, con confirmacion y administrador.
DO $$
BEGIN
    RAISE EXCEPTION 'Use scripts/borrar_sigf.cmd, .ps1 o .sh en una terminal interactiva. No se modificaron datos.';
END $$;
