package org.sigf.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import org.mindrot.jbcrypt.BCrypt;
import org.sigf.config.Database;

public final class Security {
    public record User(long id, String name, String role, java.util.UUID version) {
        public User(long id, String name, String role) {
            this(id, name, role, null);
        }

        public void admin() {
            if (!role.equals("ADMIN")) {
                throw new SecurityException("Operación reservada a administración");
            }
        }

        public boolean can(Permission permission) {
            return Permissions.allows(role, permission);
        }

        public void require(Permission permission) {
            if (!can(permission)) {
                throw new SecurityException("Su rol no permite esta operación");
            }
        }
    }

    public static void verify(Connection c, User user, boolean admin) throws SQLException {
        try (PreparedStatement s =
                c.prepareStatement(
                        "select role,active,session_version from app_user where id=? for share")) {
            s.setLong(1, user.id());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()
                        || !r.getBoolean(2)
                        || !java.util.Objects.equals(user.version(), r.getObject(3))
                        || !user.role().equals(r.getString(1))
                        || (admin && !r.getString(1).equals("ADMIN"))) {
                    throw new SecurityException("La sesión ya no tiene autorización");
                }
            }
        }
    }

    public static void verify(Connection connection, User user, Permission permission)
            throws SQLException {
        verify(connection, user, false);
        user.require(permission);
    }

    public static void verifyReport(Connection connection, User user, String name)
            throws SQLException {
        verify(connection, user, false);
        if (!Permissions.canReadReport(user.role(), name)) {
            throw new SecurityException("Su rol no permite consultar este reporte");
        }
    }

    private final Database db;

    public Security(Database db) {
        this.db = db;
    }

    private static final String DUMMY_HASH =
            BCrypt.hashpw("sigf-nonexistent-account", BCrypt.gensalt(12));

    public static void validateNewPassword(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "La contraseña es demasiado larga; use una más corta");
        }
    }

    public User login(String name, String password) throws SQLException {
        if (name == null || password == null) {
            throw new SecurityException("Credenciales inválidas");
        }
        User result = null;
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s =
                    c.prepareStatement(
                            "select id,username,role,password_hash,failed_attempts,locked_until,active,session_version from app_user where username=? for update")) {
                s.setString(1, name);
                try (ResultSet r = s.executeQuery()) {
                    if (r.next()) {
                        long id = r.getLong(1);
                        String username = r.getString(2),
                                role = r.getString(3),
                                hash = r.getString(4);
                        int failures = r.getInt(5);
                        Timestamp locked = r.getTimestamp(6);
                        boolean active = r.getBoolean(7);
                        boolean valid = BCrypt.checkpw(password, hash);
                        boolean blocked =
                                locked != null
                                        && locked.toInstant().isAfter(java.time.Instant.now());
                        if (active && !blocked && valid) {
                            try (PreparedStatement q =
                                    c.prepareStatement(
                                            "update app_user set failed_attempts=0,locked_until=null where id=?")) {
                                q.setLong(1, id);
                                q.executeUpdate();
                            }
                            result = new User(id, username, role, (java.util.UUID) r.getObject(8));
                        } else if (active && !blocked) {
                            int next = failures + 1;
                            try (PreparedStatement q =
                                    c.prepareStatement(
                                            "update app_user set failed_attempts=?,locked_until=case when ?>=5 then now()+interval '15 minutes' else null end where id=?")) {
                                q.setInt(1, next >= 5 ? 0 : next);
                                q.setInt(2, next);
                                q.setLong(3, id);
                                q.executeUpdate();
                            }
                        }
                    } else {
                        BCrypt.checkpw(password, DUMMY_HASH);
                    }
                }
            }
            c.commit();
        }
        if (result == null) {
            throw new SecurityException("Credenciales inválidas o acceso temporalmente bloqueado");
        }
        return result;
    }

    public boolean hasUsers() throws SQLException {
        try (Connection c = db.connection();
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("select exists(select 1 from app_user)")) {
            r.next();
            return r.getBoolean(1);
        }
    }

    public void bootstrap(String name, String password) throws SQLException {
        validateNewPassword(password);
        if (name == null || name.isBlank() || password == null || password.length() < 12) {
            throw new IllegalArgumentException(
                    "Se requieren usuario y contraseña de al menos 12 caracteres");
        }
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (Statement lock = c.createStatement()) {
                lock.execute("LOCK TABLE app_user IN EXCLUSIVE MODE");
            }
            try (Statement q = c.createStatement();
                    ResultSet r = q.executeQuery("select count(*) from app_user")) {
                r.next();
                if (r.getLong(1) > 0) {
                    throw new SecurityException("La inicialización ya se realizó");
                }
            }
            try (PreparedStatement s =
                    c.prepareStatement(
                            "insert into app_user(username,password_hash,role) values (?,?,'ADMIN')")) {
                s.setString(1, name);
                s.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(12)));
                s.executeUpdate();
            }
            c.commit();
        }
    }
}
