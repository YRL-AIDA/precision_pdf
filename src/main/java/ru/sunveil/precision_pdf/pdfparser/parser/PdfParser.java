package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfMetadata;

import java.io.File;
import java.io.IOException;

public interface PdfParser {
    PdfDocument parse(File pdfFile, ExtractionConfig config) throws IOException;
    PdfMetadata extractMetadata(PDDocument document);
}
