package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;

import java.util.List;

/**
 * Вспомогательный класс для возврата всех текстовых сущностей
 */
@Data
public class TextExtractionResult {
    private final List<PdfTextChunk> textChunks;
    private final List<TextLine> textLines;
    private final List<Word> words;
}
