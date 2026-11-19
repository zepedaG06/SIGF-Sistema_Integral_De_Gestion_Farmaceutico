# Mejoras acordadas: estado

- **Implementado y probado:** recuperación de ventas y compras con solicitudes persistentes, claves de reintento, cancelación segura y sincronización del carrito; instantáneas de comprobantes nuevos; preservación de documentos anteriores con identificación de datos históricos incompletos.
- **Implementado:** organización en paquetes, servicios por áreas, repositorios para reportes, comprobantes, auditoría y movimientos; carrito editable, selectores, panel de alertas y respaldo desde Configuración. El SQL de algunas transacciones sigue en servicios y aún puede extraerse.
- **Implementado como demostración:** serie interna, RUC de cliente, registros de anulación y reportes CSV de IVA y comprobantes internos. Estos documentos no son facturas fiscales autorizadas ni archivos oficiales de declaración.
- **Pendiente de validación externa:** requisitos, formatos, régimen y autorizaciones de la DGI; revisión independiente de seguridad, aislamiento efectivo de credenciales PostgreSQL, impresión física y pruebas integrales con una farmacia real.
- **Pendiente de mejorar:** selección asistida y validaciones en todos los formularios, separación completa de persistencia y pruebas exhaustivas de interfaz.

## Rediseño de navegación y formularios

- Implementado: siete secciones agrupadas, accesos a los módulos existentes y permisos previos; inicio simplificado con nueva venta y acceso desde tarjetas de alertas.
- Implementado: estado de productos y proveedores con casilla, roles con lista, IVA con selección explícita, fechas con calendario, cantidades con controles numéricos y presentación con opciones editables. Categoría y laboratorio permiten escribir valores propios.
- Implementado: búsqueda en selectores de productos, clientes y proveedores en los flujos que utilizan el selector compartido; confirmación de venta con cliente y pago en una pantalla. El carrito permite editar cantidades y quitar filas; los importes principales se muestran en C$.
- Implementado: catálogo persistente con sugerencias para categorías y laboratorios; la migración recupera los valores de productos existentes. Los valores nuevos se guardan al guardar el producto.
- Implementado: borrador visible de compra con retiro por línea y menú de acciones para el historial de ventas.
- Verificado: controles de formulario y flujos de compra y cobro a través de JavaFX contra PostgreSQL aislado. No se han probado todas las combinaciones ni todos los tamaños de pantalla.
- Las series y comprobantes siguen siendo internos: el rediseño no constituye autorización de facturación fiscal.

## Correcciones V11

Aplicadas: identidad de sesión no reutilizable, invalidación tras cambios de cuenta, operación NIO, separación de productos homónimos en comprobantes, criterios de stock/vencimientos, actualización al entrar en módulos, conciliación administrativa de pendientes con auditoría, formularios de guardado asíncrono que conservan valores, bloqueo de productos antes de anular, numeración asignada después de procesar inventario, restricciones SQL adicionales, reporte de conciliación, páginas para historiales y búsqueda indexada en el carrito, configuración segura con cambio por versión.

Pendientes estructurales: separación completa de OperationalPane y reemplazo de todas las filas List<String> por modelos tipados; búsqueda en servidor para selectores grandes; pruebas de carga y de interbloqueos. No se consideran completados por estas correcciones. No se han convertido documentos históricos de otras monedas ni corregido automáticamente datos históricos inconsistentes.
