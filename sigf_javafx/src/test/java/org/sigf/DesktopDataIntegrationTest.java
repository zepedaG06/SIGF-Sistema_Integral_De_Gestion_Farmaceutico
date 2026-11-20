package org.sigf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.*;
import org.sigf.repositories.ReportRepository;
import org.sigf.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SIGF_DESKTOP_TEST",matches="true")
class DesktopDataIntegrationTest {
 @Test void filtersDraftsCashAndReportTotalsAreConsistent()throws Exception{
  assertTrue(System.getenv("SIGF_DB_URL").contains(":55439/"));
  try(Database db=new Database()){
   Security auth=new Security(db);auth.bootstrap("desktop_test","contraseñaPrueba123");var admin=auth.login("desktop_test","contraseñaPrueba123");InventoryService inventory=new InventoryService(db);Operations ops=new Operations(db);DesktopData data=new DesktopData(db);ReportRepository reports=new ReportRepository(db);
   for(int i=0;i<55;i++)inventory.saveDetails(admin,null,new InventoryService.ProductDetails("FILTER-"+i,"BAR-"+i,String.format("Artículo %02d",i),"Prueba","","Caja","Laboratorio",BigDecimal.ONE,BigDecimal.TEN,2,i!=54,BigDecimal.ZERO));
   var first=data.products("","Prueba","Laboratorio","Activo","Todas",0);assertEquals(51,first.size());assertEquals(4,data.products("","Prueba","Laboratorio","Activo","Todas",50).size());long product=inventory.products("FILTER-0").stream().filter(p->p.code().equals("FILTER-0")).findFirst().orElseThrow().id();assertEquals(product,data.product(product).orElseThrow().id());
   var low=data.products("FILTER-0","","","Activo","Stock bajo",0);assertEquals(1,low.size());assertEquals(0,low.getFirst().available());ops.contact(admin,"supplier",null,"Proveedor único","",true);long supplier=data.options("supplier","único").getFirst().id();
   PurchaseDraftService drafts=new PurchaseDraftService(db);List<PurchaseItem> lines=List.of(new PurchaseItem(product,"A",LocalDate.now().plusDays(7),2,BigDecimal.ONE),new PurchaseItem(product,"B",LocalDate.now().plusDays(90),3,new BigDecimal("2.00")));UUID request=UUID.randomUUID();drafts.save(admin,supplier,request,lines);var loaded=new PurchaseDraftService(db).load(admin).orElseThrow();assertEquals(2,loaded.items().size());assertEquals(request,loaded.request());assertEquals(supplier,loaded.supplier());
   PurchaseRecovery purchases=new PurchaseRecovery(db);purchases.save(admin,new PurchaseRecovery.Pending(request,supplier,lines));long purchase=purchases.resolve(admin,request);assertEquals(purchase,purchases.resolve(admin,request));purchases.acknowledge(admin,request,false);drafts.clear(admin);assertTrue(drafts.load(admin).isEmpty());assertEquals(5,data.product(product).orElseThrow().available());assertEquals(2,data.purchaseLines(purchase).rows().size());
   ops.setting(admin,"expiry_days","14");assertEquals(1,data.lots("FILTER-0","Por vencer",0).rows().size());var dashboard=ops.grid("dashboard").rows().getFirst();assertEquals("1",dashboard.get(2));long lot=Long.parseLong(data.lots("FILTER-0","Por vencer",0).rows().getFirst().getFirst());ops.block(admin,lot,true);assertEquals("0",ops.grid("dashboard").rows().getFirst().get(2));assertEquals(3,data.product(product).orElseThrow().available());ops.block(admin,lot,false);
   SalesService sales=new SalesService(db);List<CartItem> cart=List.of(new CartItem(product,2));var quote=sales.quoteDetailed(cart);SaleRecovery recovery=new SaleRecovery(db);UUID key=UUID.randomUUID();var cash=new SaleRecovery.Pending(key,null,"CASH",quote.totals().total(),cart,quote.lines(),new BigDecimal("50.00"));recovery.save(admin,cash);assertThrows(IllegalArgumentException.class,()->recovery.save(admin,new SaleRecovery.Pending(key,null,"CASH",quote.totals().total(),cart,quote.lines(),new BigDecimal("60.00"))));var receipt=recovery.resolve(admin,key);assertEquals(receipt.id(),recovery.resolve(admin,key).id());recovery.acknowledge(admin,key,false);assertEquals(3,data.product(product).orElseThrow().available());assertTrue(sales.receipt(receipt.id()).contains("50.00"));
   try(var c=db.connection();var q=c.createStatement();var r=q.executeQuery("select received_amount,change_amount from payment")){assertTrue(r.next());assertEquals(new BigDecimal("50.00"),r.getBigDecimal(1));assertEquals(new BigDecimal("30.00"),r.getBigDecimal(2));}
   try(var c=db.connection();var q=c.createStatement()){assertThrows(java.sql.SQLException.class,()->q.executeUpdate("update payment set change_amount=null"));}
   LocalDate today=LocalDate.now();assertEquals(1,reports.reportSearch("sales",today,today,0,50,"Consumidor final").rows().size());assertEquals("20.00",reports.summary("sales",today,today,"").rows().getFirst().get(1));assertEquals(0,reports.reportSearch("sales",today,today,0,50,"inexistente").rows().size());assertEquals(50,reports.reportSearch("stock",today,today,0,50,"").rows().size());assertEquals(5,reports.reportSearch("stock",today,today,50,50,"").rows().size());assertEquals(55,reports.reportSearch("stock",today,today,0,0,"").rows().size());assertThrows(IllegalArgumentException.class,()->reports.reportSearch("sales",today,today.minusDays(1),0,50,""));assertTrue(ops.grid("reconciliation").rows().isEmpty());
   ops.voidSale(admin,receipt.id(),"Prueba de reporte");assertEquals("0",reports.summary("sales",today,today,"").rows().getFirst().get(1));assertEquals("1",reports.summary("sales",today,today,"").rows().getFirst().get(2));assertEquals(5,data.product(product).orElseThrow().available());
   ops.user(admin,null,"desktop_cashier","contraseñaPrueba123","CASHIER",true);var cashier=auth.login("desktop_cashier","contraseñaPrueba123");assertThrows(SecurityException.class,()->drafts.save(cashier,supplier,null,lines));assertThrows(SecurityException.class,()->data.pending(cashier,true));assertThrows(SecurityException.class,()->drafts.load(cashier));assertTrue(data.pending(cashier,false).rows().isEmpty());
  }
 }
}
