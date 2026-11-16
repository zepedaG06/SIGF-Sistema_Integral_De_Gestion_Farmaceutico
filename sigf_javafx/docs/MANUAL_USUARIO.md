# Manual de usuario

1. Conéctese a `sigf_javafx_db` con el usuario PostgreSQL configurado. En el primer acceso cree el administrador de SIGF y configure los datos de la farmacia. Puede editarlos en Configuración → Datos de la farmacia.
2. En **Productos**, registre nombre, código, precio sin IVA y tasa de IVA. Seleccione un producto para recibir un lote; también puede registrar varias líneas en **Compras**. Use los selectores para elegir proveedor y producto.
3. En **Punto de venta**, seleccione productos, agréguelos al carrito y cambie cantidades o quite líneas. Elija cliente y forma de pago antes de confirmar. El carrito muestra importes orientativos y el sistema vuelve a comprobarlos al registrar.
4. Si una operación queda incierta, use **Pendientes** para ventas o **Pend. compras** para compras. Una operación ya registrada se recupera con el mismo número; una solicitud no realizada puede cancelarse.
5. En **Ventas**, consulte o imprima comprobantes internos; un administrador puede anularlos con motivo. En **Lotes**, un administrador puede ajustar o bloquear lotes.
6. En **Reportes**, consulte ventas, compras, existencias, vencimientos, movimientos, IVA y comprobantes o anulaciones internas; filtre fechas y exporte CSV. En **Configuración**, un administrador puede generar un respaldo seleccionando `pg_dump` o `pg_dump.exe` y el destino.

Las series y comprobantes son de demostración, sin valor fiscal. Los reportes no generan ni presentan declaraciones ante la DGI. Los precios se introducen sin IVA y las tasas deben ser revisadas.
