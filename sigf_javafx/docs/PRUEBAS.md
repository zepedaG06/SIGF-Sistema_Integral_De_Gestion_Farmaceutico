# Pruebas y límites

Se ejecutaron pruebas unitarias y de integración con PostgreSQL 18.4 aislado. Cubren migraciones, inventario, ventas concurrentes, recuperación de ventas y compras, reintentos con la misma clave, cancelación, comprobantes históricos, perfil, IVA mixto, series internas, anulación, permisos y generación de respaldo. Una prueba de actualización desde V3 conserva ventas anteriores y no inventa instantáneas históricas. También se probó la navegación JavaFX y se inspeccionaron el inicio y el punto de venta.

Las pruebas de integración usan una base desechable y se habilitan con `SIGF_INTEGRATION_TEST=true`; nunca ejecutarlas contra datos reales. Las pruebas de interfaz y actualización se habilitan por separado con las variables de sus clases de prueba.

Al preparar la versión V9, la base local `sigf_javafx_db` aparecía vacía antes de aplicar migraciones; se guardó un respaldo de ese estado y se aplicaron nueve migraciones. No se conocen las causas del estado previo.

Pendientes: pruebas exhaustivas de interfaz y recuperación en red, impresión física, verificación independiente de seguridad y aislamiento de credenciales compartidas. La versión de Flyway advierte que PostgreSQL 18 supera sus versiones verificadas. Las series y reportes son internos, no comprobantes ni declaraciones oficiales de la DGI.
