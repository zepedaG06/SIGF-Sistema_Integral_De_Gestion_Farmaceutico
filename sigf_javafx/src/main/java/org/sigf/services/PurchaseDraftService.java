package org.sigf.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.sigf.config.Database;
import org.sigf.domain.PurchaseItem;
import org.sigf.security.Security;

public final class PurchaseDraftService {
    public record Draft(
            Long supplier,
            String supplierName,
            UUID request,
            List<PurchaseItem> items,
            Map<Long, String> names) {}

    private final Database db;

    public PurchaseDraftService(Database db) {
        this.db = db;
    }

    public Optional<Draft> load(Security.User user) throws SQLException {
        try (Connection c = db.connection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            c.setAutoCommit(false);
            Security.verify(c, user, true);
            try (PreparedStatement q =
                    c.prepareStatement(
                            "select d.supplier_id,coalesce(s.name,''),d.request_id from purchase_draft d left join supplier s on s.id=d.supplier_id where d.actor_id=?")) {
                q.setLong(1, user.id());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) {
                        return Optional.empty();
                    }
                    Long supplier = (Long) r.getObject(1);
                    String name = r.getString(2);
                    UUID request = (UUID) r.getObject(3);
                    List<PurchaseItem> items = new ArrayList<>();
                    Map<Long, String> names = new HashMap<>();
                    try (PreparedStatement l =
                            c.prepareStatement(
                                    "select l.*,p.name from purchase_draft_line l join product p on p.id=l.product_id where actor_id=? order by line_no")) {
                        l.setLong(1, user.id());
                        try (ResultSet rows = l.executeQuery()) {
                            while (rows.next()) {
                                long id = rows.getLong("product_id");
                                items.add(
                                        new PurchaseItem(
                                                id,
                                                rows.getString("lot"),
                                                rows.getDate("expires_on").toLocalDate(),
                                                rows.getInt("quantity"),
                                                rows.getBigDecimal("cost")));
                                names.put(id, rows.getString("name"));
                            }
                        }
                    }
                    return Optional.of(
                            new Draft(
                                    supplier,
                                    name,
                                    request,
                                    List.copyOf(items),
                                    Map.copyOf(names)));
                }
            }
        }
    }

    public void save(Security.User user, Long supplier, UUID request, List<PurchaseItem> items)
            throws SQLException {
        user.admin();
        Set<String> lots = new HashSet<>();
        for (var line : items) {
            if (line.productId() < 1
                    || line.lot() == null
                    || line.lot().isBlank()
                    || line.expiry() == null
                    || line.quantity() < 1
                    || line.cost() == null
                    || line.cost().signum() < 0) {
                throw new IllegalArgumentException("Línea de borrador inválida");
            }
            line.cost().setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (!lots.add(line.productId() + "/" + line.lot())) {
                throw new IllegalArgumentException("Lote repetido en el borrador");
            }
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, user, true);
                try (PreparedStatement q =
                        c.prepareStatement(
                                "insert into purchase_draft(actor_id,supplier_id,request_id) values(?,?,?) on conflict(actor_id) do update set supplier_id=excluded.supplier_id,request_id=excluded.request_id,updated_at=now()")) {
                    q.setLong(1, user.id());
                    q.setObject(2, supplier);
                    q.setObject(3, request);
                    q.executeUpdate();
                }
                try (PreparedStatement q =
                        c.prepareStatement("delete from purchase_draft_line where actor_id=?")) {
                    q.setLong(1, user.id());
                    q.executeUpdate();
                }
                try (PreparedStatement q =
                        c.prepareStatement(
                                "insert into purchase_draft_line(actor_id,line_no,product_id,lot,expires_on,quantity,cost) values(?,?,?,?,?,?,?)")) {
                    int i = 0;
                    for (var line : items) {
                        q.setLong(1, user.id());
                        q.setInt(2, i++);
                        q.setLong(3, line.productId());
                        q.setString(4, line.lot());
                        q.setDate(5, java.sql.Date.valueOf(line.expiry()));
                        q.setInt(6, line.quantity());
                        q.setBigDecimal(7, line.cost());
                        q.addBatch();
                    }
                    q.executeBatch();
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    public void clear(Security.User user) throws SQLException {
        try (Connection c = db.connection()) {
            Security.verify(c, user, true);
            try (PreparedStatement q =
                    c.prepareStatement("delete from purchase_draft where actor_id=?")) {
                q.setLong(1, user.id());
                q.executeUpdate();
            }
        }
    }
}
