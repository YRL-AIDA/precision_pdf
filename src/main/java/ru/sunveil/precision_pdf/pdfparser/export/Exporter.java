package ru.sunveil.precision_pdf.pdfparser.export;

import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;

public interface Exporter {
    String export(PdfDocument document, ExportFormat format);
    boolean supportsFormat(ExportFormat format);
}
