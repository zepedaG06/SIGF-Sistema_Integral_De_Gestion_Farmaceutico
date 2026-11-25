package org.sigf.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.sigf.config.Database;
import org.sigf.domain.CartItem;
import org.sigf.domain.PurchaseItem;
import org.sigf.security.Security;

public final class InventoryService {
    private final Database db;

    public InventoryService(Database db) {
        this.db = db;
    }

    public record Product(
            long id,
            String code,
            String name,
            BigDecimal price,
            int stock,
            boolean active,
            int minimum) {}

    public List<Product> products(String search) throws SQLException {
        List<Product> out = new ArrayList<>();
        try (Connection c = db.connection();
                PreparedStatement s =
                        c.prepareStatement(
                                "select p.id,p.code,p.name,p.sale_price,coalesce(sum(b.quantity),0),p.active,p.minimum_stock from product p left join batch b on b.product_id=p.id where p.name ilike ? or p.code ilike ? or coalesce(p.barcode,'') ilike ? group by p.id order by p.name")) {
            for (int i = 1; i <= 3; i++) {
                s.setString(i, "%" + search + "%");
            }
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    out.add(
                            new Product(
                                    r.getLong(1),
                                    r.getString(2),
                                    r.getString(3),
                                    r.getBigDecimal(4),
                                    r.getInt(5),
                                    r.getBoolean(6),
                                    r.getInt(7)));
                }
            }
        }
        return out;
    }

    /** Compatibility entry point. New products remain explicitly unclassified for tax. */
    public void saveProduct(
            Security.User u, Long id, String code, String name, BigDecimal price, int minimum)
            throws SQLException {
        u.admin();
        if (code == null
                || code.isBlank()
                || name == null
                || name.isBlank()
                || price == null
                || price.signum() < 0
                || minimum < 0) {
            throw new IllegalArgumentException("Datos de producto inválidos");
        }
        if (id != null) {
            var p = details(id);
            saveDetails(
                    u,
                    id,
                    new ProductDetails(
                            code,
                            p.barcode(),
                            name,
                            p.category(),
                            p.description(),
                            p.presentation(),
                            p.laboratory(),
                            p.cost(),
                            price,
                            minimum,
                            p.active(),
                            p.taxRate()));
            return;
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, true);
                try (PreparedStatement q =
                        c.prepareStatement(
                                "insert into product(code,name,sale_price,minimum_stock) values(?,?,?,?)")) {
                    q.setString(1, code.trim());
                    q.setString(2, name.trim());
                    q.setBigDecimal(3, price.setScale(2, RoundingMode.UNNECESSARY));
                    q.setInt(4, minimum);
                    q.executeUpdate();
                }
                org.sigf.repositories.AuditRepository.write(c, u, "PRODUCT_CHANGE", code);
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    public long receive(
            Security.User u,
            long product,
            String lot,
            LocalDate expiry,
            int quantity,
            BigDecimal cost)
            throws SQLException {
        return receive(u, product, lot, expiry, quantity, cost, null);
    }

    public long receive(
            Security.User u,
            long product,
            String lot,
            LocalDate expiry,
            int quantity,
            BigDecimal cost,
            Long supplier)
            throws SQLException {
        u.admin();
        if (lot == null
                || lot.isBlank()
                || expiry == null
                || !expiry.isAfter(LocalDate.now())
                || quantity <= 0
                || cost == null
                || cost.signum() < 0) {
            throw new IllegalArgumentException("Lote, vencimiento, cantidad o costo inválido");
        }
        var request =
                new PurchaseRecovery.Pending(
                        UUID.randomUUID(),
                        supplier,
                        List.of(new PurchaseItem(product, lot, expiry, quantity, cost)));
        PurchaseRecovery recovery = new PurchaseRecovery(db);
        recovery.save(u, request);
        long purchase = recovery.resolve(u, request.id());
        recovery.acknowledge(u, request.id(), false);
        return purchase;
    }

    public Map<String, List<String>> catalog() throws SQLException {
        Map<String, List<String>> result = new HashMap<>();
        result.put("Categoría", new ArrayList<>());
        result.put("Laboratorio", new ArrayList<>());
        try (Connection c = db.connection();
                PreparedStatement q =
                        c.prepareStatement(
                                "select kind,name from product_catalog order by lower(name),name");
                ResultSet r = q.executeQuery()) {
            while (r.next()) {
                result.get(r.getString(1).equals("category") ? "Categoría" : "Laboratorio")
                        .add(r.getString(2));
            }
        }
        return result;
    }

    private static void rememberCatalog(Connection c, String kind, String name)
            throws SQLException {
        if (name == null || name.isBlank()) {
            return;
        }
        try (PreparedStatement q =
                c.prepareStatement(
                        "insert into product_catalog(kind,name) values(?,?) on conflict do nothing")) {
            q.setString(1, kind);
            q.setString(2, name.trim());
            q.executeUpdate();
        }
    }

    public record ProductDetails(
            String code,
            String barcode,
            String name,
            String category,
            String description,
            String presentation,
            String laboratory,
            BigDecimal cost,
            BigDecimal price,
            int minimum,
            boolean active,
            BigDecimal taxRate) {}

    public ProductDetails details(long id) throws SQLException {
        try (Connection c = db.connection();
                PreparedStatement s =
                        c.prepareStatement(
                                "select code,coalesce(barcode,''),name,coalesce(category,''),coalesce(description,''),coalesce(presentation,''),coalesce(laboratory,''),purchase_price,sale_price,minimum_stock,active,tax_rate from product where id=?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) {
                    throw new IllegalArgumentException("Producto inexistente");
                }
                return new ProductDetails(
                        r.getString(1),
                        r.getString(2),
                        r.getString(3),
                        r.getString(4),
                        r.getString(5),
                        r.getString(6),
                        r.getString(7),
                        r.getBigDecimal(8),
                        r.getBigDecimal(9),
                        r.getInt(10),
                        r.getBoolean(11),
                        r.getBigDecimal(12));
            }
        }
    }

    public long saveDetails(Security.User u, Long id, ProductDetails p) throws SQLException {
        u.admin();
        if (p.code().isBlank()
                || p.name().isBlank()
                || p.minimum() < 0
                || p.cost().signum() < 0
                || p.price().signum() < 0
                || p.taxRate() == null
                || p.taxRate().signum() < 0
                || p.taxRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Datos de producto inválidos");
        }
        BigDecimal cost = p.cost().setScale(2, RoundingMode.UNNECESSARY),
                price = p.price().setScale(2, RoundingMode.UNNECESSARY);
        String sql =
                id == null
                        ? "insert into product(code,barcode,name,category,description,presentation,laboratory,purchase_price,sale_price,minimum_stock,active,tax_rate) values(?,?,?,?,?,?,?,?,?,?,?,?)"
                        : "update product set code=?,barcode=?,name=?,category=?,description=?,presentation=?,laboratory=?,purchase_price=?,sale_price=?,minimum_stock=?,active=?,tax_rate=? where id=?";
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement(sql + " returning id")) {
                Security.verify(c, u, true);
                s.setString(1, p.code().trim());
                s.setString(2, p.barcode().isBlank() ? null : p.barcode().trim());
                s.setString(3, p.name().trim());
                s.setString(4, p.category());
                s.setString(5, p.description());
                s.setString(6, p.presentation());
                s.setString(7, p.laboratory());
                s.setBigDecimal(8, cost);
                s.setBigDecimal(9, price);
                s.setInt(10, p.minimum());
                s.setBoolean(11, p.active());
                s.setBigDecimal(12, p.taxRate().setScale(5, RoundingMode.UNNECESSARY));
                if (id != null) {
                    s.setLong(13, id);
                }
                long savedId;
                try (ResultSet result = s.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Producto inexistente");
                    }
                    savedId = result.getLong(1);
                }
                try (PreparedStatement a =
                        c.prepareStatement(
                                "insert into audit_log(actor_id,action,details) values(?,'PRODUCT_CHANGE',?)")) {
                    a.setLong(1, u.id());
                    a.setString(2, p.code());
                    a.executeUpdate();
                }
                rememberCatalog(c, "category", p.category());
                rememberCatalog(c, "laboratory", p.laboratory());
                c.commit();
                return savedId;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    public long sell(Security.User u, long product, int quantity, UUID request)
            throws SQLException {
        return new Operations(db)
                .sell(u, List.of(new CartItem(product, quantity)), null, "CASH", request)
                .id();
    }
}
