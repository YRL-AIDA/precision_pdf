package ru.sunveil.precision_pdf.pdfparser.parser.pdfbox;

import io.micrometer.common.util.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import ru.sunveil.precision_pdf.pdfparser.table.VisibleRulingExtractor;
import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Engine for extracting text content from PDF documents using PDFBox library.
 * Extends PDFTextStripper to process PDF text content with precision positioning.
 */
public class TextExtractionEngine extends PDFTextStripper {

    private List<PdfTextChunk> cachedChunks;
    private List<TextLine> cachedLines;
    private List<Word> cachedWords;
    private boolean extractionCompleted = false;

    private int currentPageNumber;
    private float pageHeight;
    private int order;

    // precomputed visible rulings by page (pageNumber -> rulings)
    private Map<Integer, List<Ruling>> pageRulings = new HashMap<>();

    private boolean newLineStarted;
    private float lineStartX, lineStartY;
    private float lineEndX, lineEndY;
    private StringBuilder lineText;
    private List<Word> currentLineWords;

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

    /**
     * Constructs a new TextExtractionEngine instance.
     * Initializes internal data structures for storing extracted text entities.
     *
     * @throws IOException if an error occurs during initialization
     */
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

        this.setShouldSeparateByBeads(false);
    }

    /**
     * Extracts all text entities from a PDF document in a single pass.
     * Populates cached chunks, lines, and words lists with full text data.
     *
     * @param document the PDF document to extract text from
     * @throws IOException if an error occurs during document processing
     */
    private void extractAllInOnePass(PDDocument document) throws IOException {
        if (extractionCompleted) {
            return;
        }

        resetExtractionState();
        precomputePageRulings(document);

        int pageCount = document.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            PDPage page = document.getPage(i);
            currentPageNumber = i + 1;
            PDRectangle pageSize = page.getMediaBox();
            pageHeight = pageSize.getHeight();
            order = -1;
            setStartPage(currentPageNumber);
            setEndPage(currentPageNumber);

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
     * Processes a string of text with associated text positions.
     * Overrides the base method to handle text extraction with precise positioning.
     *
     * @param text the text string being processed
     * @param textPositions the list of TextPosition objects representing individual characters
     * @throws IOException if an error occurs during text processing
     */
    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (StringUtils.isBlank(text) || textPositions.isEmpty()) {
            return;
        }

        for (Object rulings : pageRulings.values()){

        }

        order++;

        if (!newLineStarted) {
            newLineStarted = true;
            TextPosition firstTp = textPositions.get(0);
            // store start X and converted Y (we will normalize when building bbox)
            lineStartX = firstTp.getXDirAdj();
            lineStartY = convertToTopLeftY(firstTp.getYDirAdj() - firstTp.getHeightDir());
        }


        PdfTextChunk chunk = createPdfTextChunk(text, textPositions);
        cachedChunks.add(chunk);

        TextPosition lastTp = textPositions.get(textPositions.size() - 1);
        lineEndX = lastTp.getXDirAdj() + lastTp.getWidthDirAdj();
        lineEndY = convertToTopLeftY(lastTp.getYDirAdj());

        extractWordsFromTextPositions(textPositions);
    }

    /**
     * Extracts words from a list of text positions.
     * Splits text into words and updates bounding boxes and styles.
     *
     * @param textPositions list of TextPosition objects
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

            if (currentWordText.isEmpty()) {
                currentWordText.append(charText);
                currentWordPositions.add(tp);
                updateWordBoundingBoxInitial(tp);
            } else {
                if (isNearCurrentWord(tp)) {
                    currentWordText.append(charText);
                    currentWordPositions.add(tp);
                    updateWordBoundingBoxExtend(tp);
                } else {
                    finalizeCurrentWord();
                    currentWordText.append(charText);
                    currentWordPositions.add(tp);
                    updateWordBoundingBoxInitial(tp);
                }
            }
        }
        finalizeCurrentWord();
    }

    /**
     * Checks if a text position represents a valid word character.
     *
     * @param tp the TextPosition to check
     * @return true if the character can be part of a word, false otherwise
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
     * Determines if a TextPosition is near the current word to be considered part of it.
     *
     * @param tp the TextPosition to check
     * @return true if the character is near the current word
     */
    private boolean isNearCurrentWord(TextPosition tp) {
        float charLeft = tp.getXDirAdj();
        float spaceWidth = tp.getWidthOfSpace();
        return Math.abs(wordEndX - charLeft) < spaceWidth * 0.4;
    }

    /**
     * Updates bounding box when adding the first character to a word.
     *
     * @param tp the TextPosition representing the first character
     */
    private void updateWordBoundingBoxInitial(TextPosition tp) {
        float left = tp.getXDirAdj();
        float tpTopPdf = tp.getYDirAdj() - tp.getHeightDir();
        float tpBottomPdf = tp.getYDirAdj();

        float top = convertToTopLeftY(tpTopPdf);
        float bottom = convertToTopLeftY(tpBottomPdf);

        wordStartX = left;
        wordEndX = tp.getXDirAdj() + tp.getWidthDirAdj();

        wordStartY = Math.min(top, bottom);
        wordEndY = Math.max(top, bottom);
    }

    /**
     * Extends the current word bounding box with an additional character.
     *
     * @param tp the TextPosition representing the new character
     */
    private void updateWordBoundingBoxExtend(TextPosition tp) {
        float left = tp.getXDirAdj();
        float tpTopPdf = tp.getYDirAdj() - tp.getHeightDir();
        float tpBottomPdf = tp.getYDirAdj();

        float top = convertToTopLeftY(tpTopPdf);
        float bottom = convertToTopLeftY(tpBottomPdf);

        float right = tp.getXDirAdj() + tp.getWidthDirAdj();

        wordEndX = Math.max(wordEndX, right);
        wordStartX = Math.min(wordStartX, left);

        float minY = Math.min(wordStartY, Math.min(top, bottom));
        float maxY = Math.max(wordEndY, Math.max(top, bottom));
        wordStartY = minY;
        wordEndY = maxY;
    }

    /**
     * Finalizes the current word, adds it to the word list and the current line.
     */
    private void finalizeCurrentWord() {
        if (currentWordText.length() > 0 && !currentWordPositions.isEmpty()) {
            Word word = createWord(currentWordText.toString(), currentWordPositions, order);
            cachedWords.add(word);
            currentLineWords.add(word);
            if (newLineStarted) {
                if (lineText.length() > 0) {
                    lineText.append(' ');
                }
                lineText.append(word.getText());
            }
            currentWordText.setLength(0);
            currentWordPositions.clear();
            wordStartX = wordStartY = wordEndX = wordEndY = 0f;
        }
    }

    /**
     * Finalize current line and add to lists
     */
    private void finalizeCurrentLine() {
        if (!newLineStarted || currentLineWords.isEmpty()) {
            newLineStarted = false;
            lineText.setLength(0);
            currentLineWords.clear();
            return;
        }
        List<Word> remaining = new ArrayList<>(currentLineWords);
        while (!remaining.isEmpty()) {
            int splitIndex = -1;
            for (int i = 1; i < remaining.size(); i++) {
                Word prev = remaining.get(i - 1);
                Word curr = remaining.get(i);
                List<Ruling> rulings = pageRulings.get(currentPageNumber);
                if (isSeparatedByVerticalRuling(prev.getBoundingBox(), curr.getBoundingBox(), rulings)) {
                    splitIndex = i;
                    break;
                }
                float lastY = prev.getBoundingBox().getY();
                float thisY = curr.getBoundingBox().getY();
                float heightThreshold = prev.getBoundingBox().getHeight() * 0.6f;
                if (Math.abs(thisY - lastY) > heightThreshold) {
                    splitIndex = i;
                    break;
                }
            }
            if (splitIndex == -1) {
                createAndStoreLine(remaining);
                break;
            } else {
                List<Word> first = new ArrayList<>(remaining.subList(0, splitIndex));
                createAndStoreLine(first);
                remaining = new ArrayList<>(remaining.subList(splitIndex, remaining.size()));
            }
        }
        newLineStarted = false;
        lineText.setLength(0);
        currentLineWords.clear();
    }

    private void createAndStoreLine(List<Word> words) {
        if (words.isEmpty()) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        StringBuilder txt = new StringBuilder();
        for (Word w : words) {
            BoundingBox b = w.getBoundingBox();
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            maxX = Math.max(maxX, b.getX() + b.getWidth());
            maxY = Math.max(maxY, b.getY() + b.getHeight());
            if (txt.length() > 0) txt.append(' ');
            txt.append(w.getText());
        }
        float width = maxX - minX;
        float height = maxY - minY;
        BoundingBox bbox = new BoundingBox(minX, minY, width, height);
        TextLine line = new TextLine();
        line.setPageNumber(currentPageNumber);
        line.setBoundingBox(bbox);
        line.setText(txt.toString());
        line.setWords(new ArrayList<>(words));
        line.setLineHeight(calculateLineHeight(words));
        line.setOrder(order);
        Word first = words.get(0);
        line.setFont(first.getFont());
        line.setColor(first.getColor());
        line.setSpaceWidth(first.getSpaceWidth());
        cachedLines.add(line);
    }

    /**
     * Creates a PdfTextChunk from text and text positions.
     *
     * @param text the text string
     * @param positions list of TextPosition objects
     * @return new PdfTextChunk with bounding box and style information
     */
    private PdfTextChunk createPdfTextChunk(String text, List<TextPosition> positions) {
        BoundingBox bbox = calculateBoundingBox(positions);

        PdfTextChunk chunk = new PdfTextChunk();
        chunk.setPageNumber(currentPageNumber);
        chunk.setBoundingBox(bbox);
        chunk.setText(text);
        chunk.setOrder(order);
        chunk.setStyle(extractTextStyle(positions.get(0)));

        return chunk;
    }

    /**
     * Creates a Word object from text and text positions.
     *
     * @param text the word text
     * @param positions list of TextPosition objects for the word
     * @return Word object with bounding box and style
     */
    private Word createWord(String text, List<TextPosition> positions, int order) {
        float left = wordStartX;
        float width = wordEndX - wordStartX;
        float topY = Math.min(wordStartY, wordEndY);
        float height = Math.abs(wordEndY - wordStartY);

        if (width <= 0 || height <= 0) {
            BoundingBox bbox = calculateBoundingBox(positions);
            left = bbox.getX();
            width = bbox.getWidth();
            topY = bbox.getY();
            height = bbox.getHeight();
        }

        TextPosition styleTp = positions.get(Math.max(0, positions.size() / 2));

        PDFFont pdfFont = new PDFFont();
        pdfFont.setName(styleTp.getFont().getName());
        pdfFont.setFontSize(styleTp.getFontSizeInPt());
        pdfFont.setBold(isBoldFont(styleTp));
        pdfFont.setItalic(isItalicFont(styleTp));

        Color color = getColor(styleTp);

        Word word = new Word();
        word.setPageNumber(currentPageNumber);
        word.setBoundingBox(new BoundingBox(left, topY, width, height));
        word.setText(text);
        word.setFontName(styleTp.getFont().getName());
        word.setFontSize(styleTp.getFontSizeInPt());
        word.setConfidence(calculateConfidence(styleTp));
        word.setOrder(order);
        word.setFont(pdfFont);
        word.setColor(color);
        word.setSpaceWidth(styleTp.getWidthOfSpace());

        return word;
    }

    /**
     * Creates a TextLine from the current line state, including words and bounding box.
     *
     * @return TextLine representing the current line
     */
    private TextLine createTextLine() {
        // normalize Y coordinates: lineStartY may be > or < lineEndY depending on conversion; compute minY/height
        float minY = Math.min(lineStartY, lineEndY);
        float height = Math.abs(lineEndY - lineStartY);
        float left = lineStartX;
        float width = lineEndX - lineStartX;

        // Prefer computing bbox from words when available
        if (!currentLineWords.isEmpty()) {
            float minLeft = Float.MAX_VALUE, maxRight = -Float.MAX_VALUE;
            float minTop = Float.MAX_VALUE, maxBottom = -Float.MAX_VALUE;
            for (Word w : currentLineWords) {
                BoundingBox b = w.getBoundingBox();
                minLeft = Math.min(minLeft, b.getX());
                maxRight = Math.max(maxRight, b.getX() + b.getWidth());
                minTop = Math.min(minTop, b.getY());
                maxBottom = Math.max(maxBottom, b.getY() + b.getHeight());
            }
            if (minLeft != Float.MAX_VALUE) {
                left = Math.min(left, minLeft);
                float right = Math.max(lineEndX, maxRight);
                width = right - left;
            }
            if (minTop != Float.MAX_VALUE) {
                minY = Math.min(minY, minTop);
                float bottom = Math.max(lineEndY, maxBottom);
                height = bottom - minY;
            }
        } else {
            // act like before
            if (width <= 0 || height <= 0) {
                float minLeft = Float.MAX_VALUE, maxRight = Float.MIN_VALUE;
                float minTop = Float.MAX_VALUE, maxBottom = Float.MIN_VALUE;
                for (Word w : currentLineWords) {
                    BoundingBox b = w.getBoundingBox();
                    minLeft = Math.min(minLeft, b.getX());
                    maxRight = Math.max(maxRight, b.getX() + b.getWidth());
                    minTop = Math.min(minTop, b.getY());
                    maxBottom = Math.max(maxBottom, b.getY() + b.getHeight());
                }
                if (minLeft != Float.MAX_VALUE) {
                    left = Math.min(left, minLeft);
                    width = (maxRight - left);
                }
                if (minTop != Float.MAX_VALUE) {
                    minY = Math.min(minY, minTop);
                    height = Math.max(height, maxBottom - minY);
                }
            }
        }

        BoundingBox bbox = new BoundingBox(left, minY, width, height);
        float lineHeight = calculateLineHeight(currentLineWords);

        Word firstWord = currentLineWords.get(0);

        TextLine line = new TextLine();
        line.setPageNumber(currentPageNumber);
        line.setBoundingBox(bbox);
        line.setText(lineText.toString());
        line.setWords(new ArrayList<>(currentLineWords));
        line.setLineHeight(lineHeight);
        line.setOrder(order);
        line.setFont(firstWord.getFont());
        line.setColor(firstWord.getColor());
        line.setSpaceWidth(firstWord.getSpaceWidth());

        return line;
    }

    /**
     * get color of element
     *
     * @param textPosition the TextPosition to get color from
     * @return Color of element
     */
    private Color getColor(TextPosition textPosition) {
        Color color = new Color(0);
        try {
            RenderingMode rm = getGraphicsState().getTextState().getRenderingMode();
            if (rm == RenderingMode.FILL || rm == RenderingMode.NEITHER) {
                PDColor pdColor = getGraphicsState().getNonStrokingColor();
                color = new Color(pdColor.toRGB());
            }
            if (rm == RenderingMode.STROKE) {
                PDColor pdColor = getGraphicsState().getStrokingColor();
                color = new Color(pdColor.toRGB());
            }
        } catch (Exception e) {
            color = new Color(0);
        }
        return color;
    }

    /**
     * Calculates a bounding box covering all given text positions.
     *
     * @param positions list of TextPosition objects
     * @return BoundingBox enclosing all positions
     */
    private BoundingBox calculateBoundingBox(List<TextPosition> positions) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        for (TextPosition tp : positions) {
            float left = tp.getXDirAdj();
            float top = convertToTopLeftY(tp.getYDirAdj() - tp.getHeightDir());
            float right = tp.getXDirAdj() + tp.getWidthDirAdj();
            float bottom = convertToTopLeftY(tp.getYDirAdj());

            // normalize top/bottom for this tp
            float tpMinY = Math.min(top, bottom);
            float tpMaxY = Math.max(top, bottom);

            minX = Math.min(minX, left);
            minY = Math.min(minY, tpMinY);
            maxX = Math.max(maxX, right);
            maxY = Math.max(maxY, tpMaxY);
        }

        // Fallback if nothing found
        if (minX == Float.MAX_VALUE) {
            minX = 0;
            minY = 0;
            maxX = 0;
            maxY = 0;
        }

        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Converts PDF bottom-left Y coordinate to top-left coordinate.
     *
     * @param pdfY Y coordinate in PDF space
     * @return Y coordinate in top-left origin
     */
    private float convertToTopLeftY(float pdfY) {
        return pageHeight - pdfY;
    }

    /**
     * Extracts style information from a TextPosition (font, size, bold/italic).
     *
     * @param textPosition the TextPosition to analyze
     * @return CSS-style string representing text style
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

    /**
     * Determine if font is bold
     */
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
     * Determine if font is italic
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
     * Calculates confidence score for text recognition.
     * Based on font properties and character recognition quality.
     *
     * @param textPosition the TextPosition to evaluate
     * @return confidence score between 0 and 1
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
     * Calculates the typical line height from constituent words.
     *
     * @param words the list of words in the line
     * @return average line height
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
     * Checks whether the given text contains any whitespace characters.
     * The method compares each character in the text against a predefined list
     * of Unicode whitespace symbols (including spaces, tabs, and non-breaking spaces).
     *
     * @param text the text string to check
     * @return true if the text contains at least one whitespace character, false otherwise
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
     * Returns the Unicode text for a given TextPosition, handling special characters.
     *
     * @param tp the TextPosition to extract text from
     * @return the Unicode string representation of the character
     */
    private String getUnicodeText(TextPosition tp) {
        String text = tp.getUnicode();
        // Handle special characters like in PDContentExtractor
        if (tp.getUnicode().equals("\uF0B7")) {
            text = "•";
        }
        return text;
    }

    /**
     * Handles line separator events.
     * Finalizes the current line and prepares for a new line.
     *
     * @throws IOException if an error occurs during line processing
     */
    @Override
    protected void writeLineSeparator() throws IOException {
        finalizeCurrentWord();
        finalizeCurrentLine();
        super.writeLineSeparator();
    }

    /**
     * Handles page end events.
     * Finalizes any remaining text elements on the current page.
     *
     * @param page the current PDF page
     * @throws IOException if an error occurs during page processing
     */
    @Override
    protected void endPage(PDPage page) throws IOException {
        finalizeCurrentWord();
        finalizeCurrentLine();
        super.endPage(page);
    }

    /**
     * Resets the extraction state, clearing all cached chunks, lines, and words.
     * Prepares the engine for a new extraction session.
     */
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

    /**
     * Extracts text chunks, lines, and words from a PDF document.
     *
     * @param document PDF document to process
     * @return TextExtractionResult containing chunks, lines, and words
     * @throws IOException if an error occurs during extraction
     */
    public TextExtractionResult extractText(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new TextExtractionResult(this.cachedChunks, this.cachedLines, this.cachedWords);
    }

    /**
     * Extracts all text chunks from the given PDF document.
     * Performs a single-pass extraction and caches the results.
     *
     * @param document the PDF document to extract text from
     * @return a list of PdfTextChunk objects representing the extracted text chunks
     * @throws IOException if an error occurs during text extraction
     */
    public List<PdfTextChunk> extractTextChunks(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new ArrayList<>(cachedChunks);
    }

    /**
     * Extracts all text lines from the given PDF document.
     * Performs a single-pass extraction and caches the results.
     *
     * @param document the PDF document to extract text lines from
     * @return a list of TextLine objects representing the extracted text lines
     * @throws IOException if an error occurs during text extraction
     */
    public List<TextLine> extractTextLines(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new ArrayList<>(cachedLines);
    }

    /**
     * Extracts all words from the given PDF document.
     * Performs a single-pass extraction and caches the results.
     *
     * @param document the PDF document to extract words from
     * @return a list of Word objects representing the extracted words
     * @throws IOException if an error occurs during text extraction
     */
    public List<Word> extractWords(PDDocument document) throws IOException {
        extractAllInOnePass(document);
        return new ArrayList<>(cachedWords);
    }

    private boolean isSeparatedByVerticalRuling(BoundingBox bbox1, BoundingBox bbox2, List<Ruling> rulings) {
        if (rulings == null || rulings.isEmpty()) {
            return false;
        }

        // tolerance for floating point number
        final float tolerance = 0.5f;
        float leftBoxRight = Math.min(bbox1.getRight(), bbox2.getRight());
        float rightBoxLeft = Math.max(bbox1.getX(), bbox2.getX());

        if (leftBoxRight + tolerance >= rightBoxLeft) {
            return false;
        }

        float bbox1Top = bbox1.getTop();
        float bbox1Bottom = bbox1.getY();
        float bbox2Top = bbox2.getTop();
        float bbox2Bottom = bbox2.getY();

        float overlapTop = Math.max(bbox1Bottom, bbox2Bottom);
        float overlapBottom = Math.min(bbox1Top, bbox2Top);

        if (overlapTop >= overlapBottom) {
            return false;
        }

        for (Ruling ruling : rulings) {
            if (!ruling.isVertical()) {
                continue;
            }
            float rulingX = (float) ruling.getX1();
            float rulingY1 = (float) ruling.getY1();
            float rulingY2 = (float) ruling.getY2();

            float rulingTop = Math.max(rulingY1, rulingY2);
            float rulingBottom = Math.min(rulingY1, rulingY2);

            boolean horizontallyBetween = (rulingX + tolerance >= leftBoxRight && rulingX - tolerance <= rightBoxLeft);
            if (!horizontallyBetween) {
                continue;
            }

            boolean verticallyOverlaps = (rulingBottom <= overlapBottom + tolerance && rulingTop >= overlapTop - tolerance);

            if (verticallyOverlaps) {
                return true;
            }
        }

        return false;
    }


    public void clear() {
        resetExtractionState();
    }

    /**
     * Return visible rulings detected for given page (may be empty).
     */
    public List<Ruling> getVisibleRulingsForPage(int pageNumber) {
        return pageRulings.getOrDefault(pageNumber, Collections.emptyList());
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

    private void precomputePageRulings(PDDocument document) throws IOException {
        pageRulings.clear();
        VisibleRulingExtractor vre = new VisibleRulingExtractor(document);
        int pageCount = document.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            PDPage pdPage = document.getPage(i);
            try {
                List<Ruling> rulings = vre.extractVisibleRulings(pdPage);
                if (rulings != null) {
                    pageRulings.put(i + 1, new ArrayList<>(rulings));
                }
            } catch (Exception e) {
                // ignore per-page rulings errors
            }
        }
    }
}
