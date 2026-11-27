package org.sigf.services;

import java.nio.file.Path;
import java.time.LocalDate;
import org.sigf.config.Database;
import org.sigf.reports.JasperReportExporter;
import org.sigf.repositories.ReportRepository;
import org.sigf.security.Security;

public final class ReportExportService {
    private final Database db;

    public ReportExportService(Database db) {
        this.db = db;
    }

    public void export(
            Security.User user,
            String name,
            String title,
            LocalDate from,
            LocalDate to,
            String search,
            ReportRepository.Filters filters,
            String description,
            String format,
            Path file)
            throws Exception {
        var repository = new ReportRepository(db, user);
        var grid = repository.filtered(name, from, to, search, filters, 0, 0, false);
        var profile =
                new PharmacyProfileService(db)
                        .load()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Complete los datos de la farmacia en Configuración antes de exportar"));
        try (var c = db.connection()) {
            Security.verifyReport(c, user, name);
        }
        JasperReportExporter.export(
                grid,
                profile,
                title,
                "Del " + from + " al " + to + " · " + description + " · Búsqueda: " + search,
                user.name(),
                format,
                file);
    }
}
