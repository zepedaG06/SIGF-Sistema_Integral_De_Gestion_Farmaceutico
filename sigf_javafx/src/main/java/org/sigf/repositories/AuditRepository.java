package org.sigf.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.sigf.security.Security;

public final class AuditRepository {
    private AuditRepository() {}

    public static void write(Connection c, Security.User u, String action, String details)
            throws SQLException {
        try (PreparedStatement s =
                c.prepareStatement(
                        "insert into audit_log(actor_id,action,details) values(?,?,?)")) {
            s.setLong(1, u.id());
            s.setString(2, action);
            s.setString(3, details);
            s.executeUpdate();
        }
    }
}
