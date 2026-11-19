package org.sigf.services;

import org.sigf.config.Database;
import org.sigf.security.Security;
import java.sql.*;

/** Restablecimiento transaccional limitado a tablas de SIGF. */
public final class ResetService {
 private final Database db;
 public ResetService(Database db){this.db=db;}
 public void reset(Security.User user)throws SQLException {
  user.admin();try(Connection c=db.connection()){
   c.setAutoCommit(false);
   try {
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("select current_database()")){if(!r.next()||!"sigf_javafx_db".equals(r.getString(1)))throw new SecurityException("Base de datos incorrecta");}
    Security.verify(c,user,true);
    try(Statement s=c.createStatement()){
     s.execute("TRUNCATE TABLE public.sale_request_line, public.sale_request, public.purchase_request_line, public.purchase_request, public.sale_receipt_snapshot, public.internal_document, public.payment, public.sale_line, public.stock_movement, public.purchase_line, public.sale, public.purchase, public.batch, public.product, public.product_catalog, public.supplier, public.customer, public.pharmacy_profile, public.audit_log, public.app_user, public.internal_series, public.setting");
     s.execute("INSERT INTO public.setting(key,value) VALUES ('pharmacy_name','Mi farmacia'),('currency','NIO'),('tax_rate','0'),('business_details','Sin datos comerciales'),('expiry_days','30')");
     s.execute("INSERT INTO public.internal_series(id,code,next_number) VALUES (1,'DEMO',1)");
    }
    c.commit();
   } catch(Exception e){c.rollback();throw e;}
  }
 }
}
