package org.sigf;

import java.util.UUID;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.SaleRecovery;

/** Executed as a separate process against the disposable integration database. */
public final class RecoveryProbe {
    public static void main(String[] args) throws Exception {
        try (Database db = new Database()) {
            Security.User user = new Security(db).login(args[0], args[1]);
            var receipt = new SaleRecovery(db).resolve(user, UUID.fromString(args[2]));
            System.out.println("RECOVERED_ID=" + receipt.id());
        }
    }
}
