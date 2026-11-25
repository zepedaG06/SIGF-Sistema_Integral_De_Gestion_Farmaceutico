package org.sigf.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import org.sigf.config.Database;
import org.sigf.repositories.AuditRepository;
import org.sigf.security.Security;

public final class ContactsService {
    private final Database db;

    public ContactsService(Database db) {
        this.db = db;
    }

    private static void required(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Complete los campos obligatorios");
        }
    }

    public void contact(
            Security.User u, String kind, Long id, String name, String phone, boolean active)
            throws SQLException {
        u.admin();
        required(name);
        if (!kind.equals("supplier") && !kind.equals("customer")) {
            throw new IllegalArgumentException("Tipo inválido");
        }
        String sql =
                kind.equals("supplier")
                        ? (id == null
                                ? "insert into supplier(name,phone,active) values(?,?,?)"
                                : "update supplier set name=?,phone=?,active=? where id=?")
                        : (id == null
                                ? "insert into customer(name,phone) values(?,?)"
                                : "update customer set name=?,phone=? where id=?");
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement(sql)) {
                Security.verify(c, u, true);
                s.setString(1, name.trim());
                s.setString(2, phone.trim());
                int i = 3;
                if (kind.equals("supplier")) {
                    s.setBoolean(i++, active);
                }
                if (id != null) {
                    s.setLong(i, id);
                }
                if (s.executeUpdate() != 1) {
                    throw new IllegalArgumentException("Registro inexistente");
                }
                AuditRepository.write(
                        c,
                        u,
                        "CONTACT_" + kind,
                        (id == null ? "nuevo" : id.toString()) + ": " + name);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void customer(Security.User u, Long id, String name, String phone, String ruc)
            throws SQLException {
        u.admin();
        required(name);
        String cleanRuc = Objects.toString(ruc, "").trim();
        if (cleanRuc.length() > 30) {
            throw new IllegalArgumentException("RUC demasiado largo");
        }
        String sql =
                id == null
                        ? "insert into customer(name,phone,ruc) values(?,?,?)"
                        : "update customer set name=?,phone=?,ruc=? where id=?";
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                Security.verify(c, u, true);
                try (PreparedStatement q = c.prepareStatement(sql)) {
                    q.setString(1, name.trim());
                    q.setString(2, Objects.toString(phone, "").trim());
                    q.setString(3, cleanRuc);
                    if (id != null) {
                        q.setLong(4, id);
                    }
                    if (q.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Cliente inexistente");
                    }
                }
                AuditRepository.write(c, u, "CUSTOMER_CHANGE", name.trim());
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
