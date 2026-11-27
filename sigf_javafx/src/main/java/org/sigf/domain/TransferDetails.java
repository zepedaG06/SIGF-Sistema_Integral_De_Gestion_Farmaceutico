package org.sigf.domain;

public record TransferDetails(String bank, String reference, String description) {
    public TransferDetails {
        bank = bank == null ? "" : bank.trim();
        reference = reference == null ? "" : reference.trim();
        description = description == null ? "" : description.trim();
        if (bank.isEmpty()
                || bank.length() > 100
                || reference.isEmpty()
                || reference.length() > 120
                || description.length() > 500) {
            throw new IllegalArgumentException(
                    "Seleccione el banco e ingrese una referencia (máximo 120 caracteres) y una descripción de hasta 500 caracteres");
        }
    }
}
