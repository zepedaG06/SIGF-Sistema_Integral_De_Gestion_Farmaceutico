# Inicio rápido de SIGF

## 1. Preparar PostgreSQL

Encienda PostgreSQL y compruebe que exista la base exclusiva `sigf_javafx_db`. En este equipo la conexión comprobada utiliza servidor `127.0.0.1`, puerto `5432` y usuario PostgreSQL `sigf_app`. Use la contraseña configurada para ese usuario; no la escriba en el proyecto.

## 2. Abrir SIGF

En Windows abra `dist/v031/SIGF/SIGF.exe`. En la pantalla de conexión indique el servidor, puerto, usuario y contraseña de PostgreSQL y pulse **Conectar**. La aplicación aplica las migraciones cuando corresponda.

Si todavía no hay usuarios de SIGF, se mostrará **Crear administrador**. Defina un nombre de usuario y una contraseña de al menos 12 caracteres. Es una cuenta distinta de la de PostgreSQL. Después inicie sesión y configure los datos de la farmacia.

Si la pantalla muestra **Bienvenido**, ya existe al menos un usuario de SIGF: ingrese sus credenciales de SIGF. No elimine usuarios ni borre la base para reiniciar la configuración. Si no recuerda la contraseña, consulte al administrador.

## 3. Desde IntelliJ o Maven

En IntelliJ, abra el archivo `sigf_javafx/pom.xml` como proyecto Maven, use JDK 21 y recargue las dependencias. Desde la ventana Maven ejecute `Plugins → javafx → javafx:run`. También puede usar la terminal en la carpeta `sigf_javafx`; en macOS y Linux:

```bash
./mvnw verify
./mvnw javafx:run
```

En Windows use `mvnw.cmd verify` y `mvnw.cmd javafx:run`. El wrapper descarga Maven la primera vez, por lo que no hace falta instalarlo por separado. `verify` compila y ejecuta las pruebas predeterminadas; crea una carpeta de salida `target-v031`. Las pruebas de integración requieren una base de pruebas desechable y no deben ejecutarse sobre la base de la farmacia.

## 4. Uso básico

Registre productos y clasifique su IVA. Reciba lotes desde Productos o Compras y realice ventas desde Punto de venta. Para una operación incierta consulte **Pendientes** o **Pend. compras**. Los comprobantes actuales son internos y no constituyen facturas oficiales.

Si la conexión falla, compruebe que PostgreSQL esté encendido, que la base exista y que `sigf_app` tenga permiso de inicio de sesión y la contraseña correcta. Más detalles, incluido macOS y Linux, en `docs/INSTALACION.md`.

## 5. Reiniciar SIGF desde cero

Los scripts de `scripts/` borran todos los datos del esquema público de la base local `sigf_javafx_db`, incluidos los usuarios y la configuración. No se pueden recuperar sin un respaldo. Cierre SIGF y las demás conexiones a esa base; PostgreSQL debe seguir encendido. Están limitados a `127.0.0.1:5432` y al usuario PostgreSQL `sigf_app`.

Desde la carpeta `sigf_javafx`, en macOS o Linux ejecute:

```bash
./scripts/reiniciar-sigf.sh
```

En Windows, desde la misma carpeta, ejecute `scripts\reiniciar-sigf.cmd`. Ambos requieren `psql`; en Windows debe estar en el PATH. Escriba `BORRAR SIGF` para confirmar y luego la contraseña PostgreSQL de `sigf_app`. El script conserva la base y el usuario PostgreSQL. Al volver a abrir SIGF, conéctese para que se creen las tablas y configure un administrador nuevo.
