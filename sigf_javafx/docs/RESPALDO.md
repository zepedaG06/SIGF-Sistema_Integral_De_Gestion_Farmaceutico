# Respaldo y restauración de prueba

Use PostgreSQL `pg_dump` de versión compatible y credenciales con privilegios de lectura sobre la base exclusiva `sigf_javafx_db`. Los respaldos contienen información sensible y no están cifrados por los scripts: restrinja los permisos del directorio, proteja la copia y establezca una política de retención y recuperación. `backups/` se excluye de Git. No almacene contraseñas en el repositorio; configure la autenticación PostgreSQL de forma segura para la sesión.

Ejemplo desde PowerShell, con PostgreSQL instalado y autenticación ya configurada:

```powershell
.\scripts\respaldo.ps1 -DbUser '<usuario>' -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'
```

Para comprobar un respaldo, un administrador debe crear previamente una base **vacía y desechable** `sigf_javafx_restore_test` en un servidor aislado. El siguiente comando rechaza una base de prueba que ya contiene objetos y restaura en una transacción; no sobrescribe la base de producción:

```powershell
.\scripts\verificar-respaldo.ps1 -DbUser '<usuario>' -PostgresBin 'C:\Program Files\PostgreSQL\18\bin' -BackupFile 'backups\<archivo>.dump'
```

Se verificó un respaldo y restauración con datos de prueba en un clúster aislado. Una recuperación real debe ser planificada y ejecutada por quien administre PostgreSQL; verifique integridad, versión, permisos, tiempos de recuperación y almacenamiento externo antes de operar.

También puede crear un respaldo desde Configuración → Crear respaldo, seleccionando `pg_dump` o `pg_dump.exe` y un archivo nuevo. Para comprobar su restauración use el procedimiento de base desechable descrito arriba.
