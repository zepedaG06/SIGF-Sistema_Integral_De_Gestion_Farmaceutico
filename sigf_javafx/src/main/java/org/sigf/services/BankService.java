package org.sigf.services;

import java.sql.*;
import java.util.*;
import org.sigf.config.Database;
import org.sigf.repositories.AuditRepository;
import org.sigf.security.Security;

public final class BankService {
    private final Database db;

    public BankService(Database db) {
        this.db = db;
    }

    public List<String> list(Security.User user, boolean all) throws SQLException {
        try (var c = db.connection()) {
            Security.verify(c, user, false);
            var result = new ArrayList<String>();
            try (var q =
                    c.prepareStatement("select name from bank where (? or active) order by name")) {
                q.setBoolean(1, all);
                try (var r = q.executeQuery()) {
                    while (r.next()) result.add(r.getString(1));
                }
            }
            return result;
        }
    }

    public void save(Security.User user, String name, boolean active) throws SQLException {
        user.admin();
        name = name.trim().toUpperCase(java.util.Locale.ROOT);
        if (name.isEmpty() || name.length() > 100)
            throw new IllegalArgumentException("Nombre de banco inválido");
        try (var c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, user, true);
                try (var q =
                        c.prepareStatement(
                                "insert into bank(name,active) values(?,?) on conflict(name) do update set active=excluded.active")) {
                    q.setString(1, name);
                    q.setBoolean(2, active);
                    q.executeUpdate();
                }
                AuditRepository.write(c, user, "BANK", name + " activo=" + active);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean duplicate(Security.User user, String bank, String reference)
            throws SQLException {
        try (var c = db.connection()) {
            Security.verify(c, user, org.sigf.security.Permission.SELL);
            try (var q =
                    c.prepareStatement(
                            "select exists(select 1 from payment where bank_name=? and transfer_reference=?)")) {
                q.setString(1, bank);
                q.setString(2, reference);
                try (var r = q.executeQuery()) {
                    r.next();
                    return r.getBoolean(1);
                }
            }
        }
    }
}
