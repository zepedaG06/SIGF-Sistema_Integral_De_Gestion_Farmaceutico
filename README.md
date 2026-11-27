<div align="center">

# 💊 SIGF — Sistema Integral de Gestión Farmacéutica

**Aplicación de escritorio para administrar una farmacia: inventario por lotes con vencimientos, ventas, compras, recetas, usuarios y reportes.**

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-FXML-0F9D76)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-JDBC-4169E1?logo=postgresql&logoColor=white)

</div>

---

## 📌 Sobre el proyecto

Roles disponibles: Administrador, Farmacéutico, Cajero y Contador. Se asignan desde Administración → Usuarios. La matriz de permisos describe las operaciones y las consultas de cada perfil.

Use mvn spotless:apply para mantener el formato y mvn verify para compilar, probar y comprobarlo.

La revisión del 25/11/2026 refuerza los reintentos de compras y ventas, la lectura coherente de documentos, los escaneos ambiguos, la ordenación de tablas y los cambios de sesión y configuración. Los scripts de empaquetado compilan con dependencias aisladas; los de restablecimiento exigen confirmación y administrador. Consulte las pruebas ejecutadas y los límites en PRUEBAS.md. GitHub Actions verifica aplicaciones nativas Windows/macOS/Linux e integraciones PostgreSQL por separado.

Aplicación de escritorio independiente en Java 21, JavaFX y PostgreSQL, organizada por capas y módulos. Incluye autenticación y roles, productos, lotes, compras con varias líneas, proveedor y recuperación ante reintentos, ventas con carrito, clientes, proveedores, reportes administrativos PDF, XLSX, DOCX y CSV, respaldos, usuarios, configuración y auditoría. El IVA se configura por producto y los precios se registran sin IVA. Las operaciones de compra, venta, anulación y ajuste usan transacciones PostgreSQL; las solicitudes de venta pendientes se guardan antes de ejecutarse para poder recuperarlas tras reiniciar.

El proyecto se ha probado con datos aislados. Antes de usarlo para operaciones farmacéuticas reales faltan validación integral con los procesos de la farmacia, revisión de seguridad independiente, recuperación operativa, requisitos fiscales y pruebas de impresión física. No contiene datos ni credenciales de producción.

Consulte instalación, arquitectura, base de datos, manual y pruebas, respaldo y preparación fiscal para Nicaragua.

El rediseño incluye espacios propios para Inicio, Ventas, Inventario, Compras, Reportes y Configuración, conservando Contactos y Usuarios. Todos los importes operativos se muestran en C$. Para ejecutar el código actualizado use org.sigf.app.Launcher o mvn javafx:run. Los cambios y flujos están detallados en manual.

Los reportes incluyen General, filtros por contexto y exportación JasperReports a PDF, XLSX, DOCX y CSV. Los formatos gráficos utilizan el logo y los datos del setup; los pagos por transferencia guardan banco, referencia y descripción y se recuperan con la solicitud. V15 agrega estas estructuras sin borrar datos existentes. Los detalles de ventas, compras y movimientos incluyen información del documento y sus responsables. Consulte el manual y las pruebas para los permisos y límites.

## 👨‍💻 Equipo

| Integrante | |
|---|---|
| _[Jose Zepeda]_ | Base de datos, login y roles, usuarios, auditoría |
| _[Cesar Saavedra]_ | Catálogo, inventario, compras, alertas |
| _[Marlon Aguilar]_ | Ventas, recetas, dashboard, reportes |

---

<div align="center">
Proyecto universitario · 2026
</div>
