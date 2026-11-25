package org.sigf.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.sigf.config.Database;
import org.sigf.repositories.AuditRepository;
import org.sigf.repositories.StockMovementRepository;
import org.sigf.security.Security;

public final class StockService {
    private final Database db;

    public StockService(Database db) {
        this.db = db;
    }

    private static void required(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Complete los campos obligatorios");
        }
    }

    public void adjust(Security.User u, long batch, int delta, String reason) throws SQLException {
        u.admin();
        required(reason);
        if (delta == 0) {
            throw new IllegalArgumentException("El ajuste no puede ser cero");
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, true);
                try (PreparedStatement s =
                        c.prepareStatement(
                                "update batch set quantity=quantity+? where id=? and quantity+?>=0")) {
                    s.setInt(1, delta);
                    s.setLong(2, batch);
                    s.setInt(3, delta);
                    if (s.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Lote inexistente o ajuste dejaría existencias negativas");
                    }
                }
                StockMovementRepository.write(c, batch, delta, "ADJUSTMENT", null, u.id());
                AuditRepository.write(c, u, "STOCK_ADJUSTMENT", "Lote " + batch + ": " + reason);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void block(Security.User u, long batch, boolean block) throws SQLException {
        u.admin();
        block(u, batch, block, "Bloqueo administrativo");
    }

    public void block(Security.User u, long batch, boolean block, String reason)
            throws SQLException {
        var permission =
                block
                        ? org.sigf.security.Permission.BLOCK_BATCH
                        : org.sigf.security.Permission.ADMINISTER;
        u.require(permission);
        required(reason);
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s =
                    c.prepareStatement("update batch set blocked=? where id=?")) {
                Security.verify(c, u, permission);
                s.setBoolean(1, block);
                s.setLong(2, batch);
                if (s.executeUpdate() != 1) {
                    throw new IllegalArgumentException("Lote inexistente");
                }
                AuditRepository.write(
                        c, u, "BATCH_BLOCK", batch + "=" + block + ": " + reason.trim());
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
