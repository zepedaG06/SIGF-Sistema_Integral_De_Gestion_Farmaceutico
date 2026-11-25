# Manual de usuario

1. Abra SIGF desde `org.sigf.app.Launcher` o la aplicación de `dist/v031/SIGF`. La conexión se solicita en el primer inicio y se recuerda en el almacén seguro. Inicie sesión con su usuario de SIGF. Un administrador debe completar primero el perfil de la farmacia.
2. **Inicio:** las cuatro tarjetas abren el módulo correspondiente. Existencias bajas y lotes por vencer aplican el filtro respectivo. Seleccione una alerta y pulse Revisar alerta seleccionada para conservar el contexto. La actividad y la hora de actualización proceden de consultas a PostgreSQL.
3. **Inventario → Productos:** busque por nombre, código o barras y combine categoría, laboratorio, estado y existencias. Seleccione un producto para consultar precios, IVA, existencia física, lotes y últimos movimientos. Un administrador puede crear o editar desde el formulario agrupado; el precio ingresado es sin IVA y la clasificación es explícita. Categoría, presentación y laboratorio admiten sugerencias y valores propios.
4. **Inventario → Lotes:** filtre Disponible, Por vencer, Vencido o Bloqueado. Un administrador puede ajustar Entrada/Salida con cantidad positiva y motivo; la vista muestra antes y después. El servicio vuelve a validar el saldo dentro de la transacción. Bloquear un lote impide venderlo; habilitarlo no hace vendible un lote vencido. En Movimientos se consultan entradas, ventas, anulaciones y ajustes por período.
5. **Compras → Nueva compra:** elija un proveedor activo, busque el producto y agregue lote, vencimiento, cantidad y costo. Puede añadir varias líneas. Lote, cantidad y costo se editan en la tabla; las fechas tienen calendario. Confirme una edición con Enter o cambie el foco. Guardar borrador conserva las líneas en PostgreSQL por usuario y las recupera al volver a abrir la sesión. Registrar recepción registra compra, lotes y movimientos juntos. Los borradores todavía no afectan las existencias.
6. **Ventas → Punto de venta:** busque o escanee el código y pulse Enter para agregar una unidad; también puede usar + o doble clic. Modifique cantidades con −/+ o escriba el entero. El total usa IVA por producto, con redondeo por producto. Cliente es opcional (Consumidor final). Elija Efectivo, Tarjeta o Transferencia. En efectivo escriba Recibido o pulse Exacto: el cambio se calcula y se guarda junto con el pago. F2 enfoca la búsqueda; Ctrl+Enter inicia el cobro. El sistema vuelve a comprobar precios, clasificación y disponibilidad. Una venta correcta vacía el carrito y habilita Ver último comprobante.
7. **Pendientes:** ante un resultado de conexión incierto, reintente la misma operación o use Ventas pendientes / Compras pendientes. Comprobar y completar recupera una operación ya registrada sin repetir existencias ni documentos. Descartar solo cancela solicitudes no registradas. Los identificadores cortos permiten reconocer cada solicitud; también se muestran usuario, fecha, contacto y estado.
8. **Historiales:** busque en todos los registros, aplique fechas y navegue por páginas. Ver comprobante abre la vista previa con impresión. Un administrador puede anular una venta indicando motivo; se registra auditoría y se devuelve el inventario. El historial de compras muestra sus líneas. En Contactos se puede consultar el historial asociado a cada cliente o proveedor.
9. **Reportes:** seleccione tipo, período y búsqueda; pulse Consultar. Hay atajos Hoy, Ayer, Esta semana y Este mes. Las fechas no se aplican a existencias actuales ni conciliación. Los indicadores y la paginación corresponden a los filtros aplicados. Exportar permite la página visible o el reporte completo filtrado en CSV. Las ventas anuladas se cuentan aparte y no se suman a ventas confirmadas.
10. **Administración → Configuración:** Farmacia permite editar nombre, titular, RUC, dirección, teléfono y logo. Operación configura alertas por vencimiento, mantiene NIO/C$ y permite texto grande. Documentos internos muestra la serie y próximo número existentes. Respaldos recuerda la ubicación local de pg_dump y la última creación desde ese equipo. Conexión comprueba PostgreSQL y permite guardar otra conexión para el próximo inicio, conservando la sesión actual. Usuarios administra cuentas y roles.

Al salir se advierte sobre cambios sin guardar. No cierre durante un registro en curso; las solicitudes pendientes y los borradores guardados sobreviven al reinicio. Las consultas se ejecutan fuera del hilo gráfico. En ventanas pequeñas algunas áreas usan desplazamiento para mantener disponibles todos los controles.

Los documentos y reportes son internos: no constituyen facturas autorizadas ni declaraciones oficiales ante la DGI. La impresión depende de una impresora y controlador disponibles. Los respaldos deben restaurarse y comprobarse conforme a RESPALDO.md.


## Acciones rápidas

- **Ventas → Nuevo cliente:** crea un cliente sin salir de la venta y lo selecciona por su identificador. Nombre obligatorio; teléfono y RUC opcionales. El carrito y el efectivo introducido se conservan. Cancelar no crea registros.
- **Coincidencias:** al guardar un contacto se revisan teléfono y RUC, si se proporcionan. Puede usar un registro existente o guardar uno nuevo después de revisar. Los nombres iguales por sí solos no se rechazan.
- **Compras → Nuevo proveedor:** crea y selecciona al proveedor conservando las líneas del borrador.
- **Compras → Nuevo producto:** abre el formulario habitual. Al guardar, permite introducir lote, vencimiento, cantidad y costo para agregarlo a la recepción. Crear el producto no genera existencias; estas se reciben al confirmar la compra.
- **Inicio → Existencias bajas → Preparar compra** e **Inventario → Registrar compra:** abren Compras con el producto y una cantidad sugerida para superar el mínimo. Debe revisar proveedor, lote, fecha, cantidad y costo antes de registrar. Un borrador existente se conserva; una solicitud pendiente debe resolverse primero.
- **Inventario → Ver movimientos:** abre el historial limitado al identificador del producto, con fechas, búsqueda y páginas. «Todos los productos» elimina ese filtro.
- **Cobro visible:** el total y el botón Cobrar permanecen fuera del desplazamiento del detalle de la venta. El selector del cliente está al principio del panel.

Las altas rápidas requieren administrador, igual que las altas desde Contactos e Inventario.
Los cajeros mantienen sus permisos existentes. Los accesos quedan bloqueados cuando su
operación está procesándose o pendiente de resolución.

## Ajustar tablas y paneles

Arrastre el borde entre dos encabezados para cambiar el ancho de una columna. El ajuste se conserva al actualizar los datos y al volver a abrir esa tabla. Para recuperar la distribución automática, haga clic secundario sobre la tabla y elija **Restablecer anchos de columnas**. El botón de la esquina superior derecha permite mostrar u ocultar columnas.

Las franjas entre paneles se pueden arrastrar. En Inicio se ajusta la separación entre alertas y actividad, y la altura del bloque de indicadores. En Ventas e Inventario se ajusta la relación entre catálogo y detalle; en Compras, la altura de resultados y borrador. Las posiciones se recuerdan localmente para cada orientación. El tamaño mínimo de cada panel evita colapsarlo por completo.

En macOS también puede usar Cmd+Enter para cobrar; Ctrl+Enter continúa disponible. Los tamaños y posiciones se guardan por usuario del sistema operativo, sin alterar los datos de la farmacia.
