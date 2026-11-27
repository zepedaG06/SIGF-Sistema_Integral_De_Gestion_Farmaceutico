package org.sigf.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.sigf.config.Database;
import org.sigf.domain.PurchaseItem;
import org.sigf.repositories.AuditRepository;
import org.sigf.repositories.StockMovementRepository;
import org.sigf.security.Security;

public final class PurchaseService {
    private final Database db;

    public PurchaseService(Database db) {
        this.db = db;
    }

    public long purchase(Security.User u, long supplier, List<PurchaseItem> items)
            throws SQLException {
        return purchase(u, Long.valueOf(supplier), items, UUID.randomUUID());
    }

    public long purchase(Security.User u, Long supplier, List<PurchaseItem> items, UUID request)
            throws SQLException {
        u.admin();
        if (request == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La compra no tiene líneas");
        }
        BigDecimal total = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        for (PurchaseItem item : items) {
            if (item == null
                    || item.productId() <= 0
                    || item.lot() == null
                    || item.lot().isBlank()
                    || item.expiry() == null
                    || item.quantity() <= 0
                    || item.cost() == null
                    || item.cost().signum() < 0) {
                throw new IllegalArgumentException("Línea de compra inválida");
            }
            if (!seen.add(item.productId() + "/" + item.lot())) {
                throw new IllegalArgumentException("No repita producto y lote");
            }
            total =
                    total.add(
                            item.cost()
                                    .setScale(2, RoundingMode.UNNECESSARY)
                                    .multiply(BigDecimal.valueOf(item.quantity())));
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, true);
                try (PreparedStatement lock =
                        c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    lock.setString(1, request.toString());
                    lock.execute();
                }
                try (PreparedStatement existing =
                        c.prepareStatement(
                                "select id,created_by,supplier_id from purchase where request_id=?")) {
                    existing.setObject(1, request);
                    try (ResultSet r = existing.executeQuery()) {
                        if (r.next()) {
                            if (r.getLong(2) != u.id() && !u.role().equals("ADMIN")) {
                                throw new SecurityException("Solicitud ajena");
                            }
                            long id = r.getLong(1);
                            if (!java.util.Objects.equals(supplier, r.getObject(3))
                                    || !sameLines(c, id, items)) {
                                throw new IllegalArgumentException(
                                        "La clave de compra ya fue utilizada con otros datos");
                            }
                            c.commit();
                            return id;
                        }
                    }
                }
                try (PreparedStatement cancelled =
                        c.prepareStatement(
                                "select cancelled,supplier_id from purchase_request where request_id=?")) {
                    cancelled.setObject(1, request);
                    try (ResultSet r = cancelled.executeQuery()) {
                        if (r.next()) {
                            if (r.getBoolean(1)) {
                                throw new IllegalArgumentException("Solicitud cancelada");
                            }
                            if (!java.util.Objects.equals(supplier, r.getObject(2))
                                    || !sameItems(
                                            c,
                                            "select product_id,lot,expires_on,quantity,unit_cost from purchase_request_line where request_id=?",
                                            request,
                                            items)) {
                                throw new IllegalArgumentException(
                                        "La compra no coincide con su solicitud pendiente");
                            }
                        }
                    }
                }
                for (PurchaseItem item : items) {
                    if (!item.expiry().isAfter(LocalDate.now())) {
                        throw new IllegalArgumentException("El vencimiento debe ser futuro");
                    }
                }
                if (supplier != null) {
                    try (PreparedStatement s =
                            c.prepareStatement(
                                    "select id from supplier where id=? and active for share")) {
                        s.setLong(1, supplier);
                        try (ResultSet r = s.executeQuery()) {
                            if (!r.next()) {
                                throw new IllegalArgumentException(
                                        "Proveedor inactivo o inexistente");
                            }
                        }
                    }
                }
                long purchase;
                try (PreparedStatement s =
                        c.prepareStatement(
                                "insert into purchase(supplier_id,created_by,total,request_id) values(?,?,?,?) returning id")) {
                    if (supplier == null) {
                        s.setNull(1, Types.BIGINT);
                    } else {
                        s.setLong(1, supplier);
                    }
                    s.setLong(2, u.id());
                    s.setBigDecimal(3, total);
                    s.setObject(4, request);
                    try (ResultSet r = s.executeQuery()) {
                        r.next();
                        purchase = r.getLong(1);
                    }
                }
                for (PurchaseItem item :
                        items.stream()
                                .sorted(
                                        Comparator.comparingLong(PurchaseItem::productId)
                                                .thenComparing(PurchaseItem::lot))
                                .toList()) {
                    try (PreparedStatement s =
                            c.prepareStatement(
                                    "select id from product where id=? and active for share")) {
                        s.setLong(1, item.productId());
                        try (ResultSet r = s.executeQuery()) {
                            if (!r.next()) {
                                throw new IllegalArgumentException(
                                        "Producto inactivo o inexistente");
                            }
                        }
                    }
                    long batch;
                    try (PreparedStatement s =
                            c.prepareStatement(
                                    "insert into batch(product_id,lot,expires_on,quantity) values(?,?,?,?) on conflict(product_id,lot) do update set quantity=batch.quantity+excluded.quantity where batch.expires_on=excluded.expires_on returning id")) {
                        s.setLong(1, item.productId());
                        s.setString(2, item.lot().trim());
                        s.setDate(3, java.sql.Date.valueOf(item.expiry()));
                        s.setInt(4, item.quantity());
                        try (ResultSet r = s.executeQuery()) {
                            if (!r.next()) {
                                throw new IllegalArgumentException(
                                        "El lote tiene otro vencimiento");
                            }
                            batch = r.getLong(1);
                        }
                    }
                    try (PreparedStatement s =
                            c.prepareStatement(
                                    "insert into purchase_line(purchase_id,batch_id,quantity,unit_cost,product_name_snapshot,presentation_snapshot) values(?,?,?,?,(select name from product where id=?),(select presentation from product where id=?))")) {
                        s.setLong(1, purchase);
                        s.setLong(2, batch);
                        s.setInt(3, item.quantity());
                        s.setBigDecimal(4, item.cost());
                        s.setLong(5, item.productId());
                        s.setLong(6, item.productId());
                        s.executeUpdate();
                    }
                    StockMovementRepository.write(
                            c, batch, item.quantity(), "PURCHASE", purchase, u.id());
                }
                try (var snapshot =
                        c.prepareStatement(
                                "insert into purchase_snapshot(purchase_id,supplier_name,supplier_ruc,actor_name) values(?,coalesce((select name from supplier where id=?),'Sin proveedor'),'',?)")) {
                    snapshot.setLong(1, purchase);
                    snapshot.setObject(2, supplier);
                    snapshot.setString(3, u.name());
                    snapshot.executeUpdate();
                }
                AuditRepository.write(c, u, "PURCHASE", Long.toString(purchase));
                c.commit();
                return purchase;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static boolean sameLines(Connection connection, long purchase, List<PurchaseItem> input)
            throws SQLException {
        return sameItems(
                connection,
                "select b.product_id,b.lot,b.expires_on,l.quantity,l.unit_cost from purchase_line l join batch b on b.id=l.batch_id where l.purchase_id=?",
                purchase,
                input);
    }

    private static boolean sameItems(
            Connection connection, String sql, Object key, List<PurchaseItem> input)
            throws SQLException {
        var expected = new java.util.HashMap<String, PurchaseItem>();
        for (var item : input) {
            expected.put(item.productId() + "/" + item.lot(), item);
        }
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setObject(1, key);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    var item = expected.remove(rows.getLong(1) + "/" + rows.getString(2));
                    if (item == null) {
                        return false;
                    }
                    if (item.productId() != rows.getLong(1)
                            || !item.lot().equals(rows.getString(2))
                            || !item.expiry().equals(rows.getDate(3).toLocalDate())
                            || item.quantity() != rows.getInt(4)
                            || item.cost().compareTo(rows.getBigDecimal(5)) != 0) {
                        return false;
                    }
                }
            }
            return expected.isEmpty();
        }
    }
}
