package org.sigf.ui;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Iconos vectoriales locales para JavaFX. */
final class UiIcons {
 private UiIcons() {}
 static StackPane icon(String name,String style) {
  String path=switch(name){
   case "Inicio" -> "M3 11 L12 3 L21 11 M5 10 V21 H10 V15 H14 V21 H19 V10";
   case "Ventas" -> "M3 4 H5 L7 16 H19 L21 7 H6 M9 20 A1 1 0 1 0 9.01 20 M18 20 A1 1 0 1 0 18.01 20";
   case "Inventario" -> "M12 2 L21 7 V17 L12 22 L3 17 V7 Z M3 7 L12 12 L21 7 M12 12 V22";
   case "Compras" -> "M2 5 H15 V17 H2 Z M15 9 H19 L22 13 V17 H15 Z M6 17 A2 2 0 1 0 6 21 A2 2 0 1 0 6 17 M18 17 A2 2 0 1 0 18 21 A2 2 0 1 0 18 17";
   case "Contactos" -> "M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 M4 21 V19 C4 15 7 14 12 14 C17 14 20 15 20 19 V21 Z";
   case "Reportes" -> "M4 20 V12 H7 V20 M10 20 V5 H13 V20 M16 20 V9 H19 V20 M2 21 H22";
   case "Administración" -> "M12 2 L14 4 L17 3 L18 6 L21 7 L20 10 L22 12 L20 14 L21 17 L18 18 L17 21 L14 20 L12 22 L10 20 L7 21 L6 18 L3 17 L4 14 L2 12 L4 10 L3 7 L6 6 L7 3 L10 4 Z M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8";
   case "alert" -> "M12 3 L22 21 H2 Z M12 9 V15 M12 18 V19";
   case "calendar" -> "M4 5 H20 V21 H4 Z M4 10 H20 M8 2 V7 M16 2 V7";
   case "activity" -> "M3 12 A9 9 0 1 0 5 6 M3 3 V7 H7 M12 6 V12 L16 14";
   case "refresh" -> "M20 8 A8 8 0 1 0 20 16 M20 3 V8 H15";
   default -> "M12 3 V21 M3 12 H21";
  };
  SVGPath svg=new SVGPath();svg.setContent(path);svg.getStyleClass().add("ui-icon");
  StackPane box=new StackPane(svg);box.getStyleClass().add(style);box.setMinSize(24,24);box.setPrefSize(24,24);box.setMaxSize(24,24);return box;
 }
}
