package ru.sunveil.precision_pdf.pdfparser.exceptions;

public class PdfParseException extends RuntimeException {
    public PdfParseException(String message) {
        super(message);
    }

    public PdfParseException(String message, Throwable cause) {
        super(message, cause);
    }
}