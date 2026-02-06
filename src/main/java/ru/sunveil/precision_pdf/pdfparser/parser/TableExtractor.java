package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.TableExtractionResult;

import java.io.IOException;
import java.util.List;

public interface TableExtractor {
    TableExtractionResult extractTables(PdfPage page) throws IOException;
}