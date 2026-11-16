package org.sigf.app;
import org.sigf.app.*; import org.sigf.config.*; import org.sigf.security.*; import org.sigf.services.*; import org.sigf.ui.*; import org.sigf.domain.*;

/** Applies the versioned migrations using the connection configured in environment variables. */
public final class InitializeDatabase {
 private InitializeDatabase() {}
 public static void main(String[] args){try(Database db=new Database()){System.out.println("Migraciones aplicadas a sigf_javafx_db.");}}
}
