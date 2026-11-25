package org.sigf.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;

public final class ReportRepository {
    private final Database db;
    private final org.sigf.security.Security.User user;

    public ReportRepository(Database db) {
        this(db, null);
    }

    public ReportRepository(Database db, org.sigf.security.Security.User user) {
        this.db = db;
        this.user = user;
    }

    private void authorize(String name) throws SQLException {
        if (user != null) {
            try (Connection connection = db.connection()) {
                org.sigf.security.Security.verifyReport(connection, user, name);
            }
        }
    }

    private static final Map<String, String> QUERIES =
            Map.ofEntries(
                    Map.entry(
                            "reconciliation",
                            "select 'INVENTARIO' as tipo,b.id as referencia,b.quantity::text as registrado,coalesce(sum(m.quantity),0)::text as esperado from batch b left join stock_movement m on m.batch_id=b.id group by b.id having b.quantity<>coalesce(sum(m.quantity),0) union all select 'PAGOS',s.id,s.total::text,coalesce(sum(p.amount),0)::text from sale s left join payment p on p.sale_id=s.id group by s.id having s.total<>coalesce(sum(p.amount),0)"),
                    Map.entry(
                            "suppliers",
                            "select id,name,coalesce(phone,'') as telefono,active from supplier order by id"),
                    Map.entry(
                            "customers",
                            "select id,name,coalesce(phone,'') as telefono,ruc from customer order by id"),
                    Map.entry("users", "select id,username,role,active from app_user order by id"),
                    Map.entry(
                            "batches",
                            "select b.id,p.code,p.name,b.lot,b.expires_on,b.quantity,b.blocked from batch b join product p on p.id=b.product_id order by b.expires_on,b.id"),
                    Map.entry(
                            "sales",
                            "select s.id,s.created_at,coalesce(c.name,''),s.subtotal,s.tax,s.total,s.status from sale s left join customer c on c.id=s.customer_id order by s.id desc"),
                    Map.entry(
                            "purchases",
                            "select p.id,p.created_at,coalesce(s.name,''),p.total from purchase p left join supplier s on s.id=p.supplier_id order by p.id desc"),
                    Map.entry(
                            "movements",
                            "select m.id,m.created_at,p.name,b.lot,m.kind,m.quantity,u.username from stock_movement m join batch b on b.id=m.batch_id join product p on p.id=b.product_id join app_user u on u.id=m.actor_id order by m.id desc"),
                    Map.entry(
                            "settings",
                            "select key,value from setting where key not in ('tax_rate','pharmacy_name','business_details') order by key"),
                    Map.entry(
                            "audit",
                            "select a.id,a.created_at,coalesce(u.username,''),a.action,coalesce(a.details,'') from audit_log a left join app_user u on u.id=a.actor_id order by a.id desc"),
                    Map.entry(
                            "stock",
                            "select p.id,p.code,p.name,p.sale_price,p.minimum_stock,coalesce(sum(b.quantity),0) as total,coalesce(sum(b.quantity) filter(where p.active and not b.blocked and b.expires_on>=current_date),0) as vendible,p.tax_rate,p.active from product p left join batch b on b.product_id=p.id group by p.id order by p.name"),
                    Map.entry(
                            "top",
                            "select p.code,p.name,sum(l.quantity) as unidades,sum(l.quantity*l.unit_price) as importe from sale_line l join sale s on s.id=l.sale_id join product p on p.id=l.product_id where s.status='CONFIRMED' group by p.id order by unidades desc"),
                    Map.entry(
                            "dashboard_low",
                            "select p.code as codigo,p.name as producto,p.minimum_stock as minimo,coalesce(sum(b.quantity) filter(where not b.blocked and b.expires_on>=current_date),0) as vendible from product p left join batch b on b.product_id=p.id where p.active group by p.id having coalesce(sum(b.quantity) filter(where not b.blocked and b.expires_on>=current_date),0)<=p.minimum_stock order by p.name"),
                    Map.entry(
                            "dashboard_expiring",
                            "select p.name as producto,b.lot as lote,b.expires_on as vence,b.quantity as cantidad from batch b join product p on p.id=b.product_id where p.active and b.quantity>0 and not b.blocked and b.expires_on between current_date and current_date+coalesce((select value::integer from setting where key='expiry_days'),30) order by b.expires_on,b.id limit 100"),
                    Map.entry(
                            "dashboard_recent",
                            "select m.created_at as fecha,p.name as producto,m.kind as tipo,m.quantity as cantidad from stock_movement m join batch b on b.id=m.batch_id join product p on p.id=b.product_id order by m.id desc limit 20"),
                    Map.entry(
                            "dashboard",
                            "select (select count(*) from product where active) as productos,(select coalesce(sum(b.quantity),0) from batch b join product p on p.id=b.product_id where p.active and not b.blocked and b.expires_on>=current_date) as unidades_vendibles,(select count(*) from batch b join product p on p.id=b.product_id where p.active and not b.blocked and b.quantity>0 and b.expires_on between current_date and current_date+coalesce((select value::integer from setting where key='expiry_days'),30)) as lotes_por_vencer,(select count(*) from product p where p.active and (select coalesce(sum(b.quantity),0) from batch b where b.product_id=p.id and not b.blocked and b.expires_on>=current_date)<=p.minimum_stock) as productos_bajos,(select coalesce(sum(total),0) from sale where status='CONFIRMED' and created_at::date=current_date) as ventas_hoy"));

    public ReportGrid page(String name, LocalDate from, LocalDate to, int offset, int limit)
            throws SQLException {
        authorize(name);
        if (offset < 0 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Página inválida");
        } // Selectors and full exports retain the unpaged methods.
        try (Connection c = db.connection();
                PreparedStatement q =
                        c.prepareStatement(
                                "select * from ("
                                        + queryForPage(name, from, to)
                                        + ") result limit ? offset ?")) {
            q.setInt(1, limit);
            q.setInt(2, offset);
            try (ResultSet r = q.executeQuery()) {
                return grid(r);
            }
        }
    }

    private String queryForPage(String name, LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            throw new IllegalArgumentException("Use consulta por período para reportes");
        }
        String sql = QUERIES.get(name);
        if (sql == null) {
            throw new IllegalArgumentException("Consulta desconocida");
        }
        return sql;
    }

    public ReportGrid searchPage(String name, String search, int offset, int limit)
            throws SQLException {
        authorize(name);
        if (offset < 0 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Página inválida");
        }
        String sql = queryForPage(name, null, null);
        try (Connection c = db.connection();
                PreparedStatement q =
                        c.prepareStatement(
                                "select * from ("
                                        + sql
                                        + ") result where exists (select 1 from json_each_text(row_to_json(result)) as field where field.value ilike ?) limit ? offset ?")) {
            q.setString(1, "%" + search.trim() + "%");
            q.setInt(2, limit);
            q.setInt(3, offset);
            try (ResultSet r = q.executeQuery()) {
                return grid(r);
            }
        }
    }

    public ReportGrid grid(String name) throws SQLException {
        authorize(name);
        if (name.equals("dashboard")) {
            validateCurrency(LocalDate.now(), LocalDate.now());
        }
        String sql = QUERIES.get(name);
        if (sql == null) {
            throw new IllegalArgumentException("Consulta desconocida");
        }
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql);
                ResultSet r = q.executeQuery()) {
            return grid(r);
        }
    }

    public ReportGrid report(String name, LocalDate from, LocalDate to) throws SQLException {
        return reportPage(name, from, to, 0, 0);
    }

    public ReportGrid reportPage(String name, LocalDate from, LocalDate to, int offset, int limit)
            throws SQLException {
        return reportSearch(name, from, to, offset, limit, "");
    }

    private String reportSql(String name) {
        return switch (name) {
            case "payments" ->
                    "select p.id,s.created_at,s.id as venta,p.method as metodo,p.amount as importe,p.received_amount as recibido,p.change_amount as cambio,s.status as estado from payment p join sale s on s.id=p.sale_id where s.created_at::date between ? and ? order by p.id desc";
            case "voided_docs" ->
                    "select s.id,coalesce(d.series,'SIN SERIE') as serie,d.number as numero,s.created_at,s.voided_at,s.void_reason,s.total from sale s left join internal_document d on d.sale_id=s.id where s.status='VOID' and s.voided_at::date between ? and ? order by s.voided_at";
            case "tax_summary" ->
                    "select tax_rate as tasa_iva,sum(base) as base,sum(round(base*tax_rate,2)) as iva from (select s.id,l.product_id,l.tax_rate,sum(l.unit_price*l.quantity) as base from sale_line l join sale s on s.id=l.sale_id where s.status='CONFIRMED' and s.created_at::date between ? and ? and l.tax_rate is not null group by s.id,l.product_id,l.tax_rate) x group by tax_rate order by tax_rate";
            case "internal_docs" ->
                    "select s.id,coalesce(d.series,'SIN SERIE') as serie,d.number as numero,s.created_at,coalesce(h.customer_name,'SIN DATOS HISTORICOS') as cliente,s.subtotal,s.tax,s.total,s.status,s.voided_at,s.void_reason from sale s left join internal_document d on d.sale_id=s.id left join sale_receipt_snapshot h on h.sale_id=s.id where s.created_at::date between ? and ? order by s.id";
            case "sales" ->
                    "select s.id,s.created_at,coalesce(h.customer_name,c.name,'Consumidor final') as cliente,s.subtotal,s.tax,s.total,s.status from sale s left join sale_receipt_snapshot h on h.sale_id=s.id left join customer c on c.id=s.customer_id where s.created_at::date between ? and ? order by s.id desc";
            case "purchases" ->
                    "select p.id,p.created_at,coalesce(s.name,'Sin proveedor') as proveedor,p.total from purchase p left join supplier s on s.id=p.supplier_id where p.created_at::date between ? and ? order by p.id desc";
            case "movements" ->
                    "select m.id,m.created_at,p.name as producto,b.lot as lote,m.kind as movimiento,m.quantity as cantidad,u.username as usuario from stock_movement m join batch b on b.id=m.batch_id join product p on p.id=b.product_id join app_user u on u.id=m.actor_id where m.created_at::date between ? and ? order by m.id desc";
            case "audit" ->
                    "select id,created_at,actor_id,action,details from audit_log where created_at::date between ? and ? order by id desc";
            case "batches" ->
                    "select b.id,p.name,b.lot,b.expires_on,b.quantity,b.blocked from batch b join product p on p.id=b.product_id where b.expires_on between ? and ? order by b.expires_on";
            case "top" ->
                    "select p.code,p.name,sum(l.quantity) as unidades,sum(l.quantity*l.unit_price) as importe from sale_line l join sale s on s.id=l.sale_id join product p on p.id=l.product_id where s.status='CONFIRMED' and s.created_at::date between ? and ? group by p.id order by unidades desc";
            default -> null;
        };
    }

    private String source(String name) {
        String sql = reportSql(name);
        if (sql == null) {
            sql = QUERIES.get(name);
        }
        if (sql == null) {
            throw new IllegalArgumentException("Reporte desconocido");
        }
        return sql;
    }

    private int dates(PreparedStatement q, String name, LocalDate from, LocalDate to)
            throws SQLException {
        if (reportSql(name) == null) {
            return 1;
        }
        q.setDate(1, java.sql.Date.valueOf(from));
        q.setDate(2, java.sql.Date.valueOf(to));
        return 3;
    }

    private void validateReport(String name, LocalDate from, LocalDate to) throws SQLException {
        authorize(name);
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Período inválido");
        }
        if (Set.of("tax_summary", "top", "sales", "internal_docs", "payments").contains(name)) {
            validateCurrency(from, to);
        }
        if (name.equals("voided_docs")) {
            try (Connection c = db.connection();
                    PreparedStatement q =
                            c.prepareStatement(
                                    "select 1 from sale s join sale_receipt_snapshot h on h.sale_id=s.id where s.voided_at::date between ? and ? and h.currency<>'NIO' limit 1")) {
                q.setDate(1, java.sql.Date.valueOf(from));
                q.setDate(2, java.sql.Date.valueOf(to));
                try (ResultSet r = q.executeQuery()) {
                    if (r.next()) {
                        throw new IllegalStateException(
                                "Hay anulaciones de otra moneda en el período");
                    }
                }
            }
        }
        if (name.equals("tax_summary")) {
            try (Connection c = db.connection();
                    PreparedStatement q =
                            c.prepareStatement(
                                    "select 1 from sale_line l join sale s on s.id=l.sale_id where s.status='CONFIRMED' and s.created_at::date between ? and ? and l.tax_rate is null limit 1")) {
                q.setDate(1, java.sql.Date.valueOf(from));
                q.setDate(2, java.sql.Date.valueOf(to));
                try (ResultSet r = q.executeQuery()) {
                    if (r.next()) {
                        throw new IllegalArgumentException(
                                "Hay ventas históricas sin detalle de IVA; el resumen sería incompleto");
                    }
                }
            }
        }
    }

    public ReportGrid reportSearch(
            String name, LocalDate from, LocalDate to, int offset, int limit, String search)
            throws SQLException {
        if (offset < 0 || limit < 0 || limit > 500) {
            throw new IllegalArgumentException("Página inválida");
        }
        validateReport(name, from, to);
        String sql =
                "select * from ("
                        + source(name)
                        + ") result where exists (select 1 from json_each_text(row_to_json(result)) as field where field.value ilike ?)"
                        + (limit == 0 ? "" : " limit ? offset ?");
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql)) {
            int n = dates(q, name, from, to);
            q.setString(n++, "%" + search.trim() + "%");
            if (limit > 0) {
                q.setInt(n++, limit);
                q.setInt(n, offset);
            }
            try (ResultSet r = q.executeQuery()) {
                return grid(r);
            }
        }
    }

    public ReportGrid summary(String name, LocalDate from, LocalDate to, String search)
            throws SQLException {
        validateReport(name, from, to);
        String amount =
                switch (name) {
                    case "sales", "internal_docs" ->
                            "coalesce(sum(total) filter(where status='CONFIRMED'),0)";
                    case "purchases", "voided_docs" -> "coalesce(sum(total),0)";
                    case "payments" -> "coalesce(sum(importe) filter(where estado='CONFIRMED'),0)";
                    case "top" -> "coalesce(sum(importe),0)";
                    case "tax_summary" -> "coalesce(sum(iva),0)";
                    default -> "null::numeric";
                };
        String voided =
                Set.of("sales", "internal_docs").contains(name)
                        ? "count(*) filter(where status='VOID')"
                        : "0";
        String sql =
                "select count(*) as resultados,"
                        + amount
                        + " as importe,"
                        + voided
                        + " as anuladas from ("
                        + source(name)
                        + ") result where exists (select 1 from json_each_text(row_to_json(result)) as field where field.value ilike ?)";
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql)) {
            int n = dates(q, name, from, to);
            q.setString(n, "%" + search.trim() + "%");
            try (ResultSet r = q.executeQuery()) {
                return grid(r);
            }
        }
    }

    private void validateCurrency(LocalDate from, LocalDate to) throws SQLException {
        if (from == null || to == null) {
            return;
        }
        try (Connection c = db.connection();
                PreparedStatement q =
                        c.prepareStatement(
                                "select exists(select 1 from sale s join sale_receipt_snapshot h on h.sale_id=s.id where s.created_at::date between ? and ? and h.currency<>'NIO')")) {
            q.setDate(1, java.sql.Date.valueOf(from));
            q.setDate(2, java.sql.Date.valueOf(to));
            try (ResultSet r = q.executeQuery()) {
                r.next();
                if (r.getBoolean(1)) {
                    throw new IllegalStateException(
                            "El período contiene documentos de otra moneda; deben conciliarse antes de sumar importes en C$");
                }
            }
        }
    }

    private static ReportGrid grid(ResultSet r) throws SQLException {
        var md = r.getMetaData();
        List<String> cols = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            cols.add(md.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        while (r.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= cols.size(); i++) {
                row.add(Objects.toString(r.getObject(i), ""));
            }
            rows.add(row);
        }
        return new ReportGrid(cols, rows);
    }
}
