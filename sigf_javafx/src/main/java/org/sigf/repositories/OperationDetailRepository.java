package org.sigf.repositories;

import java.sql.*;
import java.util.*;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;
import org.sigf.reports.JasperReportExporter;
import org.sigf.security.Security;

public final class OperationDetailRepository {
    private final Database db;

    public OperationDetailRepository(Database db) {
        this.db = db;
    }

    public record Detail(String header, ReportGrid lines) {}

    public Detail load(Security.User user, String kind, long id) throws SQLException {
        if (!Set.of("sales", "purchases", "movements").contains(kind))
            throw new IllegalArgumentException("Detalle desconocido");
        try (var c = db.connection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            Security.verifyReport(c, user, kind);
            String header =
                    switch (kind) {
                        case "sales" ->
                                "select s.id as venta,s.created_at as fecha,coalesce(h.customer_name,'Consumidor final / documento anterior') as cliente,coalesce(h.customer_ruc,'') as ruc_cliente,coalesce(h.seller_name,u.username) as vendedor,s.status as estado,s.subtotal,s.tax as iva,s.total,s.void_reason as motivo_anulacion,(select username from app_user where id=s.voided_by) as anulado_por,s.voided_at as fecha_anulacion,p.method as pago,p.amount as importe,p.received_amount as recibido,p.change_amount as cambio,p.bank_name as banco,p.transfer_reference as referencia,p.transfer_description as descripcion from sale s join app_user u on u.id=s.created_by left join sale_receipt_snapshot h on h.sale_id=s.id left join payment p on p.sale_id=s.id where s.id=?"
                                        + (user.role().equals("CASHIER")
                                                ? " and s.created_by=" + user.id()
                                                : "");
                        case "purchases" ->
                                "select p.id as compra,p.created_at as fecha,coalesce(h.supplier_name,'Documento anterior sin instantánea del proveedor') as proveedor,coalesce(h.actor_name,u.username) as responsable,p.total from purchase p join app_user u on u.id=p.created_by left join purchase_snapshot h on h.purchase_id=p.id where p.id=?";
                        default ->
                                "select m.id as movimiento,m.created_at as fecha,m.kind as tipo,m.reference_id as documento,m.quantity as cantidad,u.username as responsable,b.lot as lote,b.expires_on as vencimiento from stock_movement m join app_user u on u.id=m.actor_id join batch b on b.id=m.batch_id where m.id=?";
                    };
            StringBuilder text = new StringBuilder();
            try (var q = c.prepareStatement(header)) {
                q.setLong(1, id);
                try (var r = q.executeQuery()) {
                    if (!r.next())
                        throw new SecurityException("Documento inexistente o no autorizado");
                    for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
                        Object value = r.getObject(i);
                        if (value != null)
                            text.append(
                                            JasperReportExporter.label(
                                                    r.getMetaData().getColumnLabel(i)))
                                    .append(": ")
                                    .append(value instanceof java.math.BigDecimal ? "C$ " : "")
                                    .append(JasperReportExporter.display(value.toString()))
                                    .append("   ·   ");
                    }
                }
            }
            String lines =
                    switch (kind) {
                        case "sales" ->
                                "select coalesce(l.product_name_snapshot,'Producto anterior sin instantánea') as producto,coalesce(l.presentation_snapshot,'') as presentacion,b.lot as lote,b.expires_on as vencimiento,l.quantity as cantidad,l.unit_price as precio,l.tax_rate as tasa_iva,l.quantity*l.unit_price as subtotal from sale_line l join batch b on b.id=l.batch_id where l.sale_id=? order by l.id";
                        case "purchases" ->
                                "select coalesce(l.product_name_snapshot,p.name) as producto,coalesce(l.presentation_snapshot,'') as presentacion,b.lot as lote,b.expires_on as vencimiento,l.quantity as cantidad,l.unit_cost as costo,l.quantity*l.unit_cost as subtotal from purchase_line l join batch b on b.id=l.batch_id join product p on p.id=b.product_id where l.purchase_id=? order by l.id";
                        default ->
                                "select p.code as codigo,p.name as producto,b.lot as lote,m.kind as tipo,m.quantity as cantidad from stock_movement m join batch b on b.id=m.batch_id join product p on p.id=b.product_id where m.id=?";
                    };
            try (var q = c.prepareStatement(lines)) {
                q.setLong(1, id);
                try (var r = q.executeQuery()) {
                    return new Detail(text.toString(), org.sigf.services.DesktopData.grid(r));
                }
            }
        }
    }
}
