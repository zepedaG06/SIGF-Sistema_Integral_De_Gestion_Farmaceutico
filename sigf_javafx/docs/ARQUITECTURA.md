# Arquitectura

El código está organizado en `org.sigf.app` (inicio), `ui` (JavaFX), `domain` (modelos de carrito, compra, cotización y reportes), `services` (casos de uso), `repositories` (consultas de reportes, comprobantes, auditoría y movimientos), `security` y `config`. Las vistas FXML, CSS y migraciones siguen en `src/main/resources`. `Operations` actúa como fachada de compatibilidad; ventas, compras, contactos, administración e inventario tienen servicios separados. Parte del SQL transaccional todavía reside en servicios y puede extraerse más adelante.

Ventas y compras guardan una solicitud antes de ejecutar la transacción. Las claves y bloqueos permiten recuperar reintentos sin repetir movimientos. La cotización conserva precio e IVA por producto, y la confirmación los vuelve a validar. Los comprobantes nuevos guardan una copia de los datos comerciales, cliente y productos; los anteriores a V7 se identifican como históricos sin instantánea completa.

La conexión JDBC utiliza una cuenta PostgreSQL compartida. Los roles de SIGF controlan las operaciones en la aplicación, pero quien tenga acceso a esa credencial puede actuar directamente en la base; ese límite requiere rediseño y revisión independiente para un despliegue con usuarios no confiables.


El rediseño separa `SalesPane`, `InventoryPane`, `PurchasePane`, `ReportsPane` y `SettingsPane`. `Workspace` coordina las siete secciones, carga las vistas cuando se abren y conserva sus estados. `OperationalPane` queda limitado a historiales, contactos, usuarios, lotes y pendientes; ya no contiene los flujos duplicados de carrito, compras, reportes y configuración. `Views`, `EntityPicker`, `ProductEditor` y `DocumentPreview` reúnen presentación compartida. No hay JDBC ni SQL en la capa `ui`.

`DesktopData` ofrece consultas tipadas de productos y opciones, y lecturas para detalles. `PurchaseDraftService` persiste borradores por usuario; no modifica existencias. `ConnectionSettingsService` valida la conexión y delega su protección al almacén del sistema. `ReportRepository` aplica búsqueda, fechas, páginas y resúmenes en el servidor. Las listas de historiales siguen utilizando `ReportGrid`; no se afirma que todo el modelo se haya convertido a entidades tipadas.
