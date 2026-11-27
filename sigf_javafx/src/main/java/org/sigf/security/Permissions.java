package org.sigf.security;

import java.util.List;
import java.util.Set;

/** Matriz central de permisos; los servicios vuelven a validar la sesión en PostgreSQL. */
public final class Permissions {
    private Permissions() {}

    public static List<String> roles() {
        return List.of("ADMIN", "PHARMACIST", "CASHIER", "ACCOUNTANT");
    }

    public static String label(String role) {
        return switch (role) {
            case "ADMIN" -> "Administrador";
            case "PHARMACIST" -> "Farmacéutico";
            case "CASHIER" -> "Cajero";
            case "ACCOUNTANT" -> "Contador";
            default -> "Sin permisos";
        };
    }

    public static boolean allows(String role, Permission permission) {
        return switch (role) {
            case "ADMIN" -> true;
            case "PHARMACIST" ->
                    Set.of(
                                    Permission.SELL,
                                    Permission.CREATE_CUSTOMER,
                                    Permission.EDIT_CUSTOMER,
                                    Permission.CREATE_PRODUCT,
                                    Permission.RECEIVE_PURCHASE,
                                    Permission.ADJUST_STOCK,
                                    Permission.EDIT_PRODUCT_INFO,
                                    Permission.BLOCK_BATCH)
                            .contains(permission);
            case "CASHIER" ->
                    Set.of(Permission.SELL, Permission.CREATE_CUSTOMER, Permission.EDIT_CUSTOMER)
                            .contains(permission);
            default -> false;
        };
    }

    public static boolean canReadReport(String role, String name) {
        if (name.equals("general")) return roles().contains(role);
        return switch (role) {
            case "ADMIN" -> true;
            case "CASHIER" ->
                    Set.of(
                                    "sales",
                                    "suppliers",
                                    "customers",
                                    "batches",
                                    "movements",
                                    "stock",
                                    "top",
                                    "dashboard",
                                    "dashboard_low",
                                    "dashboard_expiring",
                                    "dashboard_recent",
                                    "internal_docs",
                                    "voided_docs")
                            .contains(name);
            case "PHARMACIST" ->
                    Set.of(
                                    "purchases",
                                    "suppliers",
                                    "sales",
                                    "customers",
                                    "stock",
                                    "batches",
                                    "movements",
                                    "dashboard_low",
                                    "dashboard_expiring",
                                    "dashboard_recent")
                            .contains(name);
            case "ACCOUNTANT" ->
                    Set.of(
                                    "sales",
                                    "purchases",
                                    "payments",
                                    "customers",
                                    "suppliers",
                                    "stock",
                                    "batches",
                                    "movements",
                                    "top",
                                    "tax_summary",
                                    "internal_docs",
                                    "voided_docs",
                                    "reconciliation")
                            .contains(name);
            default -> false;
        };
    }
}
