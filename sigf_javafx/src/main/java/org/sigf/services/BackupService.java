package org.sigf.services;
import java.nio.file.Path;import java.sql.Connection;import org.sigf.config.Database;import org.sigf.security.Security;
/** Starts a PostgreSQL custom-format backup for an active administrator. */
public final class BackupService {private final Database db;public BackupService(Database db){this.db=db;}public void create(Security.User user,Path pgDump,Path destination)throws Exception{user.admin();try(Connection c=db.connection()){Security.verify(c,user,true);}db.createBackup(pgDump,destination);}}
