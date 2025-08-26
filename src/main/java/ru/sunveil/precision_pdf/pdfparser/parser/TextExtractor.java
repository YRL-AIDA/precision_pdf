package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfTextChunk;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface TextExtractor {
    List<PdfTextChunk> extractTextChunks(PDDocument document) throws IOException;
    List<TextLine> extractTextLines(PDDocument document) throws IOException;
    List<Word> extractWords(PDDocument document) throws IOException;
}
