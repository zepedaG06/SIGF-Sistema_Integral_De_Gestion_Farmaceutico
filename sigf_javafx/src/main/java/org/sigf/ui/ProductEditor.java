package org.sigf.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.sigf.security.Security;
import org.sigf.services.InventoryService;

final class ProductEditor {
    private ProductEditor() {}

    static void open(
            javafx.stage.Window owner,
            InventoryService service,
            Security.User user,
            Long id,
            InventoryService.ProductDetails old,
            Map<String, List<String>> catalog,
            Runnable saved) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(id == null ? "Nuevo producto" : "Editar producto");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        save.setText("Guardar producto");
        save.setGraphic(UiIcons.icon("save", "action-icon", 20));
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL))
                .setGraphic(UiIcons.icon("close", "action-icon", 20));
        save.getStyleClass().add("primary");
        TextField code = new TextField(old == null ? "" : old.code()),
                barcode = new TextField(old == null ? "" : old.barcode()),
                name = new TextField(old == null ? "" : old.name()),
                cost = new TextField(old == null ? "0.00" : old.cost().toPlainString()),
                price = new TextField(old == null ? "0.00" : old.price().toPlainString());
        code.setId("productCode");
        name.setId("productName");
        price.setId("productPrice");
        TextArea description = new TextArea(old == null ? "" : old.description());
        description.setPrefRowCount(3);
        description.setWrapText(true);
        ComboBox<String>
                category =
                        SearchableChoices.create(
                                catalog.getOrDefault("Categoría", List.of()),
                                old == null ? "" : old.category()),
                lab =
                        SearchableChoices.create(
                                catalog.getOrDefault("Laboratorio", List.of()),
                                old == null ? "" : old.laboratory()),
                presentation =
                        SearchableChoices.create(
                                List.of(
                                        "Tabletas",
                                        "Cápsulas",
                                        "Jarabe",
                                        "Crema",
                                        "Solución",
                                        "Inyectable"),
                                old == null ? "" : old.presentation());
        ComboBox<String> tax = new ComboBox<>();
        tax.getItems()
                .addAll("Seleccionar clasificación", "Exento (0%)", "Gravado (15%)", "Otra tasa");
        TextField rate =
                new TextField(
                        old == null || old.taxRate() == null
                                ? ""
                                : old.taxRate()
                                        .multiply(new BigDecimal("100"))
                                        .stripTrailingZeros()
                                        .toPlainString());
        rate.setPromptText("Porcentaje, por ejemplo 15");
        tax.setValue(
                old == null || old.taxRate() == null
                        ? "Seleccionar clasificación"
                        : old.taxRate().signum() == 0
                                ? "Exento (0%)"
                                : old.taxRate().compareTo(new BigDecimal("0.15")) == 0
                                        ? "Gravado (15%)"
                                        : "Otra tasa");
        tax.setId("productTax");
        rate.disableProperty().bind(tax.valueProperty().isNotEqualTo("Otra tasa"));
        Spinner<Integer> minimum = new Spinner<>(0, 100000000, old == null ? 0 : old.minimum());
        minimum.setEditable(true);
        CheckBox active = new CheckBox("Producto activo");
        active.setSelected(old == null || old.active());
        Label finalPrice = new Label(), error = Views.label("", "warning");
        Runnable preview =
                () -> {
                    try {
                        BigDecimal vat =
                                tax.getValue().equals("Exento (0%)")
                                        ? BigDecimal.ZERO
                                        : tax.getValue().equals("Gravado (15%)")
                                                ? new BigDecimal("0.15")
                                                : new BigDecimal(rate.getText())
                                                        .divide(new BigDecimal("100"));
                        finalPrice.setText(
                                "Precio final: "
                                        + Views.money(
                                                new BigDecimal(price.getText())
                                                        .multiply(BigDecimal.ONE.add(vat))
                                                        .setScale(2, RoundingMode.HALF_UP)));
                    } catch (Exception ex) {
                        finalPrice.setText("Selecciona el IVA para calcular el precio final");
                    }
                };
        price.textProperty().addListener((o, a, b) -> preview.run());
        tax.setOnAction(e -> preview.run());
        rate.textProperty().addListener((o, a, b) -> preview.run());
        preview.run();
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(14);
        grid.setPadding(new Insets(20));
        grid.add(Views.heading("IDENTIFICACIÓN", "eyebrow", "barcode"), 0, 0, 2, 1);
        grid.add(field("Código *", code), 0, 1);
        grid.add(field("Código de barras", barcode), 1, 1);
        grid.add(field("Nombre *", name), 0, 2, 2, 1);
        grid.add(Views.heading("CLASIFICACIÓN", "eyebrow", "tag"), 0, 3, 2, 1);
        grid.add(field("Categoría", category), 0, 4);
        grid.add(field("Presentación", presentation), 1, 4);
        grid.add(field("Laboratorio", lab), 0, 5);
        grid.add(field("Descripción", description), 1, 5);
        grid.add(Views.heading("PRECIOS Y CONTROL", "eyebrow", "cash"), 0, 6, 2, 1);
        grid.add(field("Costo C$", cost), 0, 7);
        grid.add(field("Precio sin IVA C$ *", price), 1, 7);
        grid.add(field("Clasificación de IVA *", tax), 0, 8);
        grid.add(field("Otra tasa (%)", rate), 1, 8);
        grid.add(field("Existencia mínima", minimum), 0, 9);
        grid.add(active, 1, 9);
        grid.add(finalPrice, 0, 10, 2, 1);
        grid.add(error, 0, 11, 2, 1);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            grid.getColumnConstraints().add(column);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(680);
        scroll.setPrefViewportHeight(570);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane()
                .getStylesheets()
                .add(ProductEditor.class.getResource("/css/style.css").toExternalForm());
        save.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                e -> {
                    e.consume();
                    try {
                        if (code.getText().isBlank() || name.getText().isBlank()) {
                            throw new IllegalArgumentException("Completa código y nombre");
                        }
                        if (tax.getValue().equals("Seleccionar clasificación")) {
                            throw new IllegalArgumentException(
                                    "Selecciona la clasificación de IVA");
                        }
                        BigDecimal vat =
                                tax.getValue().equals("Exento (0%)")
                                        ? BigDecimal.ZERO
                                        : tax.getValue().equals("Gravado (15%)")
                                                ? new BigDecimal("0.15")
                                                : new BigDecimal(rate.getText())
                                                        .divide(new BigDecimal("100"));
                        var product =
                                new InventoryService.ProductDetails(
                                        code.getText(),
                                        barcode.getText(),
                                        name.getText(),
                                        category.getEditor().getText().trim(),
                                        description.getText(),
                                        presentation.getEditor().getText().trim(),
                                        lab.getEditor().getText().trim(),
                                        new BigDecimal(cost.getText()),
                                        new BigDecimal(price.getText()),
                                        Integer.parseInt(minimum.getEditor().getText()),
                                        active.isSelected(),
                                        vat);
                        save.setDisable(true);
                        dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
                        error.setText("Guardando…");
                        Views.async(
                                () -> {
                                    service.saveDetails(user, id, product);
                                    return null;
                                },
                                x -> {
                                    save.setDisable(false);
                                    dialog.close();
                                    saved.run();
                                },
                                ex -> {
                                    save.setDisable(false);
                                    dialog.getDialogPane()
                                            .lookupButton(ButtonType.CANCEL)
                                            .setDisable(false);
                                    error.setText(
                                            Objects.toString(
                                                    ex.getMessage(), "No se pudo guardar"));
                                });
                    } catch (Exception ex) {
                        error.setText(ex.getMessage());
                    }
                });
        dialog.setOnCloseRequest(
                e -> {
                    if (save.isDisabled()) {
                        e.consume();
                    }
                });
        dialog.showAndWait();
    }

    private static VBox field(String name, javafx.scene.Node control) {
        if (control instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(5, new Label(name), control);
    }
}
