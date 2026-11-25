package org.sigf.ui;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.sigf.domain.ReportGrid;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SIGF_LAYOUT_GUI_TEST",matches="true")
class TableLayoutGuiTest {
 static void fx(Runnable action)throws Exception{var wait=new CountDownLatch(1);var error=new AtomicReference<Throwable>();Platform.runLater(()->{try{action.run();}catch(Throwable e){error.set(e);}finally{wait.countDown();}});assertTrue(wait.await(10,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError(error.get());}
 @Test void manualWidthsSurviveResizeRefreshAndPanelDragging()throws Exception{
  var ready=new CountDownLatch(1);Platform.startup(ready::countDown);assertTrue(ready.await(10,TimeUnit.SECONDS));Platform.setImplicitExit(false);
  var stage=new AtomicReference<Stage>();var table=new AtomicReference<TableView<List<String>>>();var panels=new AtomicReference<SplitPane>();String key="test-"+UUID.randomUUID();
  try{
   fx(()->{TableView<List<String>> t=Views.table("Vacío");t.setId(key);Views.grid(t,new ReportGrid(List.of("codigo","producto"),List.of(List.of("A","Producto"))));SplitPane split=new SplitPane(new VBox(t),new VBox(new Label("Detalle")));split.setDividerPositions(.5);Views.adjustable(split,key);VBox root=new VBox(split);VBox.setVgrow(split,javafx.scene.layout.Priority.ALWAYS);Stage w=new Stage();w.setScene(new Scene(root,1100,650));w.getScene().getStylesheets().add(Views.class.getResource("/css/style.css").toExternalForm());w.show();root.applyCss();root.layout();stage.set(w);table.set(t);panels.set(split);});
   fx(()->{var t=table.get();var col=t.getColumns().getFirst();assertFalse(col.prefWidthProperty().isBound());double before=col.getWidth();assertTrue(t.resizeColumn(col,75));assertTrue(col.getWidth()>before+50);});
   AtomicReference<Double> saved=new AtomicReference<>();fx(()->saved.set(table.get().getColumns().getFirst().getWidth()));
   fx(()->{stage.get().setWidth(1350);table.get().getScene().getRoot().layout();assertEquals(saved.get(),table.get().getColumns().getFirst().getPrefWidth(),1);Views.grid(table.get(),new ReportGrid(List.of("codigo","producto"),List.of(List.of("B","Otro"))));assertEquals(saved.get(),table.get().getColumns().getFirst().getPrefWidth(),1);panels.get().setDividerPositions(.7);});
   Thread.sleep(600);
   fx(()->{assertTrue(panels.get().getDividers().getFirst().getPosition()>.6);TableView<List<String>> reopened=Views.table("Vacío");reopened.setId(key);Views.grid(reopened,new ReportGrid(List.of("codigo","producto"),List.of()));assertEquals(saved.get(),reopened.getColumns().getFirst().getPrefWidth(),1);panels.get().getScene().snapshot(null);});
  }finally{fx(()->{if(stage.get()!=null)stage.get().close();});Platform.exit();}
 }
}
