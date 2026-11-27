package org.sigf.services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.sigf.config.Database;
import org.sigf.domain.CartItem;
import org.sigf.domain.QuoteLine;
import org.sigf.domain.Receipt;
import org.sigf.security.Security;

/** Durable outbox: an operation is stored before a sale is attempted. */
public final class SaleRecovery {
    private final Database db;
    private final Operations ops;

    public SaleRecovery(Database db) {
        this.db = db;
        this.ops = new Operations(db);
    }

    public record Pending(
            UUID id,
            Long customer,
            String method,
            BigDecimal total,
            List<CartItem> items,
            List<QuoteLine> quoteLines,
            BigDecimal received,
            org.sigf.domain.TransferDetails transfer) {
        public Pending(
                UUID id,
                Long customer,
                String method,
                BigDecimal total,
                List<CartItem> items,
                List<QuoteLine> lines,
                BigDecimal received) {
            this(id, customer, method, total, items, lines, received, null);
        }

        public Pending(
                UUID id,
                Long customer,
                String method,
                BigDecimal total,
                List<CartItem> items,
                List<QuoteLine> lines) {
            this(id, customer, method, total, items, lines, null);
        }

        public Pending(
                UUID id, Long customer, String method, BigDecimal total, List<CartItem> items) {
            this(id, customer, method, total, items, List.of(), null);
        }
    }

    private static org.sigf.domain.TransferDetails transfer(ResultSet r, int start)
            throws SQLException {
        return r.getString(start) == null
                ? null
                : new org.sigf.domain.TransferDetails(
                        r.getString(start), r.getString(start + 1), r.getString(start + 2));
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public void save(Security.User u, Pending p) throws SQLException {
        u.require(org.sigf.security.Permission.SELL);
        if (p.method() == null
                || !java.util.Set.of("CASH", "CARD", "TRANSFER").contains(p.method()))
            throw new IllegalArgumentException("Forma de pago inválida");
        if (p.transfer() != null && !p.method().equals("TRANSFER"))
            throw new IllegalArgumentException("Datos de transferencia en otro medio de pago");
        if (p.items().isEmpty()) {
            throw new IllegalArgumentException("Carrito vacío");
        }
        TreeMap<Long, Integer> items = new TreeMap<>();
        for (var item : p.items()) {
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Cantidad inválida");
            }
            items.merge(item.productId(), item.quantity(), Math::addExact);
        }
        List<QuoteLine> quoted =
                p.quoteLines().isEmpty() ? ops.quoteDetailed(p.items()).lines() : p.quoteLines();
        Map<Long, QuoteLine> quotedById = new HashMap<>();
        BigDecimal expected = BigDecimal.ZERO;
        for (QuoteLine line : quoted) {
            if (line.price() == null
                    || line.taxRate() == null
                    || line.price().signum() < 0
                    || line.taxRate().signum() < 0
                    || line.taxRate().compareTo(BigDecimal.ONE) > 0
                    || line.quantity() <= 0
                    || quotedById.put(line.productId(), line) != null
                    || !Objects.equals(items.get(line.productId()), line.quantity())) {
                throw new IllegalArgumentException("Detalle de cotización inválido");
            }
            BigDecimal sub = line.price().multiply(BigDecimal.valueOf(line.quantity()));
            expected =
                    expected.add(sub)
                            .add(
                                    sub.multiply(line.taxRate())
                                            .setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (quotedById.size() != items.size()
                || p.total() == null
                || expected.compareTo(p.total()) != 0) {
            throw new IllegalArgumentException("Cambió el importe; vuelva a cotizar");
        }
        if (p.received() != null
                && (!p.method().equals("CASH") || p.received().compareTo(p.total()) < 0)) {
            throw new IllegalArgumentException("El efectivo recibido no cubre el total");
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, org.sigf.security.Permission.SELL);
                try (PreparedStatement lock =
                        c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    lock.setString(1, "sigf_pending:" + u.id());
                    lock.execute();
                }
                try (PreparedStatement q =
                        c.prepareStatement(
                                "select actor_id,customer_id,method,expected_total,received_amount,acknowledged_at,exists(select 1 from sale where request_id=?),bank_name,transfer_reference,transfer_description from sale_request where request_id=?")) {
                    q.setObject(1, p.id());
                    q.setObject(2, p.id());
                    try (ResultSet r = q.executeQuery()) {
                        if (r.next()) {
                            if (r.getLong(1) != u.id()
                                    || !Objects.equals(r.getObject(2), p.customer())
                                    || !r.getString(3).equals(p.method())
                                    || r.getBigDecimal(4).compareTo(p.total()) != 0
                                    || (r.getTimestamp(6) != null && !r.getBoolean(7))
                                    || !sameMoney(r.getBigDecimal(5), p.received())
                                    || !java.util.Objects.equals(p.transfer(), transfer(r, 8))) {
                                throw new IllegalArgumentException(
                                        "La solicitud ya existe con otros datos o fue cancelada");
                            }
                            try (PreparedStatement l =
                                    c.prepareStatement(
                                            "select product_id,quantity,quoted_price,quoted_tax_rate from sale_request_line where request_id=?")) {
                                l.setObject(1, p.id());
                                int count = 0;
                                try (ResultSet lines = l.executeQuery()) {
                                    while (lines.next()) {
                                        count++;
                                        var expectedLine = quotedById.get(lines.getLong(1));
                                        if (expectedLine == null
                                                || expectedLine.quantity() != lines.getInt(2)
                                                || !sameMoney(
                                                        expectedLine.price(),
                                                        lines.getBigDecimal(3))
                                                || !sameMoney(
                                                        expectedLine.taxRate(),
                                                        lines.getBigDecimal(4))) {
                                            throw new IllegalArgumentException(
                                                    "La solicitud no coincide");
                                        }
                                    }
                                }
                                if (count != quoted.size()) {
                                    throw new IllegalArgumentException("La solicitud no coincide");
                                }
                            }
                            c.commit();
                            return;
                        }
                    }
                }
                try (PreparedStatement q =
                        c.prepareStatement(
                                "select 1 from sale_request where actor_id=? and acknowledged_at is null")) {
                    q.setLong(1, u.id());
                    try (ResultSet r = q.executeQuery()) {
                        if (r.next()) {
                            throw new IllegalArgumentException(
                                    "Tiene una venta pendiente; resuélvala antes de iniciar otra");
                        }
                    }
                }
                try (PreparedStatement s =
                        c.prepareStatement(
                                "insert into sale_request(request_id,actor_id,customer_id,method,expected_total,received_amount,bank_name,transfer_reference,transfer_description) values(?,?,?,?,?,?,?,?,?)")) {
                    s.setObject(1, p.id());
                    s.setLong(2, u.id());
                    if (p.customer() == null) {
                        s.setNull(3, Types.BIGINT);
                    } else {
                        s.setLong(3, p.customer());
                    }
                    s.setString(4, p.method());
                    s.setBigDecimal(5, p.total());
                    s.setBigDecimal(6, p.received());
                    if (p.method().equals("TRANSFER") && p.transfer() == null)
                        throw new IllegalArgumentException(
                                "Ingrese banco y referencia de transferencia");
                    if (p.transfer() != null) {
                        try (var bank =
                                c.prepareStatement(
                                        "select 1 from bank where name=? and active for share")) {
                            bank.setString(1, p.transfer().bank());
                            try (var r = bank.executeQuery()) {
                                if (!r.next())
                                    throw new IllegalArgumentException(
                                            "Banco inactivo o inexistente");
                            }
                        }
                    }
                    s.setString(7, p.transfer() == null ? null : p.transfer().bank());
                    s.setString(8, p.transfer() == null ? null : p.transfer().reference());
                    s.setString(9, p.transfer() == null ? null : p.transfer().description());
                    s.executeUpdate();
                }
                try (PreparedStatement s =
                        c.prepareStatement(
                                "insert into sale_request_line(request_id,product_id,quantity,quoted_price,quoted_tax_rate) values(?,?,?,?,?)")) {
                    for (var e : items.entrySet()) {
                        s.setObject(1, p.id());
                        s.setLong(2, e.getKey());
                        s.setInt(3, e.getValue());
                        s.setBigDecimal(4, quotedById.get(e.getKey()).price());
                        s.setBigDecimal(5, quotedById.get(e.getKey()).taxRate());
                        s.addBatch();
                    }
                    s.executeBatch();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<Pending> list(Security.User u) throws SQLException {
        List<Pending> out = new ArrayList<>();
        try (Connection c = db.connection()) {
            Security.verify(c, u, org.sigf.security.Permission.SELL);
            try (PreparedStatement s =
                    c.prepareStatement(
                            "select request_id,customer_id,method,expected_total,bank_name,transfer_reference,transfer_description from sale_request where (actor_id=? or "
                                    + u.role().equals("ADMIN")
                                    + ") and acknowledged_at is null order by created_at")) {
                s.setLong(1, u.id());
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        UUID id = (UUID) r.getObject(1);
                        Long customer = (Long) r.getObject(2);
                        List<CartItem> items = new ArrayList<>();
                        try (PreparedStatement l =
                                c.prepareStatement(
                                        "select product_id,quantity from sale_request_line where request_id=? order by product_id")) {
                            l.setObject(1, id);
                            try (ResultSet lr = l.executeQuery()) {
                                while (lr.next()) {
                                    items.add(new CartItem(lr.getLong(1), lr.getInt(2)));
                                }
                            }
                        }
                        out.add(
                                new Pending(
                                        id,
                                        customer,
                                        r.getString(3),
                                        r.getBigDecimal(4),
                                        List.copyOf(items),
                                        List.of(),
                                        null,
                                        transfer(r, 5)));
                    }
                }
            }
        }
        return out;
    }

    public Receipt resolve(Security.User u, UUID id) throws SQLException {
        Pending pending;
        try (Connection c = db.connection()) {
            Security.verify(c, u, org.sigf.security.Permission.SELL);
            try (PreparedStatement q =
                    c.prepareStatement(
                            "select customer_id,method,expected_total,acknowledged_at,exists(select 1 from sale where request_id=?),bank_name,transfer_reference,transfer_description from sale_request where request_id=? and (actor_id=? or "
                                    + u.role().equals("ADMIN")
                                    + ")")) {
                q.setObject(1, id);
                q.setObject(2, id);
                q.setLong(3, u.id());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next() || (r.getTimestamp(4) != null && !r.getBoolean(5))) {
                        throw new IllegalArgumentException(
                                "Solicitud pendiente inexistente o cancelada");
                    }
                    Long customer = (Long) r.getObject(1);
                    String method = r.getString(2);
                    BigDecimal total = r.getBigDecimal(3);
                    List<CartItem> items = new ArrayList<>();
                    List<QuoteLine> quoted = new ArrayList<>();
                    try (PreparedStatement lines =
                            c.prepareStatement(
                                    "select product_id,quantity,quoted_price,quoted_tax_rate from sale_request_line where request_id=? order by product_id")) {
                        lines.setObject(1, id);
                        try (ResultSet rows = lines.executeQuery()) {
                            while (rows.next()) {
                                items.add(new CartItem(rows.getLong(1), rows.getInt(2)));
                                quoted.add(
                                        new QuoteLine(
                                                rows.getLong(1),
                                                rows.getInt(2),
                                                rows.getBigDecimal(3),
                                                rows.getBigDecimal(4)));
                            }
                        }
                    }
                    pending =
                            new Pending(
                                    id,
                                    customer,
                                    method,
                                    total,
                                    items,
                                    quoted,
                                    null,
                                    transfer(r, 6));
                }
            }
        }
        return ops.sell(
                u,
                pending.items(),
                pending.customer(),
                pending.method(),
                id,
                pending.total(),
                pending.quoteLines());
    }

    public void cancelOrDiscard(Security.User u, UUID id) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, org.sigf.security.Permission.SELL);
                try (PreparedStatement l =
                        c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    l.setString(1, "sigf_pending:" + u.id());
                    l.execute();
                }
                try (PreparedStatement l =
                        c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    l.setString(1, id.toString());
                    l.execute();
                }
                try (PreparedStatement q =
                        c.prepareStatement(
                                "select exists(select 1 from sale where request_id=?)")) {
                    q.setObject(1, id);
                    try (ResultSet r = q.executeQuery()) {
                        r.next();
                        if (r.getBoolean(1)) {
                            throw new IllegalArgumentException(
                                    "La venta ya se registró; resuélvala en Pendientes");
                        }
                    }
                }
                try (PreparedStatement q =
                        c.prepareStatement(
                                "select actor_id from sale_request where request_id=?")) {
                    q.setObject(1, id);
                    try (ResultSet r = q.executeQuery()) {
                        if (r.next() && r.getLong(1) != u.id()) {
                            throw new SecurityException("Solicitud ajena");
                        }
                    }
                }
                try (PreparedStatement q =
                        c.prepareStatement(
                                "update sale_request set acknowledged_at=now() where request_id=? and (actor_id=? or "
                                        + u.role().equals("ADMIN")
                                        + ") and acknowledged_at is null")) {
                    q.setObject(1, id);
                    q.setLong(2, u.id());
                    q.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void acknowledge(Security.User u, UUID id, boolean cancel) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, org.sigf.security.Permission.SELL);
                try (PreparedStatement lock =
                        c.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")) {
                    lock.setString(1, id.toString());
                    lock.execute();
                }
                boolean sold;
                try (PreparedStatement s =
                        c.prepareStatement(
                                "select exists(select 1 from sale where request_id=?)")) {
                    s.setObject(1, id);
                    try (ResultSet r = s.executeQuery()) {
                        r.next();
                        sold = r.getBoolean(1);
                    }
                }
                if (cancel && sold) {
                    throw new IllegalArgumentException(
                            "La venta ya fue registrada; consulte el comprobante y resuelva la solicitud");
                }
                if (!cancel && !sold) {
                    throw new IllegalArgumentException("La venta aún no está registrada");
                }
                try (PreparedStatement s =
                        c.prepareStatement(
                                "update sale_request set acknowledged_at=now() where request_id=? and (actor_id=? or "
                                        + u.role().equals("ADMIN")
                                        + ") and acknowledged_at is null")) {
                    s.setObject(1, id);
                    s.setLong(2, u.id());
                    if (s.executeUpdate() != 1) {
                        try (PreparedStatement q =
                                c.prepareStatement(
                                        "select 1 from sale_request where request_id=? and (actor_id=? or "
                                                + u.role().equals("ADMIN")
                                                + ") and acknowledged_at is not null")) {
                            q.setObject(1, id);
                            q.setLong(2, u.id());
                            try (ResultSet r = q.executeQuery()) {
                                if (!r.next()) {
                                    throw new IllegalArgumentException(
                                            "Solicitud inexistente o ya resuelta");
                                }
                            }
                        }
                    }
                }
                org.sigf.repositories.AuditRepository.write(
                        c, u, "SALE_PENDING_RECONCILE", id + " cancel=" + cancel);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
