package org.sigf.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;
import org.sigf.repositories.ReportRepository;
import org.sigf.security.Security;

public final class ReportsPane extends VBox {
    private final Security.User userForExport;
    private final ReportRepository repository;
    private final org.sigf.services.ReportExportService exporter;
    private final FlowPane contextual = new FlowPane(10, 10);
    private final Map<String, ComboBox<Choice>> choices = new LinkedHashMap<>();
    private ReportRepository.Filters appliedFilters = ReportRepository.Filters.empty();
    private String appliedDescription = "Todos";
    private boolean exporting;
    private HBox summaryCards;
    private int filterVersion;

    private record Choice(String value, String label) {
        public String toString() {
            return label;
        }
    }

    private final ComboBox<String> type = new ComboBox<>();
    private final Map<String, String> types = new LinkedHashMap<>();
    private final DatePicker from = new DatePicker(LocalDate.now().withDayOfMonth(1)),
            to = new DatePicker(LocalDate.now());
    private final TextField search = new TextField();
    private final TableView<List<String>> table = Views.table("Sin resultados para estos filtros");
    private final Label status = new Label(),
            pageLabel = new Label(),
            count = new Label(),
            amount = new Label(),
            voided = new Label();
    private int page, version;
    private long total;
    private final Button previous, next;
    private String appliedType = "stock", appliedSearch = "";
    private LocalDate appliedFrom, appliedTo;
    private ReportGrid current;

    public ReportsPane(Database db, Security.User user) {
        userForExport = user;
        repository = new ReportRepository(db, user);
        exporter = new org.sigf.services.ReportExportService(db);
        getStyleClass().add("workspace");
        setSpacing(16);
        Views.date(from);
        Views.date(to);
        types.put("General", "general");
        types.put("Existencias", "stock");
        types.put("Ventas por período", "sales");
        types.put("Compras", "purchases");
        types.put("Productos más vendidos", "top");
        types.put("Vencimientos", "batches");
        types.put("Movimientos de inventario", "movements");
        types.put("Resumen de IVA", "tax_summary");
        types.put("Comprobantes internos", "internal_docs");
        types.put("Anulaciones internas", "voided_docs");
        if (user.role().equals("ACCOUNTANT") || user.role().equals("ADMIN")) {
            types.put("Pagos registrados", "payments");
            types.put("Conciliación de datos", "reconciliation");
        }
        if (user.role().equals("ADMIN")) {
            types.put("Actividad administrativa", "audit");
            types.put("Conciliación de datos", "reconciliation");
        }
        types.entrySet()
                .removeIf(
                        entry ->
                                !org.sigf.security.Permissions.canReadReport(
                                        user.role(), entry.getValue()));
        type.getItems().addAll(types.keySet());
        type.setValue("General");
        appliedType = "general";
        type.setId("reportType");
        search.setPromptText("Buscar en todo el reporte");
        search.setPrefWidth(230);
        Button consult =
                Views.button(
                        "Aplicar filtros",
                        "search",
                        () -> {
                            try {
                                from.commitValue();
                                to.commitValue();
                            } catch (Exception ex) {
                                status.setText("Fecha inválida. Usa DD/MM/AAAA");
                                return;
                            }
                            page = 0;
                            appliedType = types.get(type.getValue());
                            appliedFrom = from.getValue();
                            appliedTo = to.getValue();
                            appliedSearch = search.getText();
                            Map<String, String> selected = new LinkedHashMap<>();
                            StringBuilder description = new StringBuilder();
                            choices.forEach(
                                    (key, box) -> {
                                        Choice c = box.getValue();
                                        if (c != null && !c.value().isEmpty()) {
                                            selected.put(key, c.value());
                                            description
                                                    .append(key)
                                                    .append(": ")
                                                    .append(c.label())
                                                    .append("; ");
                                        }
                                    });
                            appliedFilters = new ReportRepository.Filters(selected);
                            appliedDescription =
                                    description.isEmpty() ? "Todos" : description.toString();
                            load();
                        },
                        true);
        consult.setId("reportConsult");
        search.setOnAction(e -> consult.fire());
        type.setOnAction(
                e -> {
                    boolean dates =
                            !Set.of("stock", "reconciliation").contains(types.get(type.getValue()));
                    from.setDisable(!dates);
                    to.setDisable(!dates);
                    context(types.get(type.getValue()));
                    status.setText("Pulsa Aplicar filtros para actualizar el reporte");
                });
        from.setDisable(false);
        to.setDisable(false);
        FlowPane presets =
                new FlowPane(
                        6,
                        6,
                        Views.button(
                                "Hoy",
                                "calendar",
                                () -> period(LocalDate.now(), LocalDate.now()),
                                false),
                        Views.button(
                                "Ayer",
                                "calendar",
                                () ->
                                        period(
                                                LocalDate.now().minusDays(1),
                                                LocalDate.now().minusDays(1)),
                                false),
                        Views.button(
                                "Esta semana",
                                "calendar",
                                () ->
                                        period(
                                                LocalDate.now().with(java.time.DayOfWeek.MONDAY),
                                                LocalDate.now()),
                                false),
                        Views.button(
                                "Este mes",
                                "calendar",
                                () -> period(LocalDate.now().withDayOfMonth(1), LocalDate.now()),
                                false));
        FlowPane filters =
                new FlowPane(
                        10,
                        10,
                        type,
                        new Label("Desde"),
                        from,
                        new Label("Hasta"),
                        to,
                        Views.searchBox(search, "search"),
                        consult,
                        Views.button(
                                "Limpiar filtros",
                                "close",
                                () -> {
                                    search.clear();
                                    from.setValue(LocalDate.now().withDayOfMonth(1));
                                    to.setValue(LocalDate.now());
                                    choices.values()
                                            .forEach(box -> box.getSelectionModel().selectFirst());
                                    consult.fire();
                                },
                                false));
        MenuButton export = new MenuButton("Exportar", UiIcons.icon("export", "action-icon", 20));
        for (String format : List.of("PDF", "XLSX", "DOCX", "CSV")) {
            MenuItem item = new MenuItem("Exportar reporte completo a " + format);
            item.setOnAction(e -> export(format.toLowerCase(java.util.Locale.ROOT)));
            export.getItems().add(item);
        }
        HBox summary =
                new HBox(
                        14,
                        Views.cardWithIcon("Resultados", "Reportes", count),
                        Views.cardWithIcon("Importe del reporte", "cash", amount),
                        Views.cardWithIcon("Anuladas", "close", voided));
        summaryCards = summary;
        for (var node : summary.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        count.getStyleClass().add("sale-total");
        amount.getStyleClass().add("sale-total");
        voided.getStyleClass().add("sale-total");
        previous =
                Views.button(
                        "Anterior",
                        "chevron-left",
                        () -> {
                            if (page > 0) {
                                page--;
                                load();
                            }
                        },
                        false);
        next =
                Views.button(
                        "Siguiente",
                        "chevron-right",
                        () -> {
                            if ((page + 1L) * 50 < total) {
                                page++;
                                load();
                            }
                        },
                        false);
        HBox paging = new HBox(12, previous, pageLabel, next);
        VBox card = Views.cardWithIcon("Resultado de la consulta", "file", table, paging);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren()
                .addAll(
                        Views.headerWithIcon(
                                "Reportes",
                                "Consulta, compara y exporta información con filtros claros",
                                "Reportes",
                                export),
                        Views.cardWithIcon("Filtros", "filter", presets, filters, contextual),
                        summary,
                        card,
                        status);
        appliedFrom = from.getValue();
        appliedTo = to.getValue();
        load();
    }

    private void period(LocalDate a, LocalDate b) {
        from.setValue(a);
        to.setValue(b);
        status.setText(
                "Período seleccionado. Pulsa Aplicar filtros. Las existencias y alertas corresponden al estado actual.");
    }

    private record Result(ReportGrid page, ReportGrid summary) {}

    private void load() {
        current = null;
        count.setText("—");
        amount.setText("—");
        voided.setText("—");
        previous.setDisable(true);
        next.setDisable(true);
        int ticket = ++version;
        String name = appliedType, q = appliedSearch;
        LocalDate a = appliedFrom, b = appliedTo;
        int offset = page * 50;
        var filters = appliedFilters;
        status.setText("Consultando…");
        Views.async(
                () ->
                        new Result(
                                repository.filtered(name, a, b, q, filters, offset, 50, false),
                                repository.filtered(name, a, b, q, filters, 0, 0, true)),
                result -> {
                    if (ticket != version) {
                        return;
                    }
                    summaryCards.setVisible(!name.equals("general"));
                    summaryCards.setManaged(!name.equals("general"));
                    current = result.page();
                    Views.grid(table, current);
                    var values = result.summary().rows().getFirst();
                    total = Long.parseLong(values.get(0));
                    count.setText(Long.toString(total));
                    amount.setText(
                            values.get(1).isBlank()
                                    ? "No aplica"
                                    : Views.money(new BigDecimal(values.get(1))));
                    voided.setText(values.get(2));
                    pageLabel.setText(
                            "Página " + (page + 1) + " de " + Math.max(1, (total + 49) / 50));
                    previous.setDisable(page == 0);
                    next.setDisable((page + 1L) * 50 >= total);
                    status.setText(
                            (Set.of("stock", "reconciliation").contains(name)
                                            ? "Situación actual"
                                            : "Del " + a + " al " + b)
                                    + " · "
                                    + total
                                    + " resultados · filtros aplicados");
                },
                ex -> {
                    if (ticket == version) {
                        current = null;
                        table.getItems().clear();
                        status.setText("No se pudo consultar: " + ex.getMessage());
                    }
                });
    }

    private void context(String name) {
        int ticket = ++filterVersion;
        choices.clear();
        contextual.getChildren().clear();
        if (name.equals("batches"))
            contextual
                    .getChildren()
                    .add(
                            Views.button(
                                    "Todos los vencimientos",
                                    "calendar",
                                    () ->
                                            period(
                                                    LocalDate.of(1900, 1, 1),
                                                    LocalDate.of(2200, 12, 31)),
                                    false));
        for (String key : ReportRepository.filterKeys(name)) {
            ComboBox<Choice> box = new ComboBox<>();
            box.setId("reportFilter-" + key);
            box.setPrefWidth(210);
            box.getItems().add(new Choice("", "Todos"));
            box.getSelectionModel().selectFirst();
            choices.put(key, box);
            contextual
                    .getChildren()
                    .add(
                            new VBox(
                                    4,
                                    new Label(key.substring(0, 1).toUpperCase() + key.substring(1)),
                                    box));
            if (key.equals("estado"))
                box.getItems()
                        .addAll(
                                new Choice("CONFIRMED", "Confirmadas"),
                                new Choice("VOID", "Anuladas"));
            else if (key.equals("pago"))
                box.getItems()
                        .addAll(
                                new Choice("CASH", "Efectivo"),
                                new Choice("CARD", "Tarjeta"),
                                new Choice("TRANSFER", "Transferencia"));
            else if (key.equals("lote"))
                for (String value : List.of("Disponible", "Por vencer", "Vencido", "Bloqueado"))
                    box.getItems().add(new Choice(value, value));
            else
                Views.async(
                        () -> {
                            var result = new java.util.ArrayList<Choice>();
                            if (Set.of("cliente", "proveedor", "responsable", "producto")
                                    .contains(key))
                                for (var value : repository.filterOptions(name, key))
                                    result.add(new Choice(Long.toString(value.id()), value.name()));
                            else
                                for (String value : repository.textOptions(name, key))
                                    result.add(new Choice(value, value));
                            return result;
                        },
                        values -> {
                            if (ticket == filterVersion) box.getItems().addAll(values);
                        },
                        Views::error);
        }
    }

    private void export(String format) {
        if (exporting) return;
        if (current == null || total == 0) {
            Views.error(new IllegalStateException("No hay resultados para exportar"));
            return;
        }
        if (total > 25000) {
            Views.error(
                    new IllegalArgumentException(
                            "Más de 25 000 resultados. Reduzca el período o aplique filtros"));
            return;
        }
        if (total > 10000
                && !Views.confirm(
                        "El reporte contiene más de 10 000 filas y puede tardar. ¿Continuar?"))
            return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar reporte a " + format.toUpperCase());
        chooser.setInitialFileName("sigf-" + appliedType + "-" + LocalDate.now() + "." + format);
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(format.toUpperCase(), "*." + format));
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        if (file.exists() && !Views.confirm("El archivo ya existe. ¿Reemplazarlo?")) return;
        String name = appliedType,
                q = appliedSearch,
                description = appliedDescription,
                title =
                        types.entrySet().stream()
                                .filter(e -> e.getValue().equals(name))
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElse(name);
        LocalDate a = appliedFrom, b = appliedTo;
        var filters = appliedFilters;
        exporting = true;
        status.setText("Generando reporte " + format.toUpperCase() + "…");
        Views.async(
                () -> {
                    exporter.export(
                            userForExport,
                            name,
                            title,
                            a,
                            b,
                            q,
                            filters,
                            description,
                            format,
                            file.toPath());
                    return null;
                },
                ignored -> {
                    exporting = false;
                    status.setText("Reporte exportado: " + file.getName());
                },
                error -> {
                    exporting = false;
                    status.setText("No se pudo exportar el reporte");
                    Views.error(error);
                });
    }

    public boolean isBusy() {
        return exporting;
    }

    public void reload() {
        load();
    }
}
