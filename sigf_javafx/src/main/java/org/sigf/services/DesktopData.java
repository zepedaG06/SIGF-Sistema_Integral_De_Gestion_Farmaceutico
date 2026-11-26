package org.sigf.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;
import org.sigf.security.Security;

/** Consultas tipadas para los espacios de trabajo de escritorio. */
public final class DesktopData {
    private final Database db;
    private final Security.User actor;

    public DesktopData(Database db) {
        this(db, null);
    }

    public DesktopData(Database db, Security.User actor) {
        this.db = db;
        this.actor = actor;
    }

    private void authorize(String name) throws SQLException {
        if (actor != null) {
            try (Connection connection = db.connection()) {
                Security.verifyReport(connection, actor, name);
            }
        }
    }

    public record Option(long id, String name) {
        public String toString() {
            return name;
        }
    }

    public record Product(
            long id,
            String code,
            String barcode,
            String name,
            String category,
            String presentation,
            String laboratory,
            BigDecimal price,
            BigDecimal tax,
            BigDecimal cost,
            int minimum,
            boolean active,
            long total,
            long available) {
        public BigDecimal finalPrice() {
            return tax == null
                    ? null
                    : price.multiply(BigDecimal.ONE.add(tax)).setScale(2, RoundingMode.HALF_UP);
        }

        public String state() {
            return !active
                    ? "Inactivo"
                    : available == 0
                            ? "Agotado"
                            : available <= minimum ? "Stock bajo" : "Disponible";
        }
    }

    public List<Product> products(
            String text, String category, String laboratory, String state, String stock, int offset)
            throws SQLException {
        authorize("stock");
        return products(text, category, laboratory, state, stock, offset, null);
    }

    private List<Product> products(
            String text,
            String category,
            String laboratory,
            String state,
            String stock,
            int offset,
            Long id)
            throws SQLException {
        List<Product> out = new ArrayList<>();
        String sql =
                "select * from (select p.*,coalesce(sum(b.quantity),0) as total,coalesce(sum(b.quantity) filter(where not b.blocked and b.expires_on>=current_date),0) as available from product p left join batch b on b.product_id=p.id group by p.id) p where (?::bigint is null or p.id=?) and (p.name ilike ? or p.code ilike ? or coalesce(p.barcode,'') ilike ?) and (?='' or coalesce(category,'')=?) and (?='' or coalesce(laboratory,'')=?) and (?='Todos' or active=(?='Activo')) and (?='Todas' or (?='Agotado' and available=0) or (?='Stock bajo' and available<=minimum_stock) or (?='Disponible' and available>0)) order by name,id limit 51 offset ?";
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql)) {
            int n = 1;
            q.setObject(n++, id, Types.BIGINT);
            q.setObject(n++, id, Types.BIGINT);
            for (int i = 0; i < 3; i++) {
                q.setString(n++, "%" + text.trim() + "%");
            }
            q.setString(n++, category);
            q.setString(n++, category);
            q.setString(n++, laboratory);
            q.setString(n++, laboratory);
            q.setString(n++, state);
            q.setString(n++, state);
            for (int i = 0; i < 4; i++) {
                q.setString(n++, stock);
            }
            q.setInt(n, offset);
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    out.add(
                            new Product(
                                    r.getLong("id"),
                                    r.getString("code"),
                                    Objects.toString(r.getString("barcode"), ""),
                                    r.getString("name"),
                                    Objects.toString(r.getString("category"), ""),
                                    Objects.toString(r.getString("presentation"), ""),
                                    Objects.toString(r.getString("laboratory"), ""),
                                    r.getBigDecimal("sale_price"),
                                    r.getBigDecimal("tax_rate"),
                                    r.getBigDecimal("purchase_price"),
                                    r.getInt("minimum_stock"),
                                    r.getBoolean("active"),
                                    r.getLong("total"),
                                    r.getLong("available")));
                }
            }
        }
        return out;
    }

    public Optional<Product> product(long id) throws SQLException {
        authorize("stock");
        return products("", "", "", "Todos", "Todas", 0, id).stream().findFirst();
    }

    public Optional<Product> productByCode(String code) throws SQLException {
        long id;
        try (Connection connection = db.connection();
                PreparedStatement query =
                        connection.prepareStatement("select id from product where code=?")) {
            query.setString(1, code);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                id = result.getLong(1);
            }
        }
        return product(id);
    }

    public List<Product> scanMatches(String text) throws SQLException {
        authorize("stock");
        List<Long> ids = new ArrayList<>();
        try (Connection connection = db.connection();
                PreparedStatement query =
                        connection.prepareStatement(
                                "select id from product where active and (lower(code)=lower(?) or barcode=?) order by id limit 3")) {
            query.setString(1, text.trim());
            query.setString(2, text.trim());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getLong(1));
                }
            }
        }
        List<Product> result = new ArrayList<>();
        for (long id : ids) {
            product(id).filter(Product::active).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    public ReportGrid productMovements(
            long product,
            java.time.LocalDate from,
            java.time.LocalDate to,
            String search,
            int offset)
            throws SQLException {
        authorize("movements");
        if (product < 1 || from == null || to == null || to.isBefore(from) || offset < 0) {
            throw new IllegalArgumentException("Filtros de movimientos inválidos");
        }
        String sql =
                "select * from (select m.id,m.created_at,p.name as producto,b.lot as lote,m.quantity,m.kind,m.reference_id from stock_movement m join batch b on b.id=m.batch_id join product p on p.id=b.product_id where b.product_id=? and m.created_at::date between ? and ?) result where exists(select 1 from json_each_text(row_to_json(result)) field where field.value ilike ?) order by id desc limit 51 offset ?";
        try (Connection connection = db.connection();
                PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, product);
            query.setDate(2, java.sql.Date.valueOf(from));
            query.setDate(3, java.sql.Date.valueOf(to));
            query.setString(4, "%" + search.trim() + "%");
            query.setInt(5, offset);
            try (ResultSet rows = query.executeQuery()) {
                return grid(rows);
            }
        }
    }

    public ReportGrid lots(String text, String state, int offset) throws SQLException {
        authorize("batches");
        String sql =
                "select * from (select b.id,p.code as codigo,p.name as producto,b.lot as lote,b.expires_on as vencimiento,b.quantity as cantidad,b.blocked as bloqueado,case when b.blocked then 'Bloqueado' when b.expires_on<current_date then 'Vencido' when b.expires_on<=current_date+coalesce((select value::integer from setting where key='expiry_days'),30) then 'Por vencer' else 'Disponible' end as estado from batch b join product p on p.id=b.product_id) result where exists (select 1 from json_each_text(row_to_json(result)) as field where field.value ilike ?) and (?='Todos' or estado=?) order by vencimiento,id limit 51 offset ?";
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, "%" + text.trim() + "%");
            q.setString(2, state);
            q.setString(3, state);
            q.setInt(4, offset);
            try (ResultSet r = q.executeQuery()) {
                return grid(r);
            }
        }
    }

    public ReportGrid pending(Security.User user, boolean purchase) throws SQLException {
        String sql =
                purchase
                        ? "select r.request_id as solicitud,u.username as usuario,r.created_at as fecha,coalesce(s.name,'Sin proveedor') as proveedor,(select sum(l.quantity*l.unit_cost) from purchase_request_line l where l.request_id=r.request_id) as total,case when exists(select 1 from purchase p where p.request_id=r.request_id) then 'Registrada: confirmar resultado' else 'Pendiente de registrar' end as estado from purchase_request r join app_user u on u.id=r.actor_id left join supplier s on s.id=r.supplier_id where r.acknowledged_at is null and (r.actor_id=? or ?) order by r.created_at"
                        : "select r.request_id as solicitud,u.username as usuario,r.created_at as fecha,coalesce(c.name,'Consumidor final') as cliente,r.expected_total as total,case when exists(select 1 from sale s where s.request_id=r.request_id) then 'Registrada: confirmar resultado' else 'Pendiente de registrar' end as estado from sale_request r join app_user u on u.id=r.actor_id left join customer c on c.id=r.customer_id where r.acknowledged_at is null and (r.actor_id=? or ?) order by r.created_at";
        try (Connection c = db.connection()) {
            Security.verify(
                    c,
                    user,
                    purchase
                            ? org.sigf.security.Permission.ADMINISTER
                            : org.sigf.security.Permission.SELL);
            try (PreparedStatement q = c.prepareStatement(sql)) {
                q.setLong(1, user.id());
                q.setBoolean(2, user.role().equals("ADMIN"));
                try (ResultSet r = q.executeQuery()) {
                    return grid(r);
                }
            }
        }
    }

    public List<Option> options(String kind, String text) throws SQLException {
        authorize(kind.equals("customer") ? "customers" : "suppliers");
        String sql =
                switch (kind) {
                    case "supplier" ->
                            "select id,name from supplier where active and name ilike ? order by name,id limit 40";
                    case "customer" ->
                            "select id,name from customer where name ilike ? order by name,id limit 40";
                    default -> throw new IllegalArgumentException("Selector inválido");
                };
        List<Option> out = new ArrayList<>();
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, "%" + text.trim() + "%");
            try (ResultSet r = q.executeQuery()) {
                while (r.next()) {
                    out.add(new Option(r.getLong(1), r.getString(2)));
                }
            }
        }
        return out;
    }

    public ReportGrid productLots(long id) throws SQLException {
        authorize("batches");
        return query(
                "select b.id as referencia,b.lot as lote,b.expires_on as vencimiento,b.quantity as existencia,case when b.blocked then 'Bloqueado' when b.expires_on<current_date then 'Vencido' when b.expires_on<=current_date+coalesce((select value::integer from setting where key='expiry_days'),30) then 'Por vencer' else 'Disponible' end as estado from batch b where b.product_id=? order by b.expires_on,b.id",
                id);
    }

    public ReportGrid productMoves(long id) throws SQLException {
        authorize("movements");
        return query(
                "select m.created_at as fecha,b.lot as lote,m.kind as movimiento,m.quantity as cantidad from stock_movement m join batch b on b.id=m.batch_id where b.product_id=? order by m.id desc limit 100",
                id);
    }

    public ReportGrid contactHistory(boolean supplier, long id) throws SQLException {
        authorize(supplier ? "purchases" : "sales");
        return supplier
                ? query(
                        "select id,created_at as fecha,total from purchase where supplier_id=? order by id desc limit 200",
                        id)
                : query(
                        "select id,created_at as fecha,total,status as estado from sale where customer_id=? order by id desc limit 200",
                        id);
    }

    public ReportGrid purchaseLines(long id) throws SQLException {
        authorize("purchases");
        return query(
                "select p.name as producto,b.lot as lote,b.expires_on as vencimiento,l.quantity as cantidad,l.unit_cost as costo,l.quantity*l.unit_cost as subtotal from purchase_line l join batch b on b.id=l.batch_id join product p on p.id=b.product_id where l.purchase_id=? order by l.id",
                id);
    }

    private ReportGrid query(String sql, long id) throws SQLException {
        try (Connection c = db.connection();
                PreparedStatement q = c.prepareStatement(sql)) {
            q.setLong(1, id);
            try (ResultSet r = q.executeQuery()) {
                return grid(r);
            }
        }
    }

    public Map<String, String> settings() throws SQLException {
        authorize("settings");
        Map<String, String> out = new HashMap<>();
        try (Connection c = db.connection();
                Statement q = c.createStatement();
                ResultSet r = q.executeQuery("select key,value from setting")) {
            while (r.next()) {
                out.put(r.getString(1), r.getString(2));
            }
        }
        try (Connection c = db.connection();
                Statement q = c.createStatement();
                ResultSet r =
                        q.executeQuery("select code,next_number from internal_series where id=1")) {
            if (r.next()) {
                out.put("series", r.getString(1));
                out.put("next_number", r.getString(2));
            }
        }
        return out;
    }

    public static ReportGrid grid(ResultSet r) throws SQLException {
        List<String> cols = new ArrayList<>();
        for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
            cols.add(r.getMetaData().getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        while (r.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= cols.size(); i++) {
                row.add(Objects.toString(r.getObject(i), ""));
            }
            rows.add(row);
        }
        return new ReportGrid(List.copyOf(cols), List.copyOf(rows));
    }
}
