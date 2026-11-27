package org.sigf.reports;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.export.ooxml.*;
import net.sf.jasperreports.engine.type.*;
import net.sf.jasperreports.export.*;
import org.sigf.domain.ReportGrid;
import org.sigf.services.PharmacyProfileService.Profile;

/** Diseño Jasper compartido; los datos nunca se interpolan como expresiones Java. */
public final class JasperReportExporter {
    private JasperReportExporter() {}

    private static final Set<String> NUMERIC =
            Set.of(
                    "subtotal",
                    "tax",
                    "total",
                    "importe",
                    "recibido",
                    "cambio",
                    "base",
                    "iva",
                    "cantidad",
                    "unidades",
                    "valor",
                    "sale_price",
                    "minimum_stock",
                    "vendible",
                    "tasa_iva",
                    "tax_rate",
                    "quantity");

    public static void export(
            ReportGrid grid,
            Profile profile,
            String title,
            String filters,
            String actor,
            String format,
            Path destination)
            throws Exception {
        if (grid.rows().isEmpty())
            throw new IllegalArgumentException("No hay resultados para exportar");
        if (grid.rows().size() > 25000)
            throw new IllegalArgumentException(
                    "El reporte supera 25 000 filas. Reduzca el período o aplique filtros");
        if (!Set.of("pdf", "xlsx", "docx", "csv").contains(format))
            throw new IllegalArgumentException("Formato desconocido");
        JasperDesign design = new JasperDesign();
        design.setName("SIGF");
        design.setPageWidth(842);
        design.setPageHeight(595);
        design.setOrientation(OrientationEnum.LANDSCAPE);
        design.setLeftMargin(24);
        design.setRightMargin(24);
        design.setTopMargin(20);
        design.setBottomMargin(20);
        design.setColumnWidth(794);
        JRDesignStyle style = new JRDesignStyle();
        style.setName("Base");
        style.setDefault(true);
        style.setFontName("DejaVu Sans");
        style.setFontSize(8f);
        design.addStyle(style);
        JRDesignBand heading = new JRDesignBand();
        heading.setHeight(154);
        String identity =
                profile == null
                        ? "Farmacia sin perfil registrado"
                        : profile.name()
                                + "\n"
                                + profile.legalName()
                                + " · RUC: "
                                + profile.ruc()
                                + "\n"
                                + profile.address()
                                + "\nTel.: "
                                + profile.phone()
                                + " · "
                                + profile.email();
        heading.addElement(flowText(design, "IDENTITY", identity, 90, 0, 704, 57, 10, true));
        if (profile != null && profile.logo() != null && profile.logo().length > 0) {
            var image = ImageIO.read(new ByteArrayInputStream(profile.logo()));
            if (image != null) {
                JRDesignParameter parameter = new JRDesignParameter();
                parameter.setName("LOGO");
                parameter.setValueClass(java.awt.Image.class);
                design.addParameter(parameter);
                JRDesignImage logo = new JRDesignImage(design);
                logo.setX(0);
                logo.setY(0);
                logo.setWidth(76);
                logo.setHeight(57);
                logo.setScaleImage(ScaleImageEnum.RETAIN_SHAPE);
                logo.setExpression(new JRDesignExpression("$P{LOGO}"));
                heading.addElement(logo);
            }
        }
        heading.addElement(text(title, 0, 62, 794, 23, 15, true));
        heading.addElement(flowText(design, "FILTERS", filters, 0, 88, 794, 38, 8, false));
        String total = "";
        int totalIndex = grid.columns().indexOf("total");
        if (totalIndex < 0) totalIndex = grid.columns().indexOf("importe");
        if (totalIndex < 0) totalIndex = grid.columns().indexOf("iva");
        if (totalIndex >= 0) {
            BigDecimal sum = BigDecimal.ZERO;
            int state = grid.columns().indexOf("status");
            if (state < 0) state = grid.columns().indexOf("estado");
            for (var row : grid.rows())
                if (state < 0 || row.get(state).equals("CONFIRMED"))
                    sum = sum.add(new BigDecimal(row.get(totalIndex)));
            total = " · Importe del reporte: C$ " + sum.setScale(2);
        }
        heading.addElement(
                text(
                        "Generado: "
                                + LocalDateTime.now().withNano(0)
                                + " · Usuario: "
                                + actor
                                + " · Filas: "
                                + grid.rows().size()
                                + total,
                        0,
                        128,
                        794,
                        24,
                        8,
                        false));
        design.setTitle(heading);
        JRDesignBand columns = new JRDesignBand();
        columns.setHeight(32);
        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(32);
        detail.setSplitType(SplitTypeEnum.STRETCH);
        int size = grid.columns().size(), width = 794 / size;
        for (int i = 0; i < size; i++) {
            String name = grid.columns().get(i), fieldName = "c" + i;
            boolean number = NUMERIC.contains(name);
            JRDesignField field = new JRDesignField();
            field.setName(fieldName);
            field.setValueClass(number ? BigDecimal.class : String.class);
            design.addField(field);
            int w = i == size - 1 ? 794 - i * width : width;
            var label = text(label(name), i * width, 0, w, 32, 8, true);
            label.setMode(ModeEnum.OPAQUE);
            label.setBackcolor(new Color(225, 235, 219));
            columns.addElement(label);
            JRDesignTextField cell = new JRDesignTextField();
            cell.setX(i * width);
            cell.setY(0);
            cell.setWidth(w - 8);
            cell.setHeight(30);
            cell.setExpression(new JRDesignExpression("$F{" + fieldName + "}"));
            cell.setBlankWhenNull(true);
            cell.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
            if (number) {
                cell.setPattern(
                        Set.of(
                                                "subtotal",
                                                "tax",
                                                "total",
                                                "importe",
                                                "recibido",
                                                "cambio",
                                                "base",
                                                "iva",
                                                "sale_price")
                                        .contains(name)
                                ? "#,##0.00"
                                : "#,##0.##");
                cell.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
            }
            detail.addElement(cell);
        }
        design.setColumnHeader(columns);
        ((JRDesignSection) design.getDetailSection()).addBand(detail);
        JRDesignBand footer = new JRDesignBand();
        footer.setHeight(26);
        footer.addElement(
                text(
                        "SIGF · Reporte administrativo · Importes en córdobas (C$)",
                        0,
                        0,
                        660,
                        25,
                        8,
                        false));
        JRDesignTextField page = new JRDesignTextField();
        page.setX(680);
        page.setY(0);
        page.setWidth(114);
        page.setHeight(25);
        page.setExpression(new JRDesignExpression("\"Página \" + $V{PAGE_NUMBER}"));
        footer.addElement(page);
        design.setPageFooter(footer);
        var parameters = new HashMap<String, Object>();
        parameters.put("IDENTITY", identity);
        parameters.put("FILTERS", filters);
        parameters.put(JRParameter.REPORT_LOCALE, new Locale("es", "NI"));
        parameters.put(
                JRParameter.IS_IGNORE_PAGINATION, (format.equals("xlsx") || format.equals("csv")));
        if (profile != null && profile.logo() != null)
            parameters.put("LOGO", ImageIO.read(new ByteArrayInputStream(profile.logo())));
        JRDataSource source =
                new JRDataSource() {
                    int row = -1;

                    public boolean next() {
                        return ++row < grid.rows().size();
                    }

                    public Object getFieldValue(JRField field) {
                        String value =
                                grid.rows()
                                        .get(row)
                                        .get(Integer.parseInt(field.getName().substring(1)));
                        return field.getValueClass() == BigDecimal.class
                                ? (value.isBlank() ? null : new BigDecimal(value))
                                : (format.equals("csv") ? csvSafe(display(value)) : display(value));
                    }
                };
        JasperPrint print =
                JasperFillManager.fillReport(
                        JasperCompileManager.compileReport(design), parameters, source);
        Path temp =
                Files.createTempFile(
                        destination.toAbsolutePath().getParent(), ".sigf-reporte-", ".tmp");
        try {
            if (format.equals("pdf"))
                JasperExportManager.exportReportToPdfFile(print, temp.toString());
            else if (format.equals("csv")) {
                var csv = new net.sf.jasperreports.engine.export.JRCsvExporter();
                csv.setExporterInput(new SimpleExporterInput(print));
                csv.setExporterOutput(new SimpleWriterExporterOutput(temp.toFile(), "UTF-8"));
                var config = new SimpleCsvExporterConfiguration();
                config.setEscapeFormula(true);
                config.setWriteBOM(true);
                csv.setConfiguration(config);
                csv.exportReport();
            } else {
                net.sf.jasperreports.engine.JRAbstractExporter exporter =
                        format.equals("xlsx") ? new JRXlsxExporter() : new JRDocxExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(temp.toFile()));
                if (exporter instanceof JRXlsxExporter xlsx) {
                    SimpleXlsxReportConfiguration cfg = new SimpleXlsxReportConfiguration();
                    cfg.setDetectCellType(true);
                    cfg.setRemoveEmptySpaceBetweenRows(true);
                    cfg.setWhitePageBackground(false);
                    xlsx.setConfiguration(cfg);
                }
                exporter.exportReport();
            }
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static JRDesignTextField flowText(
            JasperDesign design,
            String name,
            String value,
            int x,
            int y,
            int w,
            int h,
            int size,
            boolean bold)
            throws JRException {
        var parameter = new JRDesignParameter();
        parameter.setName(name);
        parameter.setValueClass(String.class);
        design.addParameter(parameter);
        var text = new JRDesignTextField();
        text.setX(x);
        text.setY(y);
        text.setWidth(w);
        text.setHeight(h);
        text.setFontSize((float) size);
        text.setBold(bold);
        text.setForecolor(new Color(22, 70, 57));
        text.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
        text.setPositionType(PositionTypeEnum.FLOAT);
        text.setExpression(new JRDesignExpression("$P{" + name + "}"));
        return text;
    }

    private static JRDesignStaticText text(
            String value, int x, int y, int w, int h, int size, boolean bold) {
        var text = new JRDesignStaticText();
        text.setPositionType(PositionTypeEnum.FLOAT);
        text.setText(value);
        text.setX(x);
        text.setY(y);
        text.setWidth(w);
        text.setHeight(h);
        text.setFontSize((float) size);
        text.setBold(bold);
        text.setForecolor(new Color(22, 70, 57));
        return text;
    }

    public static String label(String name) {
        return switch (name) {
            case "created_at" -> "Fecha";
            case "voided_at" -> "Fecha de anulación";
            case "void_reason" -> "Motivo de anulación";
            case "method", "metodo" -> "Forma de pago";
            case "descripcion" -> "Descripción";
            case "blocked" -> "Bloqueado";
            case "active" -> "Activo";
            case "lot" -> "Lote";
            case "username" -> "Usuario";
            case "actor_id" -> "Responsable N.º";
            case "action" -> "Acción";
            case "details" -> "Detalle";
            case "tax" -> "IVA";
            case "status", "estado" -> "Estado";
            case "sale_price" -> "Precio sin IVA";
            case "name" -> "Nombre";
            case "code" -> "Código";
            case "expires_on" -> "Vencimiento";
            case "quantity" -> "Cantidad";
            case "minimum_stock" -> "Mínimo";
            case "tax_rate" -> "Tasa IVA";
            default ->
                    name.substring(0, 1).toUpperCase(new Locale("es", "NI"))
                            + name.substring(1).replace('_', ' ');
        };
    }

    private static String csvSafe(String value) {
        String trimmed = value.stripLeading();
        return !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0 ? "'" + value : value;
    }

    public static String display(String value) {
        return switch (value) {
            case "CASH" -> "Efectivo";
            case "CARD" -> "Tarjeta";
            case "TRANSFER" -> "Transferencia";
            case "CONFIRMED" -> "Confirmada";
            case "VOID" -> "Anulada";
            case "true" -> "Sí";
            case "false" -> "No";
            case "SALE" -> "Venta";
            case "PURCHASE" -> "Compra";
            case "ADJUSTMENT" -> "Ajuste";
            case "SALE_VOID" -> "Anulación de venta";
            default -> value;
        };
    }
}
