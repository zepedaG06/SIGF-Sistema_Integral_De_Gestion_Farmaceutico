# Pruebas y límites

## Organización del código — 24/11/2026

Se organizaron 50 fuentes Java de aplicación y 18 de pruebas: formato uniforme de cuatro
espacios, importaciones explícitas y llaves en condiciones y bucles. Se organizó también
el formato de Maven, CSS y FXML. Los paquetes y las firmas públicas se conservaron.

Validaciones ejecutadas en Windows con JDK 21:

- `mvn verify`: compilación y empaquetado correctos, comprobación Spotless correcta,
  cinco pruebas satisfactorias y doce pruebas especiales omitidas por sus condiciones de activación.
- `FormControlsGuiTest` y `TableLayoutGuiTest`, habilitadas expresamente y con procesos
  separados: dos pruebas adicionales satisfactorias.
- Carga JavaFX del FXML de acceso, su controlador y CSS; comprobación de los controles
  de usuario, contraseña y botón de ingreso, sin conectar PostgreSQL.
- Comparación del JAR anterior con el reorganizado: bytecode equivalente en las 85 clases
  generadas al excluir metadatos de depuración (líneas y variables locales).
- Verificación SHA-256 de las trece migraciones: sin cambios.

Las evidencias locales de esta ejecución están en `target-v031/organizacion/` y los
resultados JUnit en `target-v031/surefire-reports/`. No se volvió a ejecutar la integración
PostgreSQL ni se hicieron cambios en la base operativa. Para mantener el formato Java,
use `mvn spotless:apply`; para comprobarlo sin modificar archivos, `mvn spotless:check`.

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


## Rediseño funcional — 19 de noviembre de 2026

Verificado en Windows con Java 21, JavaFX 21 y PostgreSQL 18.4 en un clúster desechable local, puerto 55439. No se utilizaron datos de la farmacia ni el servidor operativo del puerto 5432. Las capturas de `capturas-rediseno/` son escenas JavaFX renderizadas con datos de prueba.

| Prueba ejecutada | Cobertura comprobada |
| --- | --- |
| InventoryServiceTest, PharmacyProfileValidationTest | Validaciones y límites administrativos antes de persistir |
| SecureConnectionStoreTest | Cifrado, recuperación y borrado DPAPI con preferencias aisladas |
| FormControlsGuiTest | Controles asistidos y conservación de datos después de un fallo de guardado |
| CheckoutGuiTest | Formulario nuevo de producto; compra de dos líneas; guardado y recuperación del borrador al recrear la sesión; recepción; escaneo Enter; cantidades −/+; cobro con Ctrl+Enter; efectivo C$20, venta C$11.50 y cambio C$8.50 guardados; descuento de stock; comprobante y su historial; reportes, configuración y diálogo de ajuste |
| DesktopDataIntegrationTest | Búsquedas y páginas de 50 sobre 55 productos; filtros; consulta exacta de producto; borrador y recepción idempotente; alertas y bloqueo; cambio y recibido; rechazo de reintento con diferente efectivo; resúmenes de confirmadas/anuladas; permisos; conciliación |
| ResetServiceTest | Restablecimiento con esquema V13 e invalidación de la sesión previa |
| MigrationUpgradeTest | Actualización de V3 a V13 conservando productos y documentos históricos |
| PostgresIntegrationTest | Regresión de ventas/compras, concurrencia, reintentos, precios/IVA, anulaciones, instantáneas, permisos y creación/inspección de respaldo |

Se inspeccionaron las seis pantallas capturadas y el punto de venta en una ventana de 1150×760. Las áreas con más controles conservan desplazamiento en ventanas pequeñas. No se realizó una prueba de todas las resoluciones, de todos los formularios administrativos, de impresora física, de macOS/Linux, de carga ni de cortes reales de red.

Para reproducir, use exclusivamente un clúster vacío y desechable en 55439 con `sigf_javafx_db`, defina las variables SIGF_DB_URL, SIGF_DB_USER y SIGF_DB_PASSWORD para ese clúster y habilite la variable de la clase. Ejecute cada prueba que crea administrador en una base vacía diferente. Ejemplos: `SIGF_DESKTOP_TEST=true` para `mvn -Dtest=DesktopDataIntegrationTest test`; `SIGF_CHECKOUT_GUI_TEST=true` para `mvn -Dtest=CheckoutGuiTest test`. La prueba de actualización usa su propia `sigf_javafx_upgrade_test`, indicada por SIGF_UPGRADE_DB_URL. Las pruebas JavaFX deben ejecutarse por separado porque inicializan su propio toolkit.

Los resultados de Maven se guardan en target-v031/surefire-reports; una ejecución sin las variables habilitadas omite las pruebas especiales y no prueba estos flujos. Los límites fiscales y de despliegue anteriores siguen vigentes.


## Iconografía — 20 de noviembre de 2026

Se incorporaron iconos SVG locales a tarjetas, encabezados, búsquedas, acciones principales, pestañas, configuración y formularios, conservando etiquetas y ayudas accesibles. No se añadieron dependencias ni cambios de base de datos. CheckoutGuiTest volvió a ejecutarse correctamente: alta de producto, recepción de dos líneas y borrador recuperable, escaneo y cobro con cambio, historiales, reportes y configuración. Las seis capturas de capturas-rediseno/ se actualizaron e inspeccionaron en JavaFX.

También pasaron InventoryServiceTest, PharmacyProfileValidationTest, SecureConnectionStoreTest, FormControlsGuiTest y ProfileDialogGuiTest. Junto con CheckoutGuiTest son ocho pruebas en esta verificación; no se volvieron a ejecutar las restantes integraciones del 19/11 para este cambio visual.


## Tablas ajustables y compilación por plataforma - 24/11/2026

Se eliminó la vinculación permanente de ancho de columnas. TableLayoutGuiTest comprueba la modificación mediante la política pública de redimensionado de TableView, la conservación al cambiar el tamaño de ventana, la reconstrucción de las columnas y la reapertura de la tabla. También verifica el cambio de posición de un separador. No es una prueba con ratón físico. CheckoutGuiTest volvió a pasar con las nuevas distribuciones; las cinco pruebas predeterminadas de validación y DPAPI también pasaron: siete pruebas en total.

Compilación y empaquetado JAR verificados con JavaFX win, mac, mac-aarch64 y linux, usando directorios de construcción independientes. Las compilaciones con clasificadores de otras plataformas se realizaron desde Windows: no prueban carga de bibliotecas nativas, Keychain, impresión ni instaladores en esos sistemas. Se preparó .github/workflows/compilar.yml con Windows, macOS y Ubuntu; el flujo remoto todavía no se ejecutó en esta entrega.
