package org.sigf;
import java.util.UUID;import org.sigf.config.Database;import org.sigf.security.Security;import org.sigf.services.PurchaseRecovery;
/** Separate process used only against a disposable test database. */
public final class PurchaseRecoveryProbe {public static void main(String[] args)throws Exception{try(Database db=new Database()){var user=new Security(db).login(args[0],args[1]);long id=new PurchaseRecovery(db).resolve(user,UUID.fromString(args[2]));System.out.println("RECOVERED_PURCHASE="+id);}}}
