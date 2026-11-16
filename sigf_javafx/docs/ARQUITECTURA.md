# Arquitectura

El código está organizado en `org.sigf.app` (inicio), `ui` (JavaFX), `domain` (modelos de carrito, compra, cotización y reportes), `services` (casos de uso), `repositories` (consultas de reportes, comprobantes, auditoría y movimientos), `security` y `config`. Las vistas FXML, CSS y migraciones siguen en `src/main/resources`. `Operations` actúa como fachada de compatibilidad; ventas, compras, contactos, administración e inventario tienen servicios separados. Parte del SQL transaccional todavía reside en servicios y puede extraerse más adelante.

Ventas y compras guardan una solicitud antes de ejecutar la transacción. Las claves y bloqueos permiten recuperar reintentos sin repetir movimientos. La cotización conserva precio e IVA por producto, y la confirmación los vuelve a validar. Los comprobantes nuevos guardan una copia de los datos comerciales, cliente y productos; los anteriores a V7 se identifican como históricos sin instantánea completa.

La conexión JDBC utiliza una cuenta PostgreSQL compartida. Los roles de SIGF controlan las operaciones en la aplicación, pero quien tenga acceso a esa credencial puede actuar directamente en la base; ese límite requiere rediseño y revisión independiente para un despliegue con usuarios no confiables.
