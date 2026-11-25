package org.sigf.ui;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Iconos vectoriales locales para JavaFX. */
final class UiIcons {
    private UiIcons() {}

    static StackPane icon(String name, String style) {
        return icon(name, style, 22);
    }

    static StackPane icon(String name, String style, double size) {
        String path =
                switch (name) {
                    case "search" -> "M10 3 A7 7 0 1 0 10 17 A7 7 0 1 0 10 3 M15 15 L22 22";
                    case "save" -> "M4 3 H17 L21 7 V21 H3 V3 Z M7 3 V9 H16 V3 M7 21 V14 H17 V21";
                    case "edit" -> "M4 16 L16 4 L20 8 L8 20 L3 21 Z M14 6 L18 10";
                    case "plus" -> "M12 4 V20 M4 12 H20";
                    case "chevron-left" -> "M15 5 L8 12 L15 19";
                    case "chevron-right" -> "M9 5 L16 12 L9 19";
                    case "more" -> "M4 11 H6 V13 H4 Z M11 11 H13 V13 H11 Z M18 11 H20 V13 H18 Z";
                    case "receipt" ->
                            "M5 2 L8 4 L11 2 L14 4 L17 2 L20 4 V22 L17 20 L14 22 L11 20 L8 22 L5 20 Z M8 8 H17 M8 12 H17 M8 16 H14";
                    case "cash" ->
                            "M2 5 H22 V19 H2 Z M2 9 C5 9 6 8 6 5 M18 5 C18 8 19 9 22 9 M2 15 C5 15 6 16 6 19 M18 19 C18 16 19 15 22 15 M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8";
                    case "card" -> "M3 5 H21 V19 H3 Z M3 10 H21 M6 15 H10";
                    case "bank" ->
                            "M2 8 L12 2 L22 8 Z M5 10 V19 M10 10 V19 M15 10 V19 M20 10 V19 M2 22 H22";
                    case "barcode" ->
                            "M3 3 V21 M6 3 V21 M10 3 V21 M12 3 V21 M16 3 V21 M20 3 V21 M22 3 V21";
                    case "filter" -> "M3 4 H21 L14 12 V20 L10 18 V12 Z";
                    case "export" -> "M14 3 H21 V10 M21 3 L11 13 M10 5 H4 V21 H20 V14";
                    case "file" -> "M5 2 H14 L20 8 V22 H5 Z M14 2 V8 H20 M8 12 H17 M8 16 H17";
                    case "backup" ->
                            "M12 3 A9 9 0 1 0 21 12 M3 3 V8 H8 M12 17 V8 M8 12 L12 8 L16 12";
                    case "database" ->
                            "M3 5 C3 1 21 1 21 5 C21 9 3 9 3 5 V19 C3 23 21 23 21 19 V5 M3 12 C3 16 21 16 21 12";
                    case "upload" -> "M12 16 V3 M7 8 L12 3 L17 8 M4 14 V21 H20 V14";
                    case "image" ->
                            "M3 3 H21 V21 H3 Z M3 17 L9 11 L14 16 L17 13 L21 17 M16 6 A2 2 0 1 0 16 10 A2 2 0 1 0 16 6";
                    case "phone" ->
                            "M6 3 L3 6 C3 14 10 21 18 21 L21 18 L16 14 L13 17 C10 15 9 14 7 11 L10 8 Z";
                    case "shop" ->
                            "M4 10 V21 H20 V10 M9 21 V15 H15 V21 M3 10 L5 3 H19 L21 10 C20 13 17 13 16 10 C15 13 12 13 11 10 C10 13 6 13 5 10 C4 12 2 12 3 10 Z";
                    case "tag" ->
                            "M3 3 H12 L22 13 L13 22 L3 12 Z M7 6 A1.5 1.5 0 1 0 7 9 A1.5 1.5 0 1 0 7 6";
                    case "sliders" ->
                            "M3 6 H8 M12 6 H21 M3 12 H14 M18 12 H21 M3 18 H5 M9 18 H21 M8 3 V9 H12 V3 Z M14 9 V15 H18 V9 Z M5 15 V21 H9 V15 Z";
                    case "check" -> "M4 12 L9 17 L20 6";
                    case "close" -> "M5 5 L19 19 M19 5 L5 19";
                    case "logout" -> "M10 3 H3 V21 H10 M8 12 H22 M17 7 L22 12 L17 17";
                    case "lock" -> "M6 10 V7 A6 6 0 0 1 18 7 V10 M4 10 H20 V22 H4 Z M12 14 V18";
                    case "delete" ->
                            "M4 6 H20 M9 6 V3 H15 V6 M6 6 L7 21 H17 L18 6 M10 10 V17 M14 10 V17";
                    case "pharmacy" -> "M8 2 H16 V8 H22 V16 H16 V22 H8 V16 H2 V8 H8 Z";
                    case "Inicio" -> "M3 11 L12 3 L21 11 M5 10 V21 H10 V15 H14 V21 H19 V10";
                    case "Ventas" ->
                            "M3 4 H5 L7 16 H19 L21 7 H6 M9 20 A1 1 0 1 0 9.01 20 M18 20 A1 1 0 1 0 18.01 20";
                    case "Inventario" ->
                            "M12 2 L21 7 V17 L12 22 L3 17 V7 Z M3 7 L12 12 L21 7 M12 12 V22";
                    case "Compras" ->
                            "M2 5 H15 V17 H2 Z M15 9 H19 L22 13 V17 H15 Z M6 17 A2 2 0 1 0 6 21 A2 2 0 1 0 6 17 M18 17 A2 2 0 1 0 18 21 A2 2 0 1 0 18 17";
                    case "Contactos" ->
                            "M12 12 A4 4 0 1 0 12 4 A4 4 0 1 0 12 12 M4 21 V19 C4 15 7 14 12 14 C17 14 20 15 20 19 V21 Z";
                    case "Reportes" ->
                            "M4 20 V12 H7 V20 M10 20 V5 H13 V20 M16 20 V9 H19 V20 M2 21 H22";
                    case "Administración" ->
                            "M12 2 L14 4 L17 3 L18 6 L21 7 L20 10 L22 12 L20 14 L21 17 L18 18 L17 21 L14 20 L12 22 L10 20 L7 21 L6 18 L3 17 L4 14 L2 12 L4 10 L3 7 L6 6 L7 3 L10 4 Z M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8";
                    case "alert" -> "M12 3 L22 21 H2 Z M12 9 V15 M12 18 V19";
                    case "calendar" -> "M4 5 H20 V21 H4 Z M4 10 H20 M8 2 V7 M16 2 V7";
                    case "activity" -> "M3 12 A9 9 0 1 0 5 6 M3 3 V7 H7 M12 6 V12 L16 14";
                    case "refresh" -> "M20 8 A8 8 0 1 0 20 16 M20 3 V8 H15";
                    default -> "M12 3 V21 M3 12 H21";
                };
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.getStyleClass().add("ui-icon");
        svg.setScaleX(size / 24);
        svg.setScaleY(size / 24);
        StackPane box = new StackPane(svg);
        box.getStyleClass().add(style);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }
}
