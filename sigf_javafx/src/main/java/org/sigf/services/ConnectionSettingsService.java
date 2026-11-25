package org.sigf.services;

import org.sigf.config.Database;
import org.sigf.config.SecureConnectionStore;
import org.sigf.security.Security;

/** Cambia la conexión del siguiente inicio sin sustituir la sesión en curso. */
public final class ConnectionSettingsService {
    private final Database db;

    public ConnectionSettingsService(Database db) {
        this.db = db;
    }

    public String status() throws java.sql.SQLException {
        try (var c = db.connection();
                var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "select current_database(),inet_server_addr(),inet_server_port()")) {
            r.next();
            return r.getString(1) + " · " + r.getString(2) + ":" + r.getInt(3);
        }
    }

    public void save(Security.User user, String host, int port, String account, String password)
            throws Exception {
        try (var c = db.connection()) {
            Security.verify(c, user, true);
        }
        if (!host.matches("[A-Za-z0-9.-]+") || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Servidor o puerto inválido");
        }
        String url = "jdbc:postgresql://" + host + ":" + port + "/sigf_javafx_db";
        try (Database check = new Database(url, account, password)) {
            new SecureConnectionStore().save(url, account, password);
        }
    }
}
