package org.sigf.config;
import org.sigf.app.*; import org.sigf.config.*; import org.sigf.security.*; import org.sigf.services.*; import org.sigf.ui.*; import org.sigf.domain.*;

import com.zaxxer.hikari.*;
import org.flywaydb.core.Flyway;
import java.sql.*;
public final class Database implements AutoCloseable {
 private final HikariDataSource pool;
 public Database() {
  this(required("SIGF_DB_URL"),required("SIGF_DB_USER"),required("SIGF_DB_PASSWORD"));
 }
 public Database(String url,String user,String password) {
  if(user==null||user.isBlank()||password==null||password.isEmpty()) throw new IllegalArgumentException("Ingrese usuario y contraseña PostgreSQL");
  if (!url.matches("jdbc:postgresql://[^/]+/sigf_javafx_db(?:[?].*)?")) throw new IllegalArgumentException("La URL debe apuntar exclusivamente a sigf_javafx_db");
  HikariConfig c=new HikariConfig(); c.setJdbcUrl(url); c.setUsername(user); c.setPassword(password); c.setMaximumPoolSize(5); c.setConnectionInitSql("SET statement_timeout=30000; SET lock_timeout=10000"); c.setConnectionTimeout(5000); pool=new HikariDataSource(c);
  try {Flyway.configure().dataSource(pool).load().migrate();} catch(RuntimeException ex){pool.close();throw ex;}
 }
 private static String required(String key) { String v=System.getenv(key); if(v==null||v.isBlank()) throw new IllegalArgumentException("Falta la variable "+key); return v; }
 public void createBackup(java.nio.file.Path executable,java.nio.file.Path destination) throws Exception {if(!java.nio.file.Files.isRegularFile(executable))throw new IllegalArgumentException("Seleccione pg_dump.exe");String binary=executable.getFileName().toString();if(!binary.equalsIgnoreCase("pg_dump.exe")&&!binary.equals("pg_dump"))throw new IllegalArgumentException("Seleccione pg_dump de PostgreSQL");java.nio.file.Path target=destination.toAbsolutePath().normalize();if(java.nio.file.Files.exists(target))throw new IllegalArgumentException("El archivo ya existe; elija otro nombre");java.nio.file.Path parent=target.getParent();if(parent==null||!java.nio.file.Files.isDirectory(parent))throw new IllegalArgumentException("La carpeta de destino no existe");java.net.URI uri=java.net.URI.create(pool.getJdbcUrl().substring("jdbc:".length()));if(!"/sigf_javafx_db".equals(uri.getPath())||uri.getHost()==null)throw new IllegalArgumentException("Conexión de base inválida");java.nio.file.Path temp=java.nio.file.Files.createTempFile(parent,"sigf-backup-",".tmp");boolean moved=false;try{ProcessBuilder pb=new ProcessBuilder(executable.toAbsolutePath().toString(),"--host",uri.getHost(),"--port",Integer.toString(uri.getPort()<0?5432:uri.getPort()),"--username",pool.getUsername(),"--no-password","--format=custom","--no-owner","--no-privileges","--file",temp.toString(),"--dbname","sigf_javafx_db");pb.environment().put("PGPASSWORD",pool.getPassword());pb.redirectError(ProcessBuilder.Redirect.DISCARD);pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);Process process=pb.start();if(!process.waitFor(10,java.util.concurrent.TimeUnit.MINUTES)){process.destroyForcibly();throw new IllegalStateException("El respaldo excedió el tiempo permitido");}if(process.exitValue()!=0||java.nio.file.Files.size(temp)==0)throw new IllegalStateException("pg_dump no pudo generar el respaldo (código "+process.exitValue()+"). Verifique versión y permisos.");java.nio.file.Files.move(temp,target);moved=true;}finally{if(!moved)java.nio.file.Files.deleteIfExists(temp);}}
 public Connection connection() throws SQLException { return pool.getConnection(); }
 public void close(){pool.close();}
}