package ru.sunveil.precision_pdf.pdfparser.util;

import ru.sunveil.precision_pdf.pdfparser.model.PDFFont;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.PdfTextChunk;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link TextLine} and {@link Word} entities from plain text and a block bounding box
 * when the upstream format has no per-word geometry (e.g. ODL blocks).
 */
public final class BlockTextLayoutBuilder {

    private static final String DEFAULT_FONT_NAME = "unknown";

    private BlockTextLayoutBuilder() {
    }

    public static List<TextLine> buildTextLines(
            int pageNumber,
            BoundingBox blockBBox,
            String text,
            int baseOrder,
            int[] wordOrderRef,
            List<Word> wordsOut) {
        if (blockBBox == null || !blockBBox.isValid() || text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);
        List<String> lineTexts = new ArrayList<>();
        for (String raw : rawLines) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                lineTexts.add(trimmed);
            }
        }
        if (lineTexts.isEmpty()) {
            return List.of();
        }

        int lineCount = lineTexts.size();
        float lineHeight = blockBBox.getHeight() / lineCount;
        float blockTop = blockBBox.getY() + blockBBox.getHeight();
        List<TextLine> textLines = new ArrayList<>();

        for (int i = 0; i < lineCount; i++) {
            String lineText = lineTexts.get(i);
            float lineTop = blockTop - i * lineHeight;
            float lineY = lineTop - lineHeight;
            BoundingBox lineBBox = new BoundingBox(blockBBox.getX(), lineY, blockBBox.getWidth(), lineHeight);

            List<Word> lineWords = buildWordsForLine(pageNumber, lineBBox, lineText, wordOrderRef);
            if (wordsOut != null) {
                wordsOut.addAll(lineWords);
            }

            float fontSize = Math.max(lineHeight * 0.85f, 8f);
            PDFFont font = new PDFFont(DEFAULT_FONT_NAME, fontSize, lineHeight, false, false);
            TextLine line = new TextLine(
                    pageNumber,
                    lineBBox,
                    lineText,
                    lineWords,
                    Math.max(lineHeight, 1f),
                    baseOrder + i,
                    font,
                    Color.BLACK,
                    Math.max(lineHeight * 0.3f, 1f)
            );
            textLines.add(line);
        }

        return textLines;
    }

    public static void appendTextChunk(PdfPage page, int baseOrder, List<TextLine> textLines) {
        if (textLines == null || textLines.isEmpty()) {
            return;
        }
        BoundingBox chunkBBox = textLines.get(0).getBoundingBox().copy();
        StringBuilder chunkText = new StringBuilder();
        for (TextLine line : textLines) {
            chunkBBox = chunkBBox.union(line.getBoundingBox());
            if (!chunkText.isEmpty()) {
                chunkText.append('\n');
            }
            chunkText.append(line.getText());
        }
        PdfTextChunk chunk = new PdfTextChunk(
                page.getPageNumber(), chunkBBox, chunkText.toString(), textLines, null);
        chunk.setOrder(baseOrder);
        page.getPdfTextChunks().add(chunk);
    }

    private static List<Word> buildWordsForLine(
            int pageNumber, BoundingBox lineBBox, String lineText, int[] wordOrderRef) {
        String[] tokens = lineText.split("\\s+");
        List<Word> words = new ArrayList<>();
        if (tokens.length == 0) {
            return words;
        }

        int totalChars = 0;
        for (String token : tokens) {
            totalChars += token.length();
        }
        if (totalChars == 0) {
            return words;
        }

        float fontSize = Math.max(lineBBox.getHeight() * 0.85f, 8f);
        PDFFont font = new PDFFont(DEFAULT_FONT_NAME, fontSize, lineBBox.getHeight(), false, false);
        float x = lineBBox.getX();

        for (String token : tokens) {
            float wordWidth = lineBBox.getWidth() * (token.length() / (float) totalChars);
            BoundingBox wordBBox = new BoundingBox(x, lineBBox.getY(), wordWidth, lineBBox.getHeight());
            Word word = new Word(
                    pageNumber,
                    wordBBox,
                    token,
                    1.0f,
                    DEFAULT_FONT_NAME,
                    fontSize,
                    wordOrderRef[0],
                    font,
                    Color.BLACK,
                    Math.max(lineBBox.getHeight() * 0.3f, 1f)
            );
            words.add(word);
            wordOrderRef[0]++;
            x += wordWidth;
        }
        return words;
    }
}
