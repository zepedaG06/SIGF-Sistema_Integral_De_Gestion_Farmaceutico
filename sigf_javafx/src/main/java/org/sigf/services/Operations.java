package org.sigf.services;
import org.sigf.app.*; import org.sigf.config.*; import org.sigf.security.*; import org.sigf.services.*; import org.sigf.ui.*; import org.sigf.domain.*;


import java.sql.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

/** Transactional application operations. Queries are selected from a fixed allowlist. */
public final class Operations {
 private final Database db;
 public Operations(Database db){this.db=db;}
 public ReportGrid page(String name,int offset,int limit)throws SQLException{return new org.sigf.repositories.ReportRepository(db).page(name,null,null,offset,limit);}
 public ReportGrid grid(String name)throws SQLException{return new org.sigf.repositories.ReportRepository(db).grid(name);}
 public ReportGrid reportPage(String name,LocalDate from,LocalDate to,int offset,int limit)throws SQLException{return new org.sigf.repositories.ReportRepository(db).reportPage(name,from,to,offset,limit);}
 public ReportGrid report(String name,LocalDate from,LocalDate to)throws SQLException{return new org.sigf.repositories.ReportRepository(db).report(name,from,to);}
 public void contact(Security.User u,String kind,Long id,String name,String phone,boolean active)throws SQLException{new ContactsService(db).contact(u,kind,id,name,phone,active);}
 public void customer(Security.User u,Long id,String name,String phone,String ruc)throws SQLException{new ContactsService(db).customer(u,id,name,phone,ruc);}
 public void internalSeries(Security.User u,String code,long next)throws SQLException{new AdministrationService(db).internalSeries(u,code,next);}
 public void user(Security.User u,Long id,String name,String pass,String role,boolean active)throws SQLException{new AdministrationService(db).user(u,id,name,pass,role,active);}
 public void setting(Security.User u,String key,String value)throws SQLException{new AdministrationService(db).setting(u,key,value);}
 public void adjust(Security.User u,long batch,int delta,String reason)throws SQLException{new StockService(db).adjust(u,batch,delta,reason);}
 public void block(Security.User u,long batch,boolean block)throws SQLException{new StockService(db).block(u,batch,block);}
 public void voidSale(Security.User u,long sale,String reason)throws SQLException{new SalesService(db).voidSale(u,sale,reason);}
 public String receipt(long sale)throws SQLException{return new SalesService(db).receipt(sale);}
 public long purchase(Security.User u,long supplier,List<PurchaseItem> items)throws SQLException{return new PurchaseService(db).purchase(u,supplier,items);}
 public long purchase(Security.User u,Long supplier,List<PurchaseItem> items,UUID request)throws SQLException{return new PurchaseService(db).purchase(u,supplier,items,request);}
 public Receipt quote(List<CartItem> items)throws SQLException{return new SalesService(db).quote(items);}
 public SaleQuote quoteDetailed(List<CartItem> items)throws SQLException{return new SalesService(db).quoteDetailed(items);}
 public Receipt sell(Security.User u,List<CartItem> items,Long customer,String method,UUID request)throws SQLException{return new SalesService(db).sell(u,items,customer,method,request);}
 public Receipt sell(Security.User u,List<CartItem> items,Long customer,String method,UUID request,BigDecimal total)throws SQLException{return new SalesService(db).sell(u,items,customer,method,request,total);}
 public Receipt sell(Security.User u,List<CartItem> items,Long customer,String method,UUID request,BigDecimal total,List<QuoteLine> lines)throws SQLException{return new SalesService(db).sell(u,items,customer,method,request,total,lines);}
}
