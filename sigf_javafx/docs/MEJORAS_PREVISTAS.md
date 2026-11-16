# Mejoras acordadas: estado

- **Implementado y probado:** recuperación de ventas y compras con solicitudes persistentes, claves de reintento, cancelación segura y sincronización del carrito; instantáneas de comprobantes nuevos; preservación de documentos anteriores con identificación de datos históricos incompletos.
- **Implementado:** organización en paquetes, servicios por áreas, repositorios para reportes, comprobantes, auditoría y movimientos; carrito editable, selectores, panel de alertas y respaldo desde Configuración. El SQL de algunas transacciones sigue en servicios y aún puede extraerse.
- **Implementado como demostración:** serie interna, RUC de cliente, registros de anulación y reportes CSV de IVA y comprobantes internos. Estos documentos no son facturas fiscales autorizadas ni archivos oficiales de declaración.
- **Pendiente de validación externa:** requisitos, formatos, régimen y autorizaciones de la DGI; revisión independiente de seguridad, aislamiento efectivo de credenciales PostgreSQL, impresión física y pruebas integrales con una farmacia real.
- **Pendiente de mejorar:** selección asistida y validaciones en todos los formularios, separación completa de persistencia y pruebas exhaustivas de interfaz.
