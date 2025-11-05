package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;

import java.util.List;

/**
 * Вспомогательный класс для возврата всех текстовых сущностей
 */
@Data
public class TextExtractionResult {
    private List<PdfTextChunk> textChunks;
    private List<TextLine> textLines;
    private List<Word> words;
}
