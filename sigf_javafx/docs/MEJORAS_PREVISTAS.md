# Mejoras acordadas: estado del rediseño

Implementadas en JavaFX, sin sustituir la aplicación por una web:

- Inicio con cuatro accesos, alertas con contexto, movimientos legibles y hora real de actualización.
- Punto de venta con catálogo y carrito permanentes, controles de cantidad, cliente y pago en la pantalla, efectivo/cambio persistidos y acceso al último comprobante. Conserva recuperación idempotente y evita cobrar con precios anteriores a la cotización.
- Inventario con búsqueda en servidor, filtros combinables, ficha contextual, calendario de lotes y formulario de producto agrupado con precio final. Ajustes guiados Entrada/Salida con motivo y vista del saldo.
- Compras separadas de su historial: proveedor al principio, varias líneas editables, fechas con calendario, totales, borrador guardado y recuperación tras reiniciar. Reintentos conservan la misma clave.
- Reportes con períodos rápidos, fechas pertinentes, búsqueda global, conteo y resúmenes, páginas y exportación CSV completa o visible con los filtros aplicados.
- Configuración por secciones: farmacia, operación, documentos internos, respaldo y conexión; NIO, texto grande, serie existente, herramienta pg_dump recordada y estado de conexión real.
- Navegación horizontal de siete secciones, estilos marfil/salvia/verde, acciones principales visibles, menús contextuales, controles asistidos y aviso de cambios sin guardar.
- Separación de las pantallas principales, consultas fuera del hilo gráfico y eliminación de los flujos duplicados de OperationalPane.

Los resultados comprobados están en PRUEBAS.md. Se mantienen como límites la impresión física, las pruebas macOS/Linux, carga y fallos reales de red, y la separación completa de persistencia/modelos tipados. No se convirtieron monedas o documentos históricos ni se corrigieron automáticamente datos antiguos inconsistentes. Los comprobantes y series continúan siendo internos; el rediseño no añade autorización fiscal ni envío de declaraciones a la DGI.
