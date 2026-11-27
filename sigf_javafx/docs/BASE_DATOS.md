# Base de datos

V14 amplía la restricción `app_user_role_check` para admitir `PHARMACIST` y `ACCOUNTANT`,
conservando `ADMIN`, `CASHIER`, las cuentas y sus datos. No crea usuarios PostgreSQL ni
modifica las migraciones anteriores. Los cambios de rol realizados desde la aplicación
renuevan `session_version`, invalidando la sesión previa. La auditoría de bloqueos registra
el motivo; la edición farmacéutica registra `PHARMACEUTICAL_INFO_CHANGE`.

Flyway aplica migraciones versionadas a `sigf_javafx_db`. El modelo incluye usuarios, catálogo, lotes, compras, ventas, pagos, inventario, perfil de farmacia y auditoría. V7 añade solicitudes recuperables de compra e instantáneas de comprobantes; V8 añade una serie **interna de demostración**, RUC de cliente y datos de anulación; V9 conserva los precios e impuestos cotizados de ventas pendientes.

Los documentos previos a V7 no tienen una instantánea histórica completa ni serie interna. La aplicación los identifica como tales; no se inventan datos históricos. Para pendientes anteriores a V9 sin una venta confirmada, se requiere cancelar la solicitud y volver a cotizar; una venta ya confirmada sigue siendo recuperable. Las claves únicas, restricciones y transacciones protegen las operaciones nuevas.

La migración V10 crea `product_catalog` para categorías y laboratorios, conserva las denominaciones existentes y añade las nuevas al guardar productos en la misma transacción. Flyway la aplica al conectar; el usuario de base de datos necesita permisos de migración.


V11 refuerza las sesiones y la integridad entre productos, lotes y detalles. V12 añade efectivo recibido a la solicitud y recibido/cambio al pago, con restricción de igualdad respecto al importe. No se inventan importes recibidos para pagos anteriores. V13 crea `purchase_draft` y `purchase_draft_line`: un borrador por administrador, líneas ordenadas y clave de recuperación opcional. Guardarlo no crea compras ni movimientos; registrar recepción sigue pasando por la solicitud idempotente y la transacción comercial. El restablecimiento incluye estas tablas.

## V15 — Reportes y transferencias

`bank` contiene el catálogo administrable. `sale_request` y `payment` incorporan `bank_name`, `transfer_reference` y `transfer_description`; claves foráneas, longitudes y restricciones impiden combinar esos datos con otro medio de pago. El guardado de nuevas transferencias exige banco activo y referencia; la recuperación conserva una solicitud ya aceptada aunque el banco se haya desactivado después. El pago se completa dentro de la misma transacción que venta y existencias. Un índice ayuda a detectar referencias repetidas.

`purchase_snapshot` conserva proveedor y responsable de compras nuevas. `sale_receipt_snapshot.seller_name`, `sale_line.presentation_snapshot` y las instantáneas de nombre/presentación de `purchase_line` conservan información de nuevas operaciones. No se reconstruyen datos históricos inexistentes. El restablecimiento autorizado borra también las instantáneas y los bancos personalizados y repone seis bancos iniciales, conservando las migraciones.
