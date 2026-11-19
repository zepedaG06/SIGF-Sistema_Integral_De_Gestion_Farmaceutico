package org.sigf.app;

import org.sigf.config.Database;
import org.sigf.config.SecureConnectionStore;
import org.sigf.security.Security;
import org.sigf.services.ResetService;
import java.io.Console;
import java.util.Arrays;

/** Herramienta interactiva; no admite modo automático. */
public final class ResetSigf {
 private ResetSigf() {}
 public static void main(String[] args) {
  Console console=System.console();
  if(console==null){System.err.println("Se requiere una terminal interactiva para restablecer SIGF.");System.exit(1);return;}
  try {
   SecureConnectionStore store=new SecureConnectionStore();SecureConnectionStore.ConnectionInfo saved=null;
   try{saved=store.load().orElse(null);}catch(Exception e){console.printf("No se pudo leer la conexión guardada; ingrese los datos manualmente.%n");}
   String url,user,password;
   if(saved!=null){url=saved.url();user=saved.user();password=saved.password();}
   else {String host=console.readLine("Servidor PostgreSQL [127.0.0.1]: ");if(host.isBlank())host="127.0.0.1";String port=console.readLine("Puerto [5432]: ");if(port.isBlank())port="5432";if(!host.matches("[A-Za-z0-9.-]+")||!port.matches("[0-9]{1,5}")||Integer.parseInt(port)<1||Integer.parseInt(port)>65535)throw new IllegalArgumentException("Servidor o puerto inválido");url="jdbc:postgresql://"+host+":"+port+"/sigf_javafx_db";user=console.readLine("Usuario PostgreSQL: ");password=readPassword(console,"Contraseña PostgreSQL: ");}
   console.printf("%nATENCIÓN: se eliminarán productos, usuarios, ventas, compras, inventario, configuración y auditoría de %s.%n",url);
   console.printf("Cierre SIGF en todos los equipos antes de continuar. El esquema permanece; los datos no se podrán recuperar sin respaldo.%n");
   if(!"BORRAR SIGF".equals(console.readLine("Escriba BORRAR SIGF para continuar: "))){console.printf("Cancelado. No se borraron datos.%n");return;}
   String admin=console.readLine("Usuario administrador de SIGF: ");String adminPassword=readPassword(console,"Contraseña del administrador de SIGF: ");
   try(Database db=new Database(url,user,password)) {Security.User actor=new Security(db).login(admin,adminPassword);actor.admin();new ResetService(db).reset(actor);}
   try{store.clear();console.printf("Datos borrados y conexión guardada eliminada. Al abrir SIGF comenzará el setup.%n");}catch(Exception e){console.printf("Datos borrados, pero no se pudo eliminar la conexión guardada. SIGF solicitará crear el administrador al iniciar.%n");}
  }catch(Exception e){console.printf("No se completó el restablecimiento: %s%n",e.getMessage());System.exit(1);}
 }
 private static String readPassword(Console console,String prompt){char[] input=console.readPassword("%s",prompt);if(input==null)throw new IllegalStateException("No se pudo leer la contraseña");try{return new String(input);}finally{Arrays.fill(input,'\0');}}
}
