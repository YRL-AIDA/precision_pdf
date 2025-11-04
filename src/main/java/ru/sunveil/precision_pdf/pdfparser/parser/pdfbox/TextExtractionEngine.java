package ru.sunveil.precision_pdf.pdfparser.parser.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.commons.lang3.StringUtils;
import ru.sunveil.precision_pdf.pdfparser.model.PdfTextChunk;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;


import java.io.IOException;
import java.util.*;

/**
 * Upgraded TextExtractionEngine based on PDContentExtractor logic
 * Extracts chunks, lines and words in single pass without intermediate TextChunk
 */
public class TextExtractionEngine extends PDFTextStripper {

    private List<PdfTextChunk> cachedChunks;
    private List<TextLine> cachedLines;
    private List<Word> cachedWords;
    private boolean extractionCompleted = false;

    private int currentPageNumber;
    private float pageHeight;
    private int order;

    private boolean newLineStarted;
    private float lineStartX, lineStartY;
    private float lineEndX, lineEndY;
    private StringBuilder lineText;
    private List<Word> currentLineWords;

    // Word extraction state
    private StringBuilder currentWordText;
    private List<TextPosition> currentWordPositions;
    private float wordStartX, wordStartY, wordEndX, wordEndY;

    private final char[] whitespaces = {
            '\u0020', '\u00A0', '\u0009', '\n', '\u000B', '\u000C', '\r',
            '\u0085', '\u1680', '\u2000', '\u2001', '\u2002', '\u2003',
            '\u2004', '\u2005', '\u2006', '\u2007', '\u2008', '\u2009',
            '\u200A', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000',
            '\u180E', '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF'
    };

    public TextExtractionEngine() throws IOException {
        super();
        initialize();
    }

    private void initialize() {
        this.cachedChunks = new ArrayList<>();
        this.cachedLines = new ArrayList<>();
        this.cachedWords = new ArrayList<>();

        this.lineText = new StringBuilder();
        this.currentLineWords = new ArrayList<>();

        this.currentWordText = new StringBuilder();
        this.currentWordPositions = new ArrayList<>();

        this.setSortByPosition(true);
        this.setShouldSeparateByBeads(false);
    }

    /**
     * Extract all text entities in single pass and cache results
     */
    private void extractAllInOnePass(PDDocument document) throws IOException {
        if (extractionCompleted) {
            return;
        }

        resetExtractionState();

        int pageCount = document.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            PDPage page = document.getPage(i);
            currentPageNumber = i + 1;
            PDRectangle pageSize = page.getMediaBox();
            pageHeight = pageSize.getHeight();

            setStartPage(currentPageNumber);
            setEndPage(currentPageNumber);

            order = -1;
            newLineStarted = false;
            lineText.setLength(0);
            currentLineWords.clear();
            currentWordText.setLength(0);
            currentWordPositions.clear();

            super.getText(document);

            finalizeCurrentWord();
            finalizeCurrentLine();
        }

        extractionCompleted = true;
    }

    /**
     * Process text string with positions - main extraction logic
     */
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        if (StringUtils.isBlank(string) || textPositions.isEmpty()) {
            return;
        }

        order++;

        if (!newLineStarted) {
            newLineStarted = true;
            TextPosition firstTp = textPositions.get(0);
            lineStartX = firstTp.getXDirAdj();
            lineStartY = convertToTopLeftY(firstTp.getYDirAdj() - firstTp.getHeightDir());
        }

        lineText.append(string);

        PdfTextChunk chunk = createPdfTextChunk(string, textPositions);
        cachedChunks.add(chunk);

        extractWordsFromTextPositions(textPositions);

        TextPosition lastTp = textPositions.get(textPositions.size() - 1);
        lineEndX = lastTp.getXDirAdj() + lastTp.getWidthDirAdj();
        lineEndY = convertToTopLeftY(lastTp.getYDirAdj());
    }

    /**
     * Extract words from text positions using PDContentExtractor logic
     */
    private void extractWordsFromTextPositions(List<TextPosition> textPositions) {
        if (textPositions.isEmpty()) return;

        textPositions.sort(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj));

        for (TextPosition tp : textPositions) {
            String charText = getUnicodeText(tp);

            if (!isWordChar(tp)) {
                finalizeCurrentWord();
                continue;
            }

            if (currentWordText.length() == 0) {
                currentWordText.append(charText);
                currentWordPositions.add(tp);
                updateWordBoundingBox(tp);
            } else {
                if (isNearCurrentWord(tp)) {
                    currentWordText.append(charText);
                    currentWordPositions.add(tp);
                    updateWordBoundingBox(tp);
                } else {
                    finalizeCurrentWord();
                    currentWordText.append(charText);
                    currentWordPositions.add(tp);
                    updateWordBoundingBox(tp);
                }
            }
        }
    }

    /**
     * Check if text position can be part of a word
     */
    private boolean isWordChar(TextPosition tp) {
        String text = getUnicodeText(tp);
        if (text == null || text.isEmpty()) return false;
        if (containsWhitespace(text)) return false;

        // Check font
        if (tp.getFont() == null) {
            return false;
        }

        return true;
    }

    /**
     * Check if text position is near current word
     */
    private boolean isNearCurrentWord(TextPosition tp) {
        float charLeft = tp.getXDirAdj();
        float spaceWidth = tp.getWidthOfSpace();
        return Math.abs(wordEndX - charLeft) < spaceWidth * 0.4;
    }

    /**
     * Update word bounding box with new text position
     */
    private void updateWordBoundingBox(TextPosition tp) {
        float left = tp.getXDirAdj();
        float top = convertToTopLeftY(tp.getYDirAdj() - tp.getHeightDir());
        float right = tp.getXDirAdj() + tp.getWidthDirAdj();
        float bottom = convertToTopLeftY(tp.getYDirAdj());

        if (currentWordText.length() == 1) {
            wordStartX = left;
            wordStartY = top;
            wordEndX = right;
            wordEndY = bottom;
        } else {
            wordStartX = Math.min(wordStartX, left);
            wordStartY = Math.min(wordStartY, top);
            wordEndX = Math.max(wordEndX, right);
            wordEndY = Math.max(wordEndY, bottom);
        }
    }

    /**
     * Finalize current word and add to lists
     */
    private void finalizeCurrentWord() {
        if (currentWordText.length() > 0 && !currentWordPositions.isEmpty()) {
            Word word = createWord(currentWordText.toString(), currentWordPositions);
            cachedWords.add(word);
            currentLineWords.add(word);

            currentWordText.setLength(0);
            currentWordPositions.clear();
        }
    }

    /**
     * Finalize current line and add to lists
     */
    private void finalizeCurrentLine() {
        if (newLineStarted && !currentLineWords.isEmpty()) {
            // Create TextLine object
            TextLine line = createTextLine();
            cachedLines.add(line);

            // Reset line state
            newLineStarted = false;
            lineText.setLength(0);
            currentLineWords.clear();
        }
    }

    /**
     * Create PdfTextChunk from text and positions
     */
    private PdfTextChunk createPdfTextChunk(String text, List<TextPosition> positions) {
        BoundingBox bbox = calculateBoundingBox(positions);

        PdfTextChunk chunk = new PdfTextChunk();
        chunk.setPageNumber(currentPageNumber);
        chunk.setBoundingBox(bbox);
        chunk.setText(text);
        chunk.setStyle(extractTextStyle(positions.get(0)));

        return chunk;
    }

    /**
     * Create Word from text and positions
     */
    private Word createWord(String text, List<TextPosition> positions) {
        BoundingBox bbox = new BoundingBox(wordStartX, wordStartY, wordEndX - wordStartX, wordEndY - wordStartY);
        TextPosition styleTp = positions.get(positions.size() / 2);

        Word word = new Word();
        word.setPageNumber(currentPageNumber);
        word.setBoundingBox(bbox);
        word.setText(text);
        word.setFontName(styleTp.getFont().getName());
        word.setFontSize(styleTp.getFontSizeInPt());
        word.setConfidence(calculateConfidence(styleTp));

        return word;
    }

    /**
     * Create TextLine from current line state
     */
    private TextLine createTextLine() {
        BoundingBox bbox = new BoundingBox(lineStartX, lineStartY, lineEndX - lineStartX, lineEndY - lineStartY);
        float lineHeight = calculateLineHeight(currentLineWords);

        TextLine line = new TextLine();
        line.setPageNumber(currentPageNumber);
        line.setBoundingBox(bbox);
        line.setText(lineText.toString());
        line.setWords(new ArrayList<>(currentLineWords));
        line.setLineHeight(lineHeight);

        return line;
    }

    /**
     * Calculate bounding box from text positions
     */
    private BoundingBox calculateBoundingBox(List<TextPosition> positions) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (TextPosition tp : positions) {
            float left = tp.getXDirAdj();
            float top = convertToTopLeftY(tp.getYDirAdj() - tp.getHeightDir());
            float right = tp.getXDirAdj() + tp.getWidthDirAdj();
            float bottom = convertToTopLeftY(tp.getYDirAdj());

            minX = Math.min(minX, left);
            minY = Math.min(minY, top);
            maxX = Math.max(maxX, right);
            maxY = Math.max(maxY, bottom);
        }

        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Convert PDF Y coordinate (bottom-left origin) to top-left origin
     */
    private float convertToTopLeftY(float pdfY) {
        return pageHeight - pdfY;
    }

    /**
     * Extract text style from text position
     */
    private String extractTextStyle(TextPosition textPosition) {
        StringBuilder style = new StringBuilder();
        PDFont pdFont = textPosition.getFont();
        if (pdFont != null) {
            String name = textPosition.getFont().getName();
            style.append("font-family:").append(name).append(";");
            style.append("font-size:").append(textPosition.getFontSizeInPt()).append("pt;");

            boolean isBold = isBoldFont(textPosition);
            boolean isItalic = isItalicFont(textPosition);
            style.append("font-weight:").append(isBold ? "bold" : "normal").append(";");
            style.append("font-style:").append(isItalic ? "italic" : "normal").append(";");
        }
        // Added bold/italic detection from PDContentExtractor, not sure if everything correct

        return style.toString();
    }

    private boolean isBoldFont(TextPosition textPosition) {
        if (textPosition.getFont() == null) return false;

        String fontName = textPosition.getFont().getName();
        if (fontName == null) return false;

        final boolean isBoldFontName = fontName.toLowerCase().contains("bold");

        // Check font descriptor for force bold
        boolean isForceBold = false;
        try {
            if (textPosition.getFont().getFontDescriptor() != null) {
                isForceBold = textPosition.getFont().getFontDescriptor().isForceBold();
            }
        } catch (Exception e) {
            // Ignore font descriptor errors
        }

        if (isForceBold) {
            return true;
        } else if (isBoldFontName) {
            return true;
        } else {
            RenderingMode rm = getGraphicsState().getTextState().getRenderingMode();
            return rm == RenderingMode.FILL_STROKE;
        }
    }

    /**
     * Determine if font is italic (logic from PDContentExtractor.getFont())
     */
    private boolean isItalicFont(TextPosition textPosition) {
        if (textPosition.getFont() == null) return false;

        String fontName = textPosition.getFont().getName();
        if (fontName == null) return false;

        final boolean isItalicFontName = fontName.toLowerCase().contains("italic");
        final boolean isObliqueFontName = fontName.toLowerCase().contains("oblique");

        try {
            if (textPosition.getFont().getFontDescriptor() != null) {
                PDFontDescriptor desc = textPosition.getFont().getFontDescriptor();
                boolean italicFromDescriptor = desc.isItalic();

                if (italicFromDescriptor) {
                    return true;
                } else if (isObliqueFontName) {
                    return true;
                } else if (isItalicFontName) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore font descriptor errors
        }

        return isItalicFontName || isObliqueFontName;
    }
    /**
     * Calculate confidence score for text recognition
     */
    private float calculateConfidence(TextPosition textPosition) {
        float confidence = 1.0f;

        if (textPosition.getFontSizeInPt() < 6) {
            confidence *= 0.8f;
        }

        String fontName = textPosition.getFont().getName().toLowerCase();
        if (fontName.contains("symbol") || fontName.contains("zapf")) {
            confidence *= 0.9f;
        }

        return Math.max(0.1f, Math.min(1.0f, confidence));
    }

    /**
     * Calculate average line height from words
     */
    private float calculateLineHeight(List<Word> words) {
        if (words.isEmpty()) {
            return 0f;
        }

        float totalHeight = 0f;
        for (Word word : words) {
            totalHeight += word.getBoundingBox().getHeight();
        }

        return totalHeight / words.size();
    }

    /**
     * Check if text contains whitespace
     */
    private boolean containsWhitespace(String text) {
        for (char c : text.toCharArray()) {
            for (char ws : whitespaces) {
                if (c == ws) return true;
            }
        }
        return false;
    }

    /**
     * Get Unicode text with special character handling (from PDContentExtractor)
     */
    private String getUnicodeText(TextPosition tp) {
        String text = tp.getUnicode();
        // Handle special characters like in PDContentExtractor
        if (tp.getUnicode().equals("\uF0B7")) {
            text = "•";
        }
        return text;
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        finalizeCurrentWord();
        finalizeCurrentLine();
        super.writeLineSeparator();
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        finalizeCurrentWord();
        finalizeCurrentLine();
        super.endPage(page);
    }

    private void resetExtractionState() {
        cachedChunks.clear();
        cachedLines.clear();
        cachedWords.clear();
        extractionCompleted = false;

        lineText.setLength(0);
        currentLineWords.clear();
        currentWordText.setLength(0);
        currentWordPositions.clear();
        newLineStarted = false;
    }

    public List<PdfTextChunk> extractTextChunks(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new ArrayList<>(cachedChunks);
    }

    public List<TextLine> extractTextLines(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new ArrayList<>(cachedLines);
    }

    public List<Word> extractWords(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new ArrayList<>(cachedWords);
    }

    public void clear() {
        resetExtractionState();
    }

    public int getTextChunkCount() {
        return cachedChunks.size();
    }

    public int getTextLineCount() {
        return cachedLines.size();
    }

    public int getWordCount() {
        return cachedWords.size();
    }
}