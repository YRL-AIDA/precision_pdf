package ru.sunveil.precision_pdf.pdfparser.export;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExporterFactory {

    private final List<Exporter> exporters;

    public ExporterFactory(List<Exporter> exporters) {
        this.exporters = exporters;
    }

    public Exporter getExporter(ExportFormat format) {
        return exporters.stream()
                .filter(Exporter -> Exporter.supportsFormat(format))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported export format: " + format));
    }
}