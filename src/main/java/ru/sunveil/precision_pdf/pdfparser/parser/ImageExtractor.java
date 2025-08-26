package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfImage;

import java.io.IOException;
import java.util.List;

public interface ImageExtractor {
    List<PdfImage> extractImages(PDDocument document) throws IOException;
}
