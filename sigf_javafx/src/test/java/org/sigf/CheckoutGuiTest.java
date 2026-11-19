package org.sigf;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.InventoryService;
import org.sigf.services.Operations;
import org.sigf.ui.OperationalPane;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SIGF_CHECKOUT_GUI_TEST",matches="true")
class CheckoutGuiTest {
 static void fx(Runnable action) throws Exception {CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();Platform.runLater(()->{try{action.run();}catch(Throwable e){error.set(e);}finally{done.countDown();}});assertTrue(done.await(10,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError(error.get());}
 static Node find(String id) throws Exception {AtomicReference<Node> node=new AtomicReference<>();for(int i=0;i<150&&node.get()==null;i++){Thread.sleep(100);fx(()->Window.getWindows().stream().filter(Window::isShowing).filter(w->w.getScene()!=null).map(w->w.getScene().lookup(id)).filter(java.util.Objects::nonNull).findFirst().ifPresent(node::set));}assertNotNull(node.get(),"No apareció "+id);return node.get();}
 static Button button(String text) throws Exception {AtomicReference<Button> out=new AtomicReference<>();for(int i=0;i<150&&out.get()==null;i++){Thread.sleep(100);fx(()->Window.getWindows().stream().filter(Window::isShowing).filter(w->w.getScene()!=null).flatMap(w->w.getScene().getRoot().lookupAll(".button").stream()).filter(n->n instanceof Button b&&text.equals(b.getText())).map(n->(Button)n).findFirst().ifPresent(out::set));}assertNotNull(out.get(),"No apareció el botón "+text);return out.get();}
 @Test void saleDialogRecordsPaymentAndStockAndCatalogPersists() throws Exception {
  try(Database db=new Database()) {Security security=new Security(db);security.bootstrap("ui_admin","contraseñaPrueba123");Security.User admin=security.login("ui_admin","contraseñaPrueba123");InventoryService inventory=new InventoryService(db);
   inventory.saveDetails(admin,null,new InventoryService.ProductDetails("UI-1","","Producto UI","Analgésicos","","Tabletas","Laboratorio UI",new BigDecimal("5.00"),new BigDecimal("10.00"),1,true,new BigDecimal("0.15")));
   assertTrue(inventory.catalog().get("Categoría").contains("Analgésicos"));assertTrue(new InventoryService(db).catalog().get("Laboratorio").contains("Laboratorio UI"));long id=inventory.products("UI-1").getFirst().id();inventory.receive(admin,id,"LOTE-UI",LocalDate.now().plusDays(90),5,new BigDecimal("5.00"));new Operations(db).contact(admin,"supplier",null,"Proveedor UI","22222222",true);
   CountDownLatch started=new CountDownLatch(1);AtomicReference<Stage> stage=new AtomicReference<>();Platform.startup(()->{Stage w=new Stage();w.setScene(new Scene(new Pane(),1100,800));w.show();w.getScene().setRoot(new OperationalPane(db,admin,"purchases","Compras",()->{},(k,v)->{}));stage.set(w);started.countDown();});assertTrue(started.await(10,TimeUnit.SECONDS));
   Button purchaseAdd=(Button)find("#purchaseAdd");Platform.runLater(purchaseAdd::fire);Button chooseProduct=button("OK");fx(chooseProduct::fire);
   TextField lot=(TextField)find("#formField0");fx(()->{lot.setText("LOTE-UI-2");});DatePicker expiration=(DatePicker)find("#formField1");fx(()->expiration.setValue(LocalDate.now().plusDays(120)));Spinner<?> qty=(Spinner<?>)find("#formField2");fx(()->qty.getEditor().setText("2"));Button saveLine=button("OK");fx(saveLine::fire);
   Button purchaseCommit=(Button)find("#purchaseCommit");Platform.runLater(purchaseCommit::fire);Button chooseSupplier=button("OK");fx(chooseSupplier::fire);Button purchaseReceipt=button("OK");fx(purchaseReceipt::fire);
   for(int i=0;i<100&&inventory.products("UI-1").getFirst().stock()!=7;i++)Thread.sleep(100);assertEquals(7,inventory.products("UI-1").getFirst().stock());fx(()->stage.get().getScene().setRoot(new OperationalPane(db,admin,"cart","Punto de venta",()->{},(k,v)->{})));
   TableView<?> products=(TableView<?>)find("#operationalTable");for(int i=0;i<100;i++){AtomicBoolean loaded=new AtomicBoolean();fx(()->loaded.set(!products.getItems().isEmpty()));if(loaded.get())break;Thread.sleep(100);}fx(()->products.getSelectionModel().select(0));Button add=(Button)find("#cartAdd");Platform.runLater(add::fire);Button ok=button("OK");fx(ok::fire);
   Button checkout=(Button)find("#cartCheckout");Platform.runLater(checkout::fire);ComboBox<?> payment=(ComboBox<?>)find("#salePayment");fx(()->payment.getSelectionModel().select(1));Button confirm=button("Confirmar venta");fx(()->{for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null&&w.getScene().lookup("#salePayment")!=null){w.getScene().getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Confirmar venta")).map(n->(Button)n).findFirst().orElseThrow().fire();return;}});
   Button receipt=button("OK");fx(receipt::fire);for(int i=0;i<100&&inventory.products("UI-1").getFirst().stock()!=6;i++)Thread.sleep(100);assertEquals(6,inventory.products("UI-1").getFirst().stock());try(var c=db.connection();var q=c.prepareStatement("select method,amount from payment" );var r=q.executeQuery()){assertTrue(r.next());assertEquals("CARD",r.getString(1));assertEquals(0,new BigDecimal("11.50").compareTo(r.getBigDecimal(2)));}
   fx(()->stage.get().close());Platform.exit();
  }
 }
}
