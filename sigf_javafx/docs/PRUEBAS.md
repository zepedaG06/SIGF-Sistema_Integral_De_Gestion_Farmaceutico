# Pruebas y límites

## Reportes JasperReports y transferencias — 26/11/2026

Ejecutadas en Windows con Java 21 y PostgreSQL 18.4 exclusivo en 55439:

- `ReportExporterTest`: apertura de PDF de varias páginas, logo y datos comerciales, texto en español y totales; apertura de XLSX con importes numéricos y logo; estructura DOCX con contenido y logo. Comprueba metadatos largos sin recorte y protección del texto frente a fórmulas de hoja de cálculo.
- `ReportingIntegrationTest`, habilitada con `SIGF_REPORTING_TEST=true`: bancos y restricciones, solicitud/pago de transferencia persistentes, recuperación idempotente y cambio de datos rechazado; filtros combinados y sumas; instantáneas de contactos; cajero limitado a ventas propias y denegaciones por rol; exportaciones reales del contador y General operativo del farmacéutico. Crea un cobro de transferencia mediante los controles JavaFX y verifica banco, referencia y descripción en PostgreSQL. Comprueba restablecimiento V15 y seis bancos iniciales.
- Se inspeccionaron el PDF y capturas JavaFX del reporte General y del punto de venta. Son datos de prueba, no información de la farmacia.

La prueba nueva usa una base vacía desechable y ejecuta un restablecimiento al finalizar; nunca debe habilitarse sobre datos operativos. El flujo remoto incluye nueve integraciones PostgreSQL independientes y compilación, empaquetado y comprobación de JavaFX y JasperReports en Windows/macOS/Linux. Consulte el resultado del commit en GitHub Actions para conocer su estado; las pruebas especiales se omiten sin sus variables.

## Revisión general — 25/11/2026

Esta sección corresponde a la revisión del 25/11/2026; las secciones posteriores conservan
el historial de verificaciones anteriores. No equivalen a una certificación fiscal o de producción.

Correcciones incluidas:

- Un UUID de compra o venta no puede reutilizarse con proveedor, cliente, pago o líneas
  distintos. También se coteja el intento guardado antes de ejecutar una solicitud pendiente.
  Se normalizan los lotes y se rechazan líneas inválidas antes de persistir.
- El escaneo busca coincidencias exactas fuera de la página visible y requiere selección
  manual cuando código y código de barras identifican productos distintos.
- Las tablas ordenan cantidades e importes como números y fechas por su valor ISO;
  la exportación de la página respeta el orden visible.
- La edición de la cuenta propia termina su sesión. La configuración impide guardados
  simultáneos y valida el rango de alertas en el servicio. Nombre de farmacia y perfil
  se actualizan juntos; los borradores y comprobantes se leen en una instantánea coherente.
- Las contraseñas nuevas respetan el límite de 72 bytes UTF-8 de BCrypt.
- En macOS, el guardado en Keychain termina al cerrar la entrada del proceso `security`;
  se eliminó la orden `quit`, que no existe y hacía fallar el guardado aunque se hubiera
  insertado el secreto. El secreto usa un sobre ASCII versionado dentro de Keychain,
  porque `security -w` convierte contraseñas no ASCII a hexadecimal. Se conserva UTF-8
  y se distingue una contraseña literal como `deadbeef` de una representación hexadecimal.
  La prueba del almacén se habilitó también para macOS e incluye espacios, Unicode y comillas.
- El empaquetado usa una compilación limpia e independiente por ejecución; los scripts
  antiguos de borrado pasan por confirmación y autenticación. Los respaldos no sobrescriben
  copias y la restauración rechaza objetos en cualquier esquema de usuario.

Ejecutado localmente en Windows, Java 21 y PostgreSQL 18.4 aislado en 55439:

| Prueba | Resultado y alcance |
| --- | --- |
| ReviewRegressionTest | Correcta: reintentos con contenido distinto, solicitudes pendientes, lotes normalizados, IVA repartido entre lotes, escaneo ambiguo, alertas y cierre de sesión tras editar la propia cuenta |
| DesktopDataIntegrationTest, QuickActionsGuiTest, CheckoutGuiTest | Correctas: búsquedas, flujos de compra/venta, borradores, pagos, existencias y accesos rápidos |
| PostgresIntegrationTest | Correcta: transacciones, concurrencia, recuperación, permisos, instantáneas y respaldo |
| FirstRunTest, StartupFlowTest, AutoConnectGuiTest | Correctas: primer administrador, setup y conexión guardada con preferencias de pruebas aisladas |
| JavaFxSmokeTest, ProfileDialogGuiTest, FormControlsGuiTest, TableLayoutGuiTest | Correctas: navegación, perfil, controles y columnas/separadores ajustables |
| ResetServiceTest, MigrationUpgradeTest | Correctas: borrado e invalidación de sesión conservando las 14 migraciones; actualización desde V3 |
| ValueOrderTest, PasswordPolicyTest y pruebas ordinarias existentes | Ordenación, límite de contraseña, inventario, perfil, permisos y DPAPI |

Se generaron una imagen nativa Windows y un instalador EXE mediante WiX 3.14.1.
La imagen pasó `--self-check`: carga real del runtime JavaFX, FXML, CSS y controles de
acceso, sin conectar a PostgreSQL. No se ejecutó el asistente de instalación del EXE.
Se ejecutaron respaldo y restauración con PowerShell y sh bajo Git Bash y los rechazos
de sobrescritura y de restauración sobre datos existentes. Se comprobaron sintaxis
PowerShell y sh. Los datos operativos del puerto 5432 no se utilizaron.
El alias `.cmd` de restablecimiento pasó una ejecución interactiva completa contra
55439: exigió confirmación y administrador, dejó cero usuarios/productos/ventas y
conservó las 14 migraciones, sin cambiar la conexión guardada del equipo. La verificación
Maven final ejecutó 12 pruebas ordinarias satisfactorias y omitió 15 especiales;
las integraciones anteriores se habilitaron por separado.

El flujo `.github/workflows/compilar.yml` ejecuta `mvn verify`, empaquetado y comprobación
nativa en Windows, macOS y Linux. Ocho trabajos adicionales ejecutan integraciones
PostgreSQL 17 con bases independientes y pantalla virtual; no son compilaciones cruzadas.
Consulte el resultado del commit en GitHub Actions para conocer su estado. Una ejecución
ordinaria sin variables de activación omite las pruebas especiales; no prueba esos flujos.

Para repetir `ReviewRegressionTest`, prepare una base vacía exclusiva en 55439, defina
SIGF_DB_URL, SIGF_DB_USER, SIGF_DB_PASSWORD y SIGF_REVIEW_TEST=true, y ejecute
`mvn -Dtest=ReviewRegressionTest test`. No comparta una base ya poblada entre pruebas
que crean el primer administrador. Las migraciones V1 a V14 no se modificaron.

## Roles profesionales — 25/11/2026

Ejecutadas en Windows con Java 21 y PostgreSQL 18.4 desechable, puerto 55439:

- `ProfessionalRolesIntegrationTest`: parte de V13 con cuentas ADMIN y CASHIER,
  aplica V14 y comprueba su conservación; crea PHARMACIST y ACCOUNTANT; verifica
  permisos en servicios, formularios, navegación, consultas y sesiones.
- Comprueba venta y alta de cliente por farmacéutico, bloqueo con motivo y auditoría,
  rechazo de venta con lote bloqueado, desbloqueo exclusivo de administración y edición
  descriptiva sin alterar precios, costos, IVA, mínimo ni estado.
- Comprueba que el contador consulte pagos, ventas, compras, IVA y conciliación, y que
  se rechacen venta, anulación, compra, ajuste, bloqueo, alta de contactos, configuración,
  usuarios y restablecimiento. Rechaza reportes administrativos, roles falseados y sesiones
  anteriores al cambio de cuenta. El selector de usuario se prueba creando un farmacéutico.
- `PermissionsTest`: tres pruebas de la matriz, etiquetas, rol desconocido y conservación
  de las capacidades del cajero.
- Regresiones satisfactorias: `QuickActionsGuiTest`, `FormControlsGuiTest`,
  `TableLayoutGuiTest`, `PostgresIntegrationTest`, las tres de `InventoryServiceTest`,
  `PharmacyProfileValidationTest` y `SecureConnectionStoreTest`.

En total, trece pruebas distintas satisfactorias. Para la prueba nueva prepare un clúster
vacío y desechable en 55439 con `sigf_javafx_db`, configure las tres variables de conexión
y `SIGF_ROLES_TEST=true`, y ejecute `mvn -Dtest=ProfessionalRolesIntegrationTest test`.
No comparte datos con otras pruebas que crean administrador. La verificación ordinaria
`mvn verify` ejecuta ocho pruebas y omite catorce especiales sin sus variables de activación.
No se modificó la base operativa ni se ejecutaron estas pruebas en macOS/Linux.

## Accesos rápidos — 25/11/2026

Comprobado en Windows con Java 21 y PostgreSQL aislado en `127.0.0.1:55439`:

- `QuickActionsGuiTest`: alta y selección por ID de clientes y proveedores; conservación
  del carrito, efectivo y borrador; cancelación sin insertar contactos; coincidencias por
  teléfono y RUC y selección del existente; nombres iguales permitidos; alta de producto
  desde Compras y recepción de su lote; accesos a compras desde Inventario e Inicio;
  cantidad sugerida; historial limitado al producto; permisos de cajero y administrador.
  También comprueba que Cobrar quede dentro de la ventana de 1150 × 760.
- `CheckoutGuiTest`: regresión de alta de producto, compra de varias líneas, recuperación
  del borrador, recepción, venta con efectivo/cambio, existencias, comprobante, reportes y
  configuración. Se espera a que termine el flujo de recepción antes de comprobar la
  limpieza del borrador, evitando consultar esa limpieza antes de su finalización.
- `PostgresIntegrationTest`: regresión de las operaciones transaccionales, concurrencia,
  recuperación, permisos y respaldo cubiertas por esa prueba.
- `InventoryServiceTest` (3), `PharmacyProfileValidationTest` (1) y
  `SecureConnectionStoreTest` (1): satisfactorias.

Son ocho pruebas distintas ejecutadas satisfactoriamente. Para habilitar la nueva prueba,
prepare exclusivamente un clúster vacío y desechable en el puerto 55439 con la base
`sigf_javafx_db`, configure las variables `SIGF_DB_URL`, `SIGF_DB_USER`, `SIGF_DB_PASSWORD`
y `SIGF_QUICK_ACTIONS_TEST=true`, y ejecute `mvn -Dtest=QuickActionsGuiTest test`.
Las pruebas que crean el primer administrador necesitan datos vacíos independientes.
No se usó la base operativa del puerto 5432. No se ejecutaron estas pruebas en macOS/Linux.

La verificación Maven de la copia preparada para publicar compiló y empaquetó correctamente,
comprobó el formato y ejecutó las cinco pruebas ordinarias; las trece pruebas especiales
quedan omitidas sin sus variables de activación. Los resultados de integración e interfaz
anteriores corresponden a sus ejecuciones habilitadas por separado.

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
