package org.sigf.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.sigf.app.Main;
import org.sigf.config.Database;
import org.sigf.security.Security;
import org.sigf.services.PharmacyProfileService;

/** Navegación y sesión; las pantallas conservan su estado al cambiar de sección. */
public final class Workspace {
    private final Stage stage;
    private final Database db;
    private final Security.User user;
    private final BorderPane root = new BorderPane();
    private final TabPane navigation = new TabPane();
    private final Map<String, Tab> children = new LinkedHashMap<>();
    private final Map<String, Node> views = new LinkedHashMap<>();
    private final Label pharmacyName = new Label("SIGF / Farmacia");
    private final javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView();

    public Workspace(Stage stage, Database db, Security.User user) {
        this.stage = stage;
        this.db = db;
        this.user = user;
    }

    public void show() {
        pharmacyName.getStyleClass().add("brand");
        logo.setFitWidth(42);
        logo.setFitHeight(42);
        logo.setPreserveRatio(true);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        MenuItem logout = new MenuItem("Cerrar sesión", UiIcons.icon("logout", "action-icon", 20));
        logout.setOnAction(
                e -> {
                    if (!allowLeave()) {
                        return;
                    }
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
                        Parent view = loader.load();
                        ((LoginController) loader.getController()).init(stage, db);
                        stage.setOnCloseRequest(null);
                        stage.getScene().setRoot(view);
                    } catch (Exception ex) {
                        Views.error(ex);
                    }
                });
        MenuButton account =
                new MenuButton(
                        user.name() + " · " + (admin() ? "Administrador" : "Cajero"),
                        UiIcons.icon("Contactos", "account-icon"),
                        logout);
        account.getStyleClass().add("account-menu");
        StackPane mark = UiIcons.icon("pharmacy", "brand-icon");
        logo.visibleProperty().bind(logo.imageProperty().isNotNull());
        logo.managedProperty().bind(logo.visibleProperty());
        mark.visibleProperty().bind(logo.imageProperty().isNull());
        mark.managedProperty().bind(mark.visibleProperty());
        HBox header = new HBox(18, mark, logo, pharmacyName, space, account);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.getStyleClass().add("topbar");
        root.setTop(header);
        navigation.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        navigation.getStyleClass().add("main-navigation");
        DashboardPane dashboard = new DashboardPane(db, this::navigate, admin());
        Tab home = new Tab("Inicio", dashboard);
        home.setGraphic(UiIcons.icon("Inicio", "nav-icon"));
        navigation.getTabs().add(home);
        views.put("Inicio", dashboard);
        add("Ventas", "Punto de venta", () -> new SalesPane(db, user));
        add("Ventas", "Historial de ventas", () -> operational("sales", "Historial de ventas"));
        add("Ventas", "Ventas pendientes", () -> operational("pending", "Ventas pendientes"));
        add("Inventario", "Productos", () -> new InventoryPane(db, user, this::navigate));
        add("Inventario", "Lotes", () -> operational("batches", "Lotes y vencimientos"));
        add(
                "Inventario",
                "Movimientos",
                () -> operational("movements", "Movimientos de inventario"));
        if (admin()) {
            add("Compras", "Nueva compra", () -> new PurchasePane(db, user));
        }
        add(
                "Compras",
                "Historial de compras",
                () -> operational("purchases", "Historial de compras"));
        if (admin()) {
            add(
                    "Compras",
                    "Compras pendientes",
                    () -> operational("purchase_pending", "Compras pendientes"));
        }
        add("Contactos", "Clientes", () -> operational("customers", "Clientes"));
        add("Contactos", "Proveedores", () -> operational("suppliers", "Proveedores"));
        add("Reportes", "Reportes", () -> new ReportsPane(db, user));
        if (admin()) {
            add("Administración", "Usuarios", () -> operational("users", "Usuarios y permisos"));
            add(
                    "Administración",
                    "Configuración",
                    () -> new SettingsPane(db, user, this::refreshIdentity));
        }
        navigation
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            if (b == home) {
                                dashboard.reload();
                            } else if (b != null && b.getContent() instanceof TabPane nested) {
                                select(nested.getSelectionModel().getSelectedItem());
                            }
                        });
        root.setCenter(navigation);
        stage.getScene().setRoot(root);
        SettingsPane.applyTextSize(stage.getScene());
        stage.setOnCloseRequest(
                e -> {
                    if (!allowLeave()) {
                        e.consume();
                    }
                });
        refreshIdentity();
    }

    private boolean admin() {
        return user.role().equals("ADMIN");
    }

    private OperationalPane operational(String mode, String title) {
        return new OperationalPane(db, user, mode, title, this::refreshIdentity, this::resolved);
    }

    private void add(String section, String name, Supplier<Node> factory) {
        Tab group =
                navigation.getTabs().stream()
                        .filter(t -> t.getText().equals(section))
                        .findFirst()
                        .orElse(null);
        if (group == null) {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getStyleClass().add("section-tabs");
            group = new Tab(section, tabs);
            group.setGraphic(UiIcons.icon(section, "nav-icon"));
            navigation.getTabs().add(group);
            tabs.getSelectionModel()
                    .selectedItemProperty()
                    .addListener(
                            (o, a, b) -> {
                                if (navigation.getSelectionModel().getSelectedItem() != null
                                        && navigation
                                                        .getSelectionModel()
                                                        .getSelectedItem()
                                                        .getContent()
                                                == tabs) {
                                    select(b);
                                }
                            });
        }
        Tab tab = new Tab(name);
        String icon =
                switch (name) {
                    case "Punto de venta" -> "Ventas";
                    case "Productos" -> "Inventario";
                    case "Lotes" -> "calendar";
                    case "Movimientos" -> "activity";
                    case "Nueva compra" -> "Compras";
                    case "Ventas pendientes", "Compras pendientes" -> "alert";
                    case "Clientes", "Proveedores", "Usuarios" -> "Contactos";
                    case "Configuración" -> "Administración";
                    case "Reportes" -> "Reportes";
                    default -> "receipt";
                };
        tab.setGraphic(UiIcons.icon(icon, "tab-icon", 18));
        tab.setUserData(factory);
        children.put(name, tab);
        ((TabPane) group.getContent()).getTabs().add(tab);
    }

    @SuppressWarnings("unchecked")
    private void select(Tab tab) {
        if (tab == null) {
            return;
        }
        if (tab.getContent() == null) {
            Node node = ((Supplier<Node>) tab.getUserData()).get();
            tab.setContent(node);
            views.put(tab.getText(), node);
        } else {
            reload(tab.getContent());
        }
    }

    private void reload(Node node) {
        if (node instanceof OperationalPane p) {
            p.reload();
        } else if (node instanceof SalesPane p) {
            p.reload();
        } else if (node instanceof PurchasePane p) {
            p.reload();
        } else if (node instanceof InventoryPane p) {
            p.reload();
        } else if (node instanceof ReportsPane p) {
            p.reload();
        } else if (node instanceof SettingsPane p) {
            p.reload();
        }
    }

    private void navigate(String target, String filter) {
        boolean productMovements = target.equals("Movimientos del producto");
        Tab tab = children.get(productMovements ? "Movimientos" : target);
        if (tab == null) {
            return;
        }
        for (Tab group : navigation.getTabs()) {
            if (group.getContent() instanceof TabPane tabs && tabs.getTabs().contains(tab)) {
                navigation.getSelectionModel().select(group);
                tabs.getSelectionModel().select(tab);
                select(tab);
                if (productMovements && tab.getContent() instanceof OperationalPane pane) {
                    pane.setProductFilter(Long.parseLong(filter));
                    return;
                }
                if (target.equals("Movimientos")
                        && tab.getContent() instanceof OperationalPane pane) {
                    pane.clearProductFilter();
                }
                if (filter != null && !filter.isBlank()) {
                    if (tab.getContent() instanceof InventoryPane p) {
                        p.setFilter(filter);
                    } else if (tab.getContent() instanceof OperationalPane p) {
                        p.setFilter(filter);
                    } else if (tab.getContent() instanceof PurchasePane p) {
                        p.prepareProduct(filter);
                    }
                }
                return;
            }
        }
    }

    private void resolved(String kind, UUID id) {
        for (Node node : views.values()) {
            if (node instanceof SalesPane p) {
                p.resolvedElsewhere(kind, id);
            } else if (node instanceof PurchasePane p) {
                p.resolvedElsewhere(kind, id);
            }
        }
    }

    private boolean allowLeave() {
        for (Node node : views.values()) {
            if ((node instanceof SalesPane p && p.isBusy())
                    || (node instanceof PurchasePane q && q.isBusy())) {
                Views.error(
                        new IllegalStateException(
                                "Espera a que termine la operación en curso antes de salir."));
                return false;
            }
        }
        boolean draft =
                views.values().stream()
                        .anyMatch(
                                n ->
                                        (n instanceof SalesPane p && p.hasDraft())
                                                || (n instanceof PurchasePane q && q.hasDraft())
                                                || (n instanceof SettingsPane r && r.hasDraft()));
        return !draft
                || Views.confirm(
                        "Hay cambios sin guardar en la sesión. ¿Salir de esta sesión? Los cambios sin guardar se perderán. Las solicitudes pendientes y los borradores guardados se conservarán.");
    }

    private void refreshIdentity() {
        Views.async(
                () -> new PharmacyProfileService(db).load(),
                profile ->
                        profile.ifPresent(
                                p -> {
                                    pharmacyName.setText("SIGF / " + p.name());
                                    logo.setImage(
                                            p.logo() == null
                                                    ? null
                                                    : new javafx.scene.image.Image(
                                                            new java.io.ByteArrayInputStream(
                                                                    p.logo()),
                                                            42,
                                                            42,
                                                            true,
                                                            true));
                                }),
                Views::error);
    }
}
