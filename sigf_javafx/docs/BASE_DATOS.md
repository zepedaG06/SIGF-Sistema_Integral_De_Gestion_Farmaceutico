# Base de datos

Flyway aplica migraciones versionadas a `sigf_javafx_db`. El modelo incluye usuarios, catálogo, lotes, compras, ventas, pagos, inventario, perfil de farmacia y auditoría. V7 añade solicitudes recuperables de compra e instantáneas de comprobantes; V8 añade una serie **interna de demostración**, RUC de cliente y datos de anulación; V9 conserva los precios e impuestos cotizados de ventas pendientes.

Los documentos previos a V7 no tienen una instantánea histórica completa ni serie interna. La aplicación los identifica como tales; no se inventan datos históricos. Para pendientes anteriores a V9 sin una venta confirmada, se requiere cancelar la solicitud y volver a cotizar; una venta ya confirmada sigue siendo recuperable. Las claves únicas, restricciones y transacciones protegen las operaciones nuevas.

La migración V10 crea `product_catalog` para categorías y laboratorios, conserva las denominaciones existentes y añade las nuevas al guardar productos en la misma transacción. Flyway la aplica al conectar; el usuario de base de datos necesita permisos de migración.
