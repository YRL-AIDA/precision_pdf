package ru.sunveil.precision_pdf.pdfparser.parser.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.PdfTextChunk;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

/**
 * Engine for extracting text content from PDF documents using PDFBox library.
 * Extends PDFTextStripper to process PDF text content with precision positioning.
 */
public class TextExtractionEngine extends PDFTextStripper {

    private List<PdfTextChunk> textChunks;
    private List<TextLine> textLines;
    private List<Word> words;

    private int currentPageNumber;
    private PDPage currentPage;
    private float pageHeight;

    private TextLine currentLine;
    private StringBuilder currentLineText;
    private List<Word> currentLineWords;

    private Word currentWord;
    private StringBuilder currentWordText;
    private List<TextPosition> currentWordPositions;

    /**
     * Constructs a new TextExtractionEngine instance.
     * Initializes data structures for storing extracted text elements.
     *
     * @throws IOException if an error occurs during engine initialization
     */
    public TextExtractionEngine() throws IOException {
        super();
        this.textChunks = new ArrayList<>();
        this.textLines = new ArrayList<>();
        this.words = new ArrayList<>();

        this.currentLineText = new StringBuilder();
        this.currentLineWords = new ArrayList<>();

        this.currentWordText = new StringBuilder();
        this.currentWordPositions = new ArrayList<>();

        // Configure text extraction settings
        this.setSortByPosition(true);
        this.setShouldSeparateByBeads(false);
        this.setAddMoreFormatting(true);
    }

    /**
     * Extracts all text content from the provided PDF document.
     * Processes each page sequentially and collects text elements with precise positioning.
     *
     * @param document the PDF document to extract text from
     * @return list of extracted PdfTextChunk objects representing text content
     * @throws IOException if an error occurs during document processing
     */
    public List<PdfTextChunk> extractTextChunks(PDDocument document) throws IOException {
        resetExtractionState();

        int i = 0;
        for (PDPage page: document.getPages()) {
            currentPageNumber = i + 1;
            PDRectangle pageSize = page.getMediaBox();
            pageHeight = pageSize.getHeight();
            setStartPage(currentPageNumber);
            setEndPage(currentPageNumber);

            // Process the page text content
            super.getText(document);

            // Finalize any remaining line and word
            finalizeCurrentWord();
            finalizeCurrentLine();
        }

        return new ArrayList<>(textChunks);
    }

    /**
     * Extracts text lines from the provided PDF document.
     * Provides structured line-level text extraction with positioning information.
     *
     * @param document the PDF document to extract text lines from
     * @return list of extracted TextLine objects
     * @throws IOException if an error occurs during document processing
     */
    public List<TextLine> extractTextLines(PDDocument document) throws IOException {
        extractTextChunks(document); // This will populate textLines as well
        return new ArrayList<>(textLines);
    }

    /**
     * Extracts individual words from the provided PDF document.
     * Provides word-level text extraction with precise positioning and formatting information.
     *
     * @param document the PDF document to extract words from
     * @return list of extracted Word objects
     * @throws IOException if an error occurs during document processing
     */
    public List<Word> extractWords(PDDocument document) throws IOException {
        extractTextChunks(document); // This will populate words as well
        return new ArrayList<>(words);
    }

    /**
     * Resets the extraction state between document processing.
     * Clears all temporary data structures and prepares for new extraction.
     */
    private void resetExtractionState() {
        textChunks.clear();
        textLines.clear();
        words.clear();

        currentLineText.setLength(0);
        currentLineWords.clear();

        currentWordText.setLength(0);
        currentWordPositions.clear();

        currentLine = null;
        currentWord = null;
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
        if (textPositions == null || textPositions.isEmpty()) {
            return;
        }

        // Process each text position for word and line extraction
        for (TextPosition textPosition : textPositions) {
            processTextPosition(textPosition);
        }

        // Create text chunk for the entire string
        createTextChunk(text, textPositions);
    }

    /**
     * Processes an individual text position for word and line extraction.
     * Handles word boundaries and line transitions.
     *
     * @param textPosition the TextPosition object to process
     */
    public void processTextPosition(TextPosition textPosition) {
        String character = textPosition.getUnicode();

        // Handle whitespace characters as word separators
        if (Character.isWhitespace(character.charAt(0))) {
            finalizeCurrentWord();
            return;
        }

        // Check if this starts a new word (position gap indicates word break)
        if (currentWord != null) {
            float currentWordEnd = currentWord.getBoundingBox().getX() + currentWord.getBoundingBox().getWidth();
            float spaceWidth = textPosition.getWidthOfSpace();

            if (textPosition.getXDirAdj() - currentWordEnd > spaceWidth * 0.5) {
                finalizeCurrentWord();
            }
        }

        // Add character to current word
        currentWordText.append(character);
        currentWordPositions.add(textPosition);

        // Create or update current word
        if (currentWord == null) {
            createNewWord(textPosition);
        } else {
            updateCurrentWord(textPosition);
        }
    }

    /**
     * Creates a new word starting at the given text position.
     *
     * @param textPosition the starting TextPosition for the new word
     */
    private void createNewWord(TextPosition textPosition) {
        BoundingBox bbox = createBoundingBox(textPosition);

        currentWord = new Word();
        currentWord.setBoundingBox(bbox);
        currentWord.setFontName(textPosition.getFont().getName());
        currentWord.setFontSize(textPosition.getFontSizeInPt());
        currentWord.setPageNumber(currentPageNumber);

        // Calculate confidence based on font properties
        float confidence = calculateConfidence(textPosition);
        currentWord.setConfidence(confidence);
    }

    /**
     * Updates the current word with additional text position.
     * Expands the word's bounding box to include the new character.
     *
     * @param textPosition the TextPosition to add to the current word
     */
    private void updateCurrentWord(TextPosition textPosition) {
        BoundingBox currentBbox = currentWord.getBoundingBox();
        BoundingBox newCharBbox = createBoundingBox(textPosition);

        // Expand the word bounding box to include the new character
        float newX = Math.min(currentBbox.getX(), newCharBbox.getX());
        float newY = Math.min(currentBbox.getY(), newCharBbox.getY());
        float newRight = Math.max(currentBbox.getX() + currentBbox.getWidth(),
                newCharBbox.getX() + newCharBbox.getWidth());
        float newBottom = Math.max(currentBbox.getY() + currentBbox.getHeight(),
                newCharBbox.getY() + newCharBbox.getHeight());

        currentWord.getBoundingBox().setX(newX);
        currentWord.getBoundingBox().setY(newY);
        currentWord.getBoundingBox().setWidth(newRight - newX);
        currentWord.getBoundingBox().setHeight(newBottom - newY);
    }

    /**
     * Finalizes the current word and adds it to the line and word list.
     */
    private void finalizeCurrentWord() {
        if (currentWord != null && currentWordText.length() > 0) {
            currentWord.setText(currentWordText.toString());

            // Add word to current line
            currentLineWords.add(currentWord);
            words.add(currentWord);

            // Update line text
            if (currentLineText.length() > 0) {
                currentLineText.append(" ");
            }
            currentLineText.append(currentWordText.toString());

            // Reset word state
            currentWordText.setLength(0);
            currentWordPositions.clear();
            currentWord = null;
        }
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
     * Finalizes the current line and adds it to the line list.
     */
    private void finalizeCurrentLine() {
        if (currentLineWords.isEmpty()) {
            return;
        }

        // Calculate line bounding box from constituent words
        BoundingBox lineBbox = calculateLineBoundingBox(currentLineWords);

        currentLine = new TextLine();
        currentLine.setWords(new ArrayList<>(currentLineWords));
        currentLine.setLineHeight(calculateLineHeight(currentLineWords));
        currentLine.setBoundingBox(lineBbox);
        currentLine.setPageNumber(currentPageNumber);

        textLines.add(currentLine);

        // Reset line state
        currentLineText.setLength(0);
        currentLineWords.clear();
        currentLine = null;
    }

    /**
     * Creates a text chunk from the processed text and positions.
     *
     * @param text the complete text string
     * @param textPositions the list of TextPosition objects
     */
    private void createTextChunk(String text, List<TextPosition> textPositions) {
        if (textPositions.isEmpty()) {
            return;
        }

        // Calculate chunk bounding box
        BoundingBox chunkBbox = calculateChunkBoundingBox(textPositions);

        PdfTextChunk textChunk = new PdfTextChunk();
        textChunk.setBoundingBox(chunkBbox);
        textChunk.setText(text);
        textChunk.setPageNumber(currentPageNumber);
        textChunk.setStyle(extractTextStyle(textPositions.get(0)));

        textChunks.add(textChunk);
    }

    /**
     * Creates a bounding box from a TextPosition object.
     * Converts PDF coordinate system to standard top-left origin.
     *
     * @param textPosition the TextPosition to create bounding box from
     * @return BoundingBox object with converted coordinates
     */
    private BoundingBox createBoundingBox(TextPosition textPosition) {
        // Convert PDF coordinates (bottom-left origin) to top-left origin
        float x = textPosition.getXDirAdj();
        float y = pageHeight - textPosition.getYDirAdj(); // Flip Y coordinate
        float width = textPosition.getWidthDirAdj();
        float height = textPosition.getHeightDir();

        return new BoundingBox(x, y, width, height);
    }

    /**
     * Calculates the bounding box for a line from its constituent words.
     *
     * @param words the list of words in the line
     * @return BoundingBox encompassing all words in the line
     */
    private BoundingBox calculateLineBoundingBox(List<Word> words) {
        if (words.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0);
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxRight = Float.MIN_VALUE;
        float maxBottom = Float.MIN_VALUE;

        for (Word word : words) {
            BoundingBox bbox = word.getBoundingBox();
            minX = Math.min(minX, bbox.getX());
            minY = Math.min(minY, bbox.getY());
            maxRight = Math.max(maxRight, bbox.getX() + bbox.getWidth());
            maxBottom = Math.max(maxBottom, bbox.getY() + bbox.getHeight());
        }

        return new BoundingBox(minX, minY, maxRight - minX, maxBottom - minY);
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
     * Calculates the bounding box for a text chunk from text positions.
     *
     * @param textPositions the list of TextPosition objects
     * @return BoundingBox encompassing all text positions
     */
    private BoundingBox calculateChunkBoundingBox(List<TextPosition> textPositions) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxRight = Float.MIN_VALUE;
        float maxBottom = Float.MIN_VALUE;

        for (TextPosition tp : textPositions) {
            BoundingBox bbox = createBoundingBox(tp);
            minX = Math.min(minX, bbox.getX());
            minY = Math.min(minY, bbox.getY());
            maxRight = Math.max(maxRight, bbox.getX() + bbox.getWidth());
            maxBottom = Math.max(maxBottom, bbox.getY() + bbox.getHeight());
        }

        return new BoundingBox(minX, minY, maxRight - minX, maxBottom - minY);
    }

    /**
     * Extracts text style information from a TextPosition.
     *
     * @param textPosition the TextPosition to analyze
     * @return string representation of text style
     */
    private String extractTextStyle(TextPosition textPosition) {
        StringBuilder style = new StringBuilder();

        // Extract font information
        style.append("font-family:").append(textPosition.getFont().getName()).append(";");
        style.append("font-size:").append(textPosition.getFontSizeInPt()).append("pt;");

        // TODO: Add more style attributes (bold, italic, color) as needed
        // This would require additional analysis of font properties

        return style.toString();
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

        // Reduce confidence for very small fonts
        if (textPosition.getFontSizeInPt() < 6) {
            confidence *= 0.8f;
        }

        // Reduce confidence for uncommon font types
        String fontName = textPosition.getFont().getName().toLowerCase();
        if (fontName.contains("symbol") || fontName.contains("zapf")) {
            confidence *= 0.9f;
        }

        return Math.max(0.1f, Math.min(1.0f, confidence));
    }

    /**
     * Clears all extracted data and resets the engine state.
     * Useful for reusing the engine instance for multiple documents.
     */
    public void clear() {
        resetExtractionState();
    }

    /**
     * Returns the number of text chunks extracted in the last operation.
     *
     * @return count of extracted text chunks
     */
    public int getTextChunkCount() {
        return textChunks.size();
    }

    /**
     * Returns the number of text lines extracted in the last operation.
     *
     * @return count of extracted text lines
     */
    public int getTextLineCount() {
        return textLines.size();
    }

    /**
     * Returns the number of words extracted in the last operation.
     *
     * @return count of extracted words
     */
    public int getWordCount() {
        return words.size();
    }
}