package org.sigf.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.sigf.config.Database;
import org.sigf.domain.ReportGrid;
import org.sigf.repositories.ReportRepository;
import org.sigf.security.Security;

/** Resumen de consulta adaptado a las responsabilidades profesionales. */
final class RoleDashboardPane extends VBox {
    private final ReportRepository reports;
    private final boolean accounting;
    private final FlowPane indicators = new FlowPane(12, 12);
    private final TableView<List<String>> first = Views.table("Sin resultados"),
            second = Views.table("Sin resultados");
    private final Label status = Views.label("Consultando…", "muted");
    private int version;

    private record Result(ReportGrid first, ReportGrid second, List<String> metrics) {}

    RoleDashboardPane(Database db, Security.User user, BiConsumer<String, String> navigate) {
        reports = new ReportRepository(db, user);
        accounting = user.role().equals("ACCOUNTANT");
        getStyleClass().add("workspace");
        setSpacing(16);
        var action =
                Views.button(
                        accounting ? "Ver reportes" : "Nueva venta",
                        accounting ? "Reportes" : "Ventas",
                        () -> navigate.accept(accounting ? "Reportes" : "Punto de venta", ""),
                        true);
        var header =
                Views.headerWithIcon(
                        accounting ? "Resumen contable" : "Control farmacéutico",
                        accounting
                                ? "Operaciones del mes actual · C$"
                                : "Existencias y vencimientos",
                        accounting ? "Reportes" : "Inventario",
                        Views.button("Actualizar", "refresh", this::reload, false),
                        action);
        TabPane tables =
                new TabPane(
                        new Tab(accounting ? "Ventas del mes" : "Existencias bajas", first),
                        new Tab(accounting ? "Compras del mes" : "Próximos vencimientos", second));
        tables.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tables, Priority.ALWAYS);
        getChildren().addAll(header, indicators, tables, status);
        reload();
    }

    void reload() {
        int ticket = ++version;
        LocalDate to = LocalDate.now(), from = to.withDayOfMonth(1);
        Views.async(
                () -> {
                    if (accounting) {
                        String sales =
                                reports.summary("sales", from, to, "").rows().getFirst().get(1);
                        String purchases =
                                reports.summary("purchases", from, to, "").rows().getFirst().get(1);
                        var voided = reports.summary("voided_docs", from, to, "").rows().getFirst();
                        return new Result(
                                reports.reportPage("sales", from, to, 0, 50),
                                reports.reportPage("purchases", from, to, 0, 50),
                                List.of(
                                        "Ventas confirmadas: " + Views.money(new BigDecimal(sales)),
                                        "Compras registradas: "
                                                + Views.money(new BigDecimal(purchases)),
                                        "Ventas anuladas: " + voided.getFirst()));
                    }
                    return new Result(
                            reports.grid("dashboard_low"),
                            reports.grid("dashboard_expiring"),
                            List.of("Revisa los lotes y las existencias antes de dispensar."));
                },
                result -> {
                    if (ticket != version) {
                        return;
                    }
                    indicators
                            .getChildren()
                            .setAll(
                                    result.metrics().stream()
                                            .map(text -> Views.label(text, "status-chip"))
                                            .toList());
                    Views.grid(first, result.first());
                    Views.grid(second, result.second());
                    status.setText(
                            accounting
                                    ? "Desde "
                                            + from
                                            + " hasta "
                                            + to
                                            + " · tablas limitadas a 50 filas; detalle completo en Reportes"
                                    : "Datos actualizados");
                },
                error -> {
                    if (ticket == version) {
                        status.setText(error.getMessage());
                    }
                });
    }
}
