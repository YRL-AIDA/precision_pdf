package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;

@Data
public class ExtractionResult {
    private boolean success;
    private String message;
    private PdfDocument document;
    private String error;
    private long processingTimeMs;

    public ExtractionResult() {}

    public ExtractionResult(boolean success, String message, PdfDocument document, long processingTimeMs) {
        this.success = success;
        this.message = message;
        this.document = document;
        this.processingTimeMs = processingTimeMs;
    }

    public ExtractionResult(boolean success, String error, long processingTimeMs) {
        this.success = success;
        this.error = error;
        this.processingTimeMs = processingTimeMs;
    }

    public static ExtractionResult success(PdfDocument document, long processingTimeMs) {
        return new ExtractionResult(true, "PDF успешно обработан", document, processingTimeMs);
    }

    public static ExtractionResult error(String error, long processingTimeMs) {
        return new ExtractionResult(false, error, processingTimeMs);
    }
}