package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import ru.sunveil.precision_pdf.pdfparser.model.Table;

import java.io.IOException;
import java.util.List;

public interface TableExtractor {
    List<Table> extractTables(PDDocument document) throws IOException;
}