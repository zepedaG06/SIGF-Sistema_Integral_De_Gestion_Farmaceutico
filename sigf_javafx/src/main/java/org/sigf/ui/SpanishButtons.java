package org.sigf.ui;

import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

public final class SpanishButtons {
    private SpanishButtons() {}

    public static final ButtonType CLOSE = new ButtonType("Cerrar", ButtonData.CANCEL_CLOSE);
    public static final ButtonType CANCEL = new ButtonType("Cancelar", ButtonData.CANCEL_CLOSE);
    public static final ButtonType OK = new ButtonType("Aceptar", ButtonData.OK_DONE);
    public static final ButtonType YES = new ButtonType("Sí", ButtonData.YES);
    public static final ButtonType NO = new ButtonType("No", ButtonData.NO);
}
