package org.sigf;

import static org.junit.jupiter.api.Assertions.*;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.sigf.domain.ReportGrid;
import org.sigf.reports.JasperReportExporter;
import org.sigf.services.PharmacyProfileService.Profile;

class ReportExporterTest {
    @Test
    void longMetadataAndSpreadsheetTextRemainIntact() throws Exception {
        Path directory = Path.of("target", "report-verification");
        Files.createDirectories(directory);
        var profile =
                new Profile(
                        "Farmacia larga " + "nombre ".repeat(20),
                        "Titular " + "empresa ".repeat(20),
                        "J1234567890123",
                        "Dirección " + "ubicación ".repeat(45) + " FIN_DIRECCION",
                        "88888888",
                        "correo@ejemplo.test",
                        null);
        var grid = new ReportGrid(List.of("producto", "total"), List.of(List.of("=1+1", "12.50")));
        for (String format : List.of("pdf", "xlsx", "csv"))
            JasperReportExporter.export(
                    grid,
                    profile,
                    "Prueba de texto",
                    "Filtro " + "cliente ".repeat(100) + " FIN_FILTROS",
                    "Usuario",
                    format,
                    directory.resolve("texto." + format));
        try (var reader = new PdfReader(directory.resolve("texto.pdf").toString())) {
            var extractor = new PdfTextExtractor(reader);
            String text = "";
            for (int page = 1; page <= reader.getNumberOfPages(); page++)
                text += extractor.getTextFromPage(page);
            assertTrue(text.contains("FIN_DIRECCION"));
            assertTrue(text.contains("FIN_FILTROS"));
            assertTrue(text.contains("=1+1"));
        }
        try (var workbook =
                new XSSFWorkbook(Files.newInputStream(directory.resolve("texto.xlsx")))) {
            boolean found = false;
            for (var row : workbook.getSheetAt(0))
                for (var cell : row) {
                    assertNotEquals(CellType.FORMULA, cell.getCellType());
                    if (cell.toString().equals("=1+1")) found = true;
                }
            assertTrue(found);
        }
        assertTrue(Files.readString(directory.resolve("texto.csv")).contains("'=1+1"));
    }

    @Test
    void exportsBrandedMultipageDocumentsAndNumericSpreadsheet() throws Exception {
        Path directory = Path.of("target", "report-verification");
        Files.createDirectories(directory);
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new java.awt.Color(14, 94, 66));
        graphics.fillRect(0, 0, 64, 64);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(26, 8, 12, 48);
        graphics.fillRect(8, 26, 48, 12);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        Profile profile =
                new Profile(
                        "Farmacia Prueba Ñ",
                        "Titular Ejemplo",
                        "J1234567890123",
                        "Managua, Nicaragua",
                        "8888-8888",
                        "correo@ejemplo.test",
                        bytes.toByteArray());
        var rows = new ArrayList<List<String>>();
        for (int i = 0; i < 90; i++)
            rows.add(
                    List.of(
                            "Cliente " + i,
                            "Medicamento con descripción y presentación de prueba",
                            "12.50",
                            "CONFIRMED"));
        ReportGrid grid = new ReportGrid(List.of("cliente", "producto", "total", "status"), rows);
        for (String format : List.of("pdf", "xlsx", "docx"))
            JasperReportExporter.export(
                    grid,
                    profile,
                    "Ventas por período",
                    "Del 01/11/2026 al 26/11/2026 · Banco BAC",
                    "Administrador",
                    format,
                    directory.resolve("ventas." + format));
        try (var reader = new PdfReader(directory.resolve("ventas.pdf").toString())) {
            assertTrue(reader.getNumberOfPages() > 1);
            String first = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(first.contains("Farmacia Prueba Ñ"));
            assertTrue(first.contains("J1234567890123"));
            assertTrue(first.contains("Banco BAC"));
            assertTrue(first.contains("1125.00"));
            assertTrue(first.contains("Cliente 0"));
            assertNotNull(
                    reader.getPageN(1)
                            .getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES)
                            .getAsDict(com.lowagie.text.pdf.PdfName.XOBJECT));
        }
        try (var workbook =
                new XSSFWorkbook(Files.newInputStream(directory.resolve("ventas.xlsx")))) {
            int numeric = 0;
            boolean brand = false;
            for (var row : workbook.getSheetAt(0))
                for (var cell : row) {
                    if (cell.getCellType() == CellType.NUMERIC
                            && cell.getNumericCellValue() == 12.5) numeric++;
                    if (cell.toString().contains("Farmacia Prueba Ñ")) brand = true;
                }
            assertEquals(90, numeric);
            assertTrue(brand);
            assertFalse(workbook.getAllPictures().isEmpty());
        }
        try (var zip = new ZipFile(directory.resolve("ventas.docx").toFile())) {
            String xml =
                    new String(
                            zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("Farmacia Prueba Ñ"));
            assertTrue(xml.contains("Cliente 89"));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().startsWith("word/media/")));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        JasperReportExporter.export(
                                new ReportGrid(grid.columns(), List.of()),
                                profile,
                                "Vacío",
                                "",
                                "",
                                "pdf",
                                directory.resolve("empty.pdf")));
    }
}
