# Pruebas y límites

Se ejecutaron pruebas unitarias y de integración con PostgreSQL 18.4 aislado. Cubren migraciones, inventario, ventas concurrentes, recuperación de ventas y compras, reintentos con la misma clave, cancelación, comprobantes históricos, perfil, IVA mixto, series internas, anulación, permisos y generación de respaldo. Una prueba de actualización desde V3 conserva ventas anteriores y no inventa instantáneas históricas. También se probó la navegación JavaFX y se inspeccionaron el inicio y el punto de venta.

Las pruebas de integración usan una base desechable y se habilitan con `SIGF_INTEGRATION_TEST=true`; nunca ejecutarlas contra datos reales. Las pruebas de interfaz y actualización se habilitan por separado con las variables de sus clases de prueba.

Al preparar la versión V9, la base local `sigf_javafx_db` aparecía vacía antes de aplicar migraciones; se guardó un respaldo de ese estado y se aplicaron nueve migraciones. No se conocen las causas del estado previo.

Pendientes: pruebas exhaustivas de interfaz y recuperación en red, impresión física, verificación independiente de seguridad y aislamiento de credenciales compartidas. La versión de Flyway advierte que PostgreSQL 18 supera sus versiones verificadas. Las series y reportes son internos, no comprobantes ni declaraciones oficiales de la DGI.

## Rediseño de navegación

Se compiló el proyecto y se ejecutaron las pruebas unitarias. Con un clúster PostgreSQL desechable en el puerto 55439 se ejecutaron la prueba de navegación JavaFX por las siete secciones y la prueba de integración de compras y ventas. No se verificaron de extremo a extremo mediante interfaz todos los formularios ni el diálogo nuevo de cobro.

## Catálogo y flujos de interfaz

Se ejecutaron pruebas JavaFX de controles de formulario y una compra y venta con pago por tarjeta, comprobando pagos y existencias en PostgreSQL aislado. Se probó también la migración desde V3 hasta V10 y la conservación de categorías y laboratorios históricos. Estas pruebas no cubren todos los formularios, resoluciones ni escenarios de red.

## Conexión segura y restablecimiento

En Windows se verificaron el almacenamiento DPAPI, la conexión automática JavaFX y el restablecimiento de una base aislada, incluida la ejecución interactiva del programa desde Maven. El borrado se probó con el puerto 55439 de un clúster de pruebas. Los adaptadores Keychain y Secret Service y los scripts en macOS/Linux no se ejecutaron en este entorno.

## Correcciones de coherencia (V11)

Ejecutadas contra un clúster aislado en 55439: integración de ventas/compras y recuperación por administrador de solicitudes de una cuenta desactivada; rechazo de moneda USD; restablecimiento e invalidación de la sesión anterior; compra y cobro JavaFX. Ejecutadas también pruebas del almacén DPAPI y de formularios, incluida conservación de valores después de un fallo SQL simulado. No se ejecutaron pruebas en macOS/Linux ni una prueba de carga; la adquisición uniforme de bloqueos se revisó en código.

La migración añade restricciones NOT VALID: se aplican a nuevas escrituras, pero los registros históricos necesitan conciliación antes de validar completamente las restricciones. El reporte administrativo Conciliación de datos permite detectar diferencias entre movimientos/existencias y pagos/totales.
