# SIGF JavaFX

Use `mvn spotless:apply` para mantener el formato y `mvn verify` para compilar, probar y comprobarlo.

Aplicación de escritorio independiente en Java 21, JavaFX y PostgreSQL, organizada por capas y módulos. Incluye autenticación y roles, productos, lotes, compras con varias líneas, proveedor y recuperación ante reintentos, ventas con carrito, clientes, proveedores, reportes CSV internos, respaldos, usuarios, configuración y auditoría. El IVA se configura por producto y los precios se registran sin IVA. Las operaciones de compra, venta, anulación y ajuste usan transacciones PostgreSQL; las solicitudes de venta pendientes se guardan antes de ejecutarse para poder recuperarlas tras reiniciar.

El proyecto se ha probado con datos aislados. **Antes de usarlo para operaciones farmacéuticas reales** faltan validación integral con los procesos de la farmacia, revisión de seguridad independiente, recuperación operativa, requisitos fiscales y pruebas de impresión física. No contiene datos ni credenciales de producción.

Consulte [instalación](docs/INSTALACION.md), [arquitectura](docs/ARQUITECTURA.md), [base de datos](docs/BASE_DATOS.md), [manual](docs/MANUAL_USUARIO.md) y [pruebas](docs/PRUEBAS.md), [respaldo](docs/RESPALDO.md) y [preparación fiscal para Nicaragua](docs/NICARAGUA.md).


El rediseño incluye espacios propios para Inicio, Ventas, Inventario, Compras, Reportes y Configuración, conservando Contactos y Usuarios. Todos los importes operativos se muestran en C$. Para ejecutar el código actualizado use `org.sigf.app.Launcher` o `mvn javafx:run`. Los cambios y flujos están detallados en [manual](docs/MANUAL_USUARIO.md).
