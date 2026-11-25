package org.sigf.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
        saveContact(u, kind, id, name, phone, active);
    }

    public long createSupplier(Security.User user, String name, String phone) throws SQLException {
        return saveContact(user, "supplier", null, name, phone, true);
    }

    private long saveContact(
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
            try (PreparedStatement s = c.prepareStatement(sql + " returning id")) {
                Security.verify(c, u, true);
                s.setString(1, name.trim());
                s.setString(2, Objects.toString(phone, "").trim());
                int i = 3;
                if (kind.equals("supplier")) {
                    s.setBoolean(i++, active);
                }
                if (id != null) {
                    s.setLong(i, id);
                }
                long savedId;
                try (ResultSet result = s.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Registro inexistente");
                    }
                    savedId = result.getLong(1);
                }
                AuditRepository.write(
                        c,
                        u,
                        "CONTACT_" + kind,
                        (id == null ? "nuevo" : id.toString()) + ": " + name);
                c.commit();
                return savedId;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void customer(Security.User u, Long id, String name, String phone, String ruc)
            throws SQLException {
        saveCustomer(u, id, name, phone, ruc);
    }

    public long createCustomer(Security.User user, String name, String phone, String ruc)
            throws SQLException {
        return saveCustomer(user, null, name, phone, ruc);
    }

    private long saveCustomer(Security.User u, Long id, String name, String phone, String ruc)
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
                long savedId;
                try (PreparedStatement q = c.prepareStatement(sql + " returning id")) {
                    q.setString(1, name.trim());
                    q.setString(2, Objects.toString(phone, "").trim());
                    q.setString(3, cleanRuc);
                    if (id != null) {
                        q.setLong(4, id);
                    }
                    try (ResultSet result = q.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Cliente inexistente");
                        }
                        savedId = result.getLong(1);
                    }
                }
                AuditRepository.write(c, u, "CUSTOMER_CHANGE", name.trim());
                c.commit();
                return savedId;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public record Match(long id, String name, String phone, String ruc) {
        @Override
        public String toString() {
            return name
                    + (phone.isBlank() ? "" : " · " + phone)
                    + (ruc.isBlank() ? "" : " · " + ruc);
        }
    }

    /** Coincidencias orientativas; los nombres iguales por sí solos no son duplicados. */
    public List<Match> matches(Security.User user, String kind, String phone, String ruc)
            throws SQLException {
        user.admin();
        if (!List.of("customer", "supplier").contains(kind)) {
            throw new IllegalArgumentException("Tipo inválido");
        }
        String number = Objects.toString(phone, "").replaceAll("[^0-9]", "");
        String taxId = Objects.toString(ruc, "").trim().toUpperCase(java.util.Locale.ROOT);
        String sql =
                kind.equals("customer")
                        ? "select id,name,coalesce(phone,''),coalesce(ruc,'') from customer where (?<>'' and regexp_replace(coalesce(phone,''),'[^0-9]','','g')=?) or (?<>'' and upper(trim(coalesce(ruc,'')))=?) order by id limit 10"
                        : "select id,name,coalesce(phone,''),'' from supplier where active and ?<>'' and regexp_replace(coalesce(phone,''),'[^0-9]','','g')=? order by id limit 10";
        try (Connection c = db.connection()) {
            Security.verify(c, user, true);
            List<Match> matches = new ArrayList<>();
            try (PreparedStatement query = c.prepareStatement(sql)) {
                query.setString(1, number);
                query.setString(2, number);
                if (kind.equals("customer")) {
                    query.setString(3, taxId);
                    query.setString(4, taxId);
                }
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        matches.add(
                                new Match(
                                        rows.getLong(1),
                                        rows.getString(2),
                                        rows.getString(3),
                                        rows.getString(4)));
                    }
                }
            }
            return List.copyOf(matches);
        }
    }
}
