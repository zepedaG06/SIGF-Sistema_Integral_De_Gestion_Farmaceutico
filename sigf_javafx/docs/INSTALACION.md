# Instalación y ejecución

La actualización con roles profesionales incorpora V14. Flyway la aplica al conectar,
conservando las cuentas existentes. Después, un administrador puede asignar Farmacéutico
o Contador desde Administración → Usuarios. No hace falta restablecer la base ni repetir
el setup. Mantenga un respaldo verificado antes de actualizar, como en las demás migraciones.

Requisitos: JDK 21, Maven 3.9 y PostgreSQL accesible solo desde una red confiable. Cree una base vacía exclusiva llamada `sigf_javafx_db` y un usuario PostgreSQL propio con permisos para crear objetos en ella. No utilice una base de otro proyecto. Los comandos siguientes suponen que un administrador de PostgreSQL ya creó esa base y el usuario.

En PowerShell, desde la carpeta del proyecto, establezca variables de entorno de la sesión (sustituya los valores; no los guarde en el repositorio):

```powershell
$env:SIGF_DB_URL='jdbc:postgresql://localhost:5432/sigf_javafx_db'
$env:SIGF_DB_USER='<usuario exclusivo>'
$env:SIGF_DB_PASSWORD='<contraseña de PostgreSQL>'
$env:SIGF_BOOTSTRAP='true'
$env:SIGF_ADMIN_USER='<nombre de administrador>'
$env:SIGF_ADMIN_PASSWORD='<contraseña de al menos 12 caracteres>'
mvn javafx:run
Remove-Item Env:SIGF_BOOTSTRAP, Env:SIGF_ADMIN_USER, Env:SIGF_ADMIN_PASSWORD
mvn javafx:run
```

El primer arranque aplica migraciones y crea el primer administrador únicamente si no hay usuarios. No hay credenciales predeterminadas. Mantenga protegida la sesión y sus variables de entorno. Si falla el inicio, compruebe la conexión y los permisos sin revelar contraseñas. La imagen Windows de `dist/v031/SIGF` incluye el runtime. También se generó un instalador EXE 0.3.1 con WiX 3.14.1; su instalación y desinstalación no se han probado. Para regenerarlo, use `scripts/instalador.ps1 -WixDirectory <ruta-a-WiX-3>`; requiere JDK, Maven y WiX 3.

Al actualizar desde una versión anterior, clasifique la tasa de IVA de cada producto antes de vender: la migración no presupone su tratamiento. El precio introducido en la aplicación se considera sin IVA. Revise [NICARAGUA.md](NICARAGUA.md) y [RESPALDO.md](RESPALDO.md) antes de cualquier despliegue.

## Inicio desde la aplicación

Abra `dist/v031/SIGF/SIGF.exe`. En la pantalla de conexión use servidor `127.0.0.1`, puerto `5432`, usuario PostgreSQL `sigf_app` y la contraseña que se haya configurado para ese usuario. Tras conectarse, el servidor, puerto y usuario se guardan en las preferencias del usuario y la contraseña en el almacén protegido del sistema; los siguientes inicios intentan conectarse automáticamente. Si falla, vuelva a introducir los datos. La base debe llamarse `sigf_javafx_db`.

Si es la primera conexión y no existen usuarios de SIGF, se solicita crear un administrador de la aplicación con una contraseña de al menos 12 caracteres. No es el usuario PostgreSQL. Después puede iniciar sesión y las operaciones quedarán registradas en la base conectada.

Tras el primer inicio de sesión del administrador aparece la configuración inicial de la farmacia. El nombre, razón social o titular, RUC, dirección y teléfono son obligatorios; correo y logo son opcionales y pueden editarse en Configuración → Datos de la farmacia. El logo PNG o JPEG (máximo 2 MB) se guarda en PostgreSQL.

El punto de entrada Java es `org.sigf.app.Launcher`; desde Maven también puede usar `mvn javafx:run`. Las migraciones se ejecutan al conectar. Antes de actualizar una base con datos, realice y verifique un respaldo.

## macOS y Linux

En cada sistema instale JDK 21, Maven y PostgreSQL compatibles con su arquitectura. Desde la carpeta del proyecto ejecute `mvn javafx:run`; Maven descarga los componentes JavaFX de la plataforma. Para crear una aplicación con runtime incluido, ejecute `sh scripts/empaquetar.sh` en el propio equipo de destino. La salida se guarda en `dist/v031-macos-<arquitectura>` o `dist/v031-linux-<arquitectura>`. `jpackage` genera aplicaciones para el sistema donde se ejecuta; el instalador de Windows no sirve en macOS o Linux. El flujo de GitHub Actions compila, empaqueta y comprueba JavaFX en cada uno de los tres sistemas; consulte el resultado del commit que descargue.

PostgreSQL se prepara por separado y debe contener una base exclusiva `sigf_javafx_db`. Para respaldos desde SIGF seleccione `pg_dump` en macOS/Linux o `pg_dump.exe` en Windows, con versión compatible con el servidor.

El empaquetador de macOS exige que la versión nativa comience en 1 o más. El script suma
uno al primer componente únicamente en el paquete Mac: Maven/JAR 0.3.1 corresponde a
paquete Mac 1.3.1, y una futura versión 1.0.0 correspondería a 2.0.0. Esto conserva el
orden de actualizaciones; no cambia la versión Maven ni el nombre del JAR.

## Conexión y restablecimiento

En Windows se utiliza DPAPI vinculado a la cuenta del usuario. En macOS se utiliza Keychain mediante `security`; en Linux se requiere `secret-tool` y un servicio Secret Service disponible y desbloqueado. Si el almacén no está disponible, SIGF avisa y funciona para esa sesión; solicitará de nuevo la contraseña al iniciar. Las variables SIGF_DB_URL, SIGF_DB_USER y SIGF_DB_PASSWORD, si están definidas, tienen prioridad sobre la conexión guardada.

Para restablecer, cierre SIGF en todos los equipos y desde el proyecto ejecute `scripts/borrar_sigf.cmd` en Windows, `powershell -File scripts/borrar_sigf.ps1` o `sh scripts/borrar_sigf.sh` en macOS/Linux. Los tres requieren JDK 21 y Maven y deben ejecutarse en una terminal interactiva. La herramienta muestra el servidor y la base exclusiva, exige escribir `BORRAR SIGF` y autenticarse con un administrador de SIGF. Borra en una transacción los datos de la farmacia, movimientos, documentos internos, auditoría y usuarios, conserva el esquema y recupera los valores iniciales. Si usa la conexión guardada, intenta eliminarla del almacén del usuario que ejecuta la herramienta. Si recibe las tres variables SIGF_DB_* completas, usa esa conexión sin tocar la conexión guardada. Es irreversible sin respaldo; no borra respaldos, aplicaciones instaladas ni credenciales almacenadas por otros usuarios o equipos. Al iniciar se vuelve a crear el administrador y el perfil de farmacia. Los antiguos scripts `borrar_datos_sin_preguntar.cmd/.sh` delegan en este mismo flujo protegido; el antiguo SQL rechaza su ejecución.

## Construcción reproducible y comprobación sin base de datos

El empaquetado usa un directorio de compilación y dependencias nuevo en cada ejecución. Rechaza destinos fuera del proyecto y una imagen ya existente; para conservar una versión anterior, elija un destino nuevo:

```powershell
.\scripts\empaquetar.ps1 -Destination dist/mi-version-windows
.\scripts\instalador.ps1 -WixDirectory '<ruta-a-WiX-3>' -Destination dist/mi-instalador
```

```sh
sh scripts/empaquetar.sh --destination dist/mi-version
```

Para comprobar el runtime, FXML, controles de acceso y CSS sin conectarse a PostgreSQL, ejecute el binario nativo con `--self-check --self-check-output=<ruta-absoluta-del-informe>`. El proceso debe terminar con código 0 y escribir `SIGF_OK`. Esta comprobación no sustituye las pruebas de operaciones con base de datos. Las contraseñas nuevas de SIGF requieren al menos 12 caracteres y como máximo 72 bytes UTF-8, límite de BCrypt.


## Actualización del rediseño del 19/11/2026

La imagen Windows existente de `dist/v031/SIGF` contiene el JAR actualizado del rediseño. Su resumen y SHA-256 están en `dist/v031/build-info.json`; se comprobó el arranque nativo y la apertura de conexiones al PostgreSQL aislado de pruebas. El JAR anterior se conservó en `versiones_archivadas/previo-rediseno-*`. No se reemplazaron el runtime ni las dependencias, que no cambiaron.

Cierre cualquier sesión anterior antes de abrir `dist/v031/SIGF/SIGF.exe`. En el IDE ejecute `org.sigf.app.Launcher` tras recargar Maven; `mvn javafx:run` compila el código vigente. Si usa una copia instalada o un acceso directo a otra carpeta, seguirá abriendo su propia copia hasta actualizarla.


En macOS utilice JDK 21 de la misma arquitectura que el equipo (Apple Silicon/ARM64 o Intel/x64). Ejecute `mvn javafx:run` para iniciar y `sh scripts/empaquetar.sh` en el propio Mac para generar la imagen con runtime. En Windows use JDK 21 x64 y Launcher o la distribución local. El JAR por sí solo no incluye JavaFX, dependencias ni PostgreSQL. La compilación cruzada comprobada con `-Djavafx.platform=mac` y `mac-aarch64` no genera un instalador Mac desde Windows.

## Actualización de reportes y transferencias — V15

Realice un respaldo antes de actualizar una base con datos. Flyway aplica V15 al conectar; no se necesita repetir el setup. La distribución debe regenerarse completa con `empaquetar.ps1` o `empaquetar.sh`, ya que JasperReports y sus fuentes/exportadores añaden dependencias: copiar únicamente el JAR a una imagen antigua no es suficiente. El modo `--self-check --self-check-output=<ruta absoluta>` comprueba ahora también PDF, XLSX y DOCX dentro del runtime nativo, sin conectarse a PostgreSQL.
