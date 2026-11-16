package org.sigf;
import org.sigf.app.*; import org.sigf.config.*; import org.sigf.security.*; import org.sigf.services.*; import org.sigf.ui.*; import org.sigf.domain.*;

import java.util.UUID;
/** Executed as a separate process against the disposable integration database. */
public final class RecoveryProbe {public static void main(String[] args)throws Exception{try(Database db=new Database()){Security.User user=new Security(db).login(args[0],args[1]);var receipt=new SaleRecovery(db).resolve(user,UUID.fromString(args[2]));System.out.println("RECOVERED_ID="+receipt.id());}}}
