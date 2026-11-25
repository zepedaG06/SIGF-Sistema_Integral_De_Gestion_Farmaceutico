package org.sigf.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

final class DocumentPreview {
    private DocumentPreview() {}

    static void show(String title, String text, javafx.stage.Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.getDialogPane()
                .getStylesheets()
                .add(DocumentPreview.class.getResource("/css/style.css").toExternalForm());
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        TextArea preview = new TextArea(text);
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setStyle("-fx-font-family: Monospaced; -fx-font-size: 14px;");
        preview.setPrefSize(650, 540);
        Button print =
                Views.button(
                        "Imprimir",
                        "receipt",
                        () -> print(text, dialog.getDialogPane().getScene().getWindow()),
                        true);
        VBox content = new VBox(12, preview, print);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane()
                .getButtonTypes()
                .add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }

    static void print(String text, javafx.stage.Window owner) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Views.error(new IllegalStateException("No hay impresora disponible en el equipo"));
            return;
        }
        if (!job.showPrintDialog(owner)) {
            return;
        }
        var page = job.getJobSettings().getPageLayout();
        int height = Math.max(5, (int) (page.getPrintableHeight() / 14) - 2),
                width = Math.max(20, (int) (page.getPrintableWidth() / 6));
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            if (raw.isEmpty()) {
                lines.add("");
            }
            for (int i = 0; i < raw.length(); i += width) {
                lines.add(raw.substring(i, Math.min(i + width, raw.length())));
            }
        }
        for (int i = 0; i < lines.size(); i += height) {
            Text node =
                    new Text(
                            String.join(
                                    "\n", lines.subList(i, Math.min(i + height, lines.size()))));
            node.setFont(Font.font("Monospaced", 9));
            if (!job.printPage(node)) {
                job.cancelJob();
                Views.error(new IllegalStateException("No se pudo imprimir"));
                return;
            }
        }
        job.endJob();
    }
}
