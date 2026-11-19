# Inicio rápido de SIGF

## 1. Preparar PostgreSQL

Encienda PostgreSQL y compruebe que exista la base exclusiva `sigf_javafx_db`. En este equipo la conexión comprobada utiliza servidor `127.0.0.1`, puerto `5432` y usuario PostgreSQL `sigf_app`. Use la contraseña configurada para ese usuario; no la escriba en el proyecto.

## 2. Abrir SIGF

En Windows abra `dist/v031/SIGF/SIGF.exe`. En la primera conexión indique el servidor, puerto, usuario y contraseña de PostgreSQL y pulse **Conectar**. SIGF guarda la conexión en el almacén seguro del usuario e intenta conectarse automáticamente las siguientes veces. La aplicación aplica las migraciones cuando corresponda. Las distribuciones existentes pueden estar desactualizadas: para usar estos cambios ejecute Launcher desde el código actual o regenere la distribución.

Si todavía no hay usuarios de SIGF, se mostrará **Crear administrador**. Defina un nombre de usuario y una contraseña de al menos 12 caracteres. Es una cuenta distinta de la de PostgreSQL. Después inicie sesión y configure los datos de la farmacia.

Si la pantalla muestra **Bienvenido**, ya existe al menos un usuario de SIGF: ingrese sus credenciales de SIGF. No elimine usuarios ni borre la base para reiniciar la configuración. Si no recuerda la contraseña, consulte al administrador.

## 3. Desde IntelliJ o Maven

Abra `pom.xml` como proyecto Maven, use JDK 21 y recargue las dependencias. Ejecute `org.sigf.app.Launcher` o, desde la terminal en la carpeta del proyecto:

```text
mvn javafx:run
```

`mvn test` compila y ejecuta las pruebas unitarias predeterminadas; crea una carpeta de salida `target-v031`. Las pruebas de integración requieren una base de pruebas desechable y no deben ejecutarse sobre la base de la farmacia.

## 4. Uso básico

Registre productos y clasifique su IVA. Reciba lotes desde Productos o Compras y realice ventas desde Punto de venta. Para una operación incierta consulte **Pendientes** o **Pend. compras**. Los comprobantes actuales son internos y no constituyen facturas oficiales.

Si la conexión falla, compruebe que PostgreSQL esté encendido, que la base exista y que `sigf_app` tenga permiso de inicio de sesión y la contraseña correcta. Más detalles, incluido macOS y Linux, en `docs/INSTALACION.md`.
