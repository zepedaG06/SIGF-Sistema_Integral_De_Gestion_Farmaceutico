package org.sigf.ui;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.sigf.config.Database;
import org.sigf.services.Operations;
import org.sigf.domain.ReportGrid;
import java.util.*;
import java.util.function.Consumer;

/** Resumen a partir de consultas reales; no contiene información de ejemplo. */
public final class DashboardPane extends VBox {
 private final Operations operations;
 private final Consumer<String> navigate;
 private final Label state=new Label("Consultando…");
 private final FlowPane cards=new FlowPane(12,12);
 private final TableView<List<String>> low=new TableView<>(),expiry=new TableView<>(),recent=new TableView<>();
 private record Summary(ReportGrid indicators,ReportGrid low,ReportGrid expiry,ReportGrid recent){}
 public DashboardPane(Database db,Consumer<String> navigate){
  this.operations=new Operations(db);this.navigate=navigate;getStyleClass().add("dashboard");setSpacing(20);
  Label title=new Label("Resumen de la farmacia");title.getStyleClass().add("dashboard-title");
  Label subtitle=new Label("Todo lo importante, en un solo lugar.");subtitle.getStyleClass().add("dashboard-subtitle");
  Button reload=new Button();reload.setGraphic(UiIcons.icon("refresh","toolbar-icon"));reload.setTooltip(new Tooltip("Actualizar datos"));reload.setAccessibleText("Actualizar datos");reload.getStyleClass().add("dashboard-refresh");reload.setOnAction(e->refresh());
  Button sales=new Button("Nueva venta",UiIcons.icon("Ventas","button-icon"));sales.getStyleClass().addAll("primary","dashboard-action");sales.setOnAction(e->navigate.accept("Punto de venta"));
  Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);HBox header=new HBox(12,new VBox(5,title,subtitle),spacer,sales,reload);header.setAlignment(Pos.CENTER_LEFT);
  for(var t:List.of(low,expiry,recent)){t.setPlaceholder(new Label("No hay registros para mostrar."));t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);t.getStyleClass().add("dashboard-table");}
  TabPane alerts=new TabPane();alerts.getStyleClass().add("alert-tabs");alerts.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);alerts.getTabs().addAll(new Tab("Existencias bajas",low),new Tab("Lotes por vencer",expiry));VBox.setVgrow(alerts,Priority.ALWAYS);
  VBox alertPanel=panel("Atención necesaria","alert",alerts);VBox recentPanel=panel("Actividad reciente","activity",recent);
  HBox panels=new HBox(16,alertPanel,recentPanel);HBox.setHgrow(alertPanel,Priority.ALWAYS);HBox.setHgrow(recentPanel,Priority.ALWAYS);alertPanel.setMaxWidth(Double.MAX_VALUE);recentPanel.setMaxWidth(Double.MAX_VALUE);alertPanel.prefWidthProperty().bind(panels.widthProperty().multiply(.42));recentPanel.prefWidthProperty().bind(panels.widthProperty().multiply(.58));VBox.setVgrow(panels,Priority.ALWAYS);
  state.getStyleClass().add("dashboard-status");getChildren().addAll(header,cards,panels,state);refresh();
 }
 private VBox panel(String title,String icon,javafx.scene.Node content){Label heading=new Label(title);heading.getStyleClass().add("panel-heading");HBox header=new HBox(12,UiIcons.icon(icon,"panel-icon"),heading);header.setAlignment(Pos.CENTER_LEFT);header.getStyleClass().add("panel-header");VBox panel=new VBox(header,content);panel.getStyleClass().add("dashboard-panel");panel.setMinWidth(280);VBox.setVgrow(content,Priority.ALWAYS);return panel;}
 public void reload(){refresh();}
 private void refresh(){state.setText("Consultando datos…");Task<Summary> task=new Task<>(){protected Summary call()throws Exception{return new Summary(operations.grid("dashboard"),operations.grid("dashboard_low"),operations.grid("dashboard_expiring"),operations.grid("dashboard_recent"));}};
  task.setOnSucceeded(e->{Summary x=task.getValue();cards.getChildren().clear();if(!x.indicators().rows().isEmpty()){
   String[] names={"Ventas de hoy","Productos activos","Unidades vendibles","Existencias bajas","Lotes por vencer"};String[] icons={"Ventas","Inventario","Compras","alert","calendar"};int[] indexes={4,0,1,3,2};
   for(int i=0;i<names.length;i++){int idx=indexes[i];String raw=x.indicators().rows().get(0).get(idx);Label number=new Label((idx==4?"C$ ":"")+raw);number.getStyleClass().add("dashboard-card-value");Label label=new Label(names[i]);label.getStyleClass().add("dashboard-card-label");label.setWrapText(true);StackPane glyph=UiIcons.icon(icons[i],"card-icon");glyph.setMinSize(54,54);glyph.setPrefSize(54,54);glyph.setMaxSize(54,54);HBox card=new HBox(14,glyph,new VBox(5,label,number));card.setAlignment(Pos.CENTER_LEFT);card.getStyleClass().add("dashboard-card");card.setMinWidth(0);card.prefWidthProperty().bind(cards.widthProperty().subtract(64).divide(5));if(idx==3)card.getStyleClass().add("dashboard-card-warning");if(idx==2||idx==3){card.setOnMouseClicked(event->navigate.accept(idx==2?"Lotes":"Productos"));card.setCursor(javafx.scene.Cursor.HAND);}cards.getChildren().add(card);}}
   show(low,x.low());show(expiry,x.expiry());show(recent,x.recent());state.setText("Datos actualizados");});
  task.setOnFailed(e->state.setText("No se pudo consultar: "+Objects.toString(task.getException().getMessage(),"error")));Thread t=new Thread(task,"sigf-dashboard");t.setDaemon(true);t.start();
 }
 private static void show(TableView<List<String>> table,ReportGrid data){table.getColumns().clear();for(int i=0;i<data.columns().size();i++){final int n=i;String name=switch(data.columns().get(i)){case "codigo"->"Código";case "minimo"->"Mínimo";case "vendible"->"Disponible";case "fecha"->"Fecha";case "producto"->"Producto";case "tipo"->"Movimiento";case "cantidad"->"Cantidad";case "vence"->"Vence";case "lote"->"Lote";default->data.columns().get(i).replace('_',' ');};TableColumn<List<String>,String> col=new TableColumn<>(name);col.setCellValueFactory(v->new javafx.beans.property.ReadOnlyStringWrapper(v.getValue().get(n)));col.setMinWidth(75);col.setPrefWidth(140);table.getColumns().add(col);}table.getItems().setAll(data.rows());}
}

