package ru.sunveil.precision_pdf.pdfparser.table;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.contentstream.operator.color.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;
import java.util.List;

public class PDContentExtractor extends PDFTextStripper {

    private PDDocument document;          // A PDF document to process
    private final List<PdfTextChunk> chunks; // Original text chunks extracted from the PDF document
    private final List<TextEntity> chars;  // Characters extracted from original PDF chunks
    private final List<Word> words;  // Words composed from characters
    private final List<Word> tmpWords;
    private final List<TextLine> lines;  // Text lines composed from characters
    private final List<Ruling> rulings;   // Ruling lines from the PDF document

    private final List<Rectangle2D> frames;

    private int order; // An index of an original chunk in its PDF document
    private final char[] whitespaces;

    private boolean newLineStarted;
    private final Point2D.Float lineStartPoint;
    private final Point2D.Float lineEndPoint;
    private final StringBuilder lineText;

    private float minLeft = Float.MAX_VALUE;
    private float maxRight = Float.MIN_VALUE;

    private PdfPage currentPage;

    // Settings
    {
        addOperator(new SetStrokingColorSpace(this));
        addOperator(new SetStrokingDeviceCMYKColor(this));
        addOperator(new SetStrokingDeviceRGBColor(this));
        addOperator(new SetStrokingDeviceGrayColor(this));
        addOperator(new SetStrokingColor(this));
        addOperator(new SetStrokingColorN(this));
        addOperator(new SetNonStrokingColorSpace(this));
        addOperator(new SetNonStrokingDeviceCMYKColor(this));
        addOperator(new SetNonStrokingDeviceRGBColor(this));
        addOperator(new SetNonStrokingDeviceGrayColor(this));
        addOperator(new SetNonStrokingColor(this));
        addOperator(new SetNonStrokingColorN(this));
    }

    public PDContentExtractor(PDDocument document) throws IOException {
        this.document = document;
        chunks = new ArrayList<>(500);
        chars = new ArrayList<>(5000);
        words = new ArrayList<>(1000);
        tmpWords = new ArrayList<>(100);
        lines = new ArrayList<>(500);
        rulings = new ArrayList<>(200);
        frames = new ArrayList<>(5000);

        whitespaces = new char[]{
                '\u0020', //  space
                '\u00A0', //  no-break space
                '\u0009', //  character tabulation
                '\n',     //  line feed
                '\u000B', //  line tabulation
                '\u000C', //  form feed
                '\r',     //  carriage return
                '\u0085', //  next line
                '\u1680', //  ogham space mark
                '\u2000', //  en quad
                '\u2001', //  em quad
                '\u2002', //  en space
                '\u2003', //  em space
                '\u2004', //  three-per-em space
                '\u2005', //  four-per-em space
                '\u2006', //  six-per-em space
                '\u2007', //  figure space
                '\u2008', //  punctuation space
                '\u2009', //  thin space
                '\u200A', //  hair space
                '\u2028', //  line separator
                '\u2029', //  paragraph separator
                '\u202F', //  narrow no-break space
                '\u205F', //  medium mathematical space
                '\u3000', //  ideographic space
                '\u180E', //  mongolian vowel separator
                '\u200B', //  zero width space
                '\u200C', //  zero width non-joiner
                '\u200D', //  zero width joiner
                '\u2060', //  word joiner
                '\uFEFF'  //  zero width non-breaking
        };

        newLineStarted = false;
        lineStartPoint = new Point2D.Float(0,0);
        lineEndPoint = new Point2D.Float(0,0);
        lineText = new StringBuilder();
    }

    public float getMinLeft() {
        return minLeft;
    }

    public float getMaxRight() {
        return maxRight;
    }

    public void process(PdfPage page) {
        if (null == page) {
            throw new IllegalArgumentException("Page cannot be null");
        }
        else {
            currentPage = page;
            final int pageIndex = page.getIndex();
            try {
                stripPage(pageIndex);
//                page.addChunks(chunks);
                page.getPdfTextChunks().addAll(chunks);
//                page.addWords(words);
                page.getWords().addAll(words);
//                page.addLines(lines);
                page.getTextLines().addAll(lines);
//                page.addRulings(rulings);
                page.getRulings().addAll(rulings);
//                page.addFrames(frames);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                release();
            }
        }
    }

    private void release() {
        chunks.clear();
        chars.clear();
        words.clear();
        tmpWords.clear();
        lines.clear();
        rulings.clear();
        renderingMode.clear();
        strokingColor.clear();
        nonStrokingColor.clear();
        newLineStarted = false;
        lineStartPoint.setLocation(0f, 0f);
        lineEndPoint.setLocation(0f, 0f);
        lineText.setLength(0);
    }

    private void stripPage(int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
//        RulingExtractor rulingExtractor = new RulingExtractor(page);
//        List<Ruling> r = rulingExtractor.getRulings();
//        if (r != null) {
//            rulings.addAll(r);
//        }

        order = -1;
        pageIndex += 1;
        setStartPage(pageIndex);
        setEndPage(pageIndex);
        Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
        super.writeText(document, dummy);

    }

    private final Map<TextPosition, RenderingMode> renderingMode = new HashMap<>();
    private final Map<TextPosition, PDColor> strokingColor = new HashMap<>();
    private final Map<TextPosition, PDColor> nonStrokingColor = new HashMap<>();

    @Override
    protected void processTextPosition(TextPosition text) {
        renderingMode.put(text, getGraphicsState().getTextState().getRenderingMode());
        strokingColor.put(text, getGraphicsState().getStrokingColor());
        nonStrokingColor.put(text, getGraphicsState().getNonStrokingColor());
        super.processTextPosition(text);
    }

    public void setStartPage(int page) {
        super.setStartPage(page);
    }
    public void setEndPage(int page) {
        super.setEndPage(page);
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
        newLineStarted = false; // The new line was ended here
        addLine();
        lineText.setLength(0);
        super.endPage(page);
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        newLineStarted = false; // The new line was ended here
        addLine();
        lineText.setLength(0);
        super.writeLineSeparator();
    }

    private void addLine() {
        if (StringUtils.isNotBlank(lineText)) {
            if (currentPage.canPrint(lineStartPoint) && currentPage.canPrint(lineEndPoint)) {
                StringBuilder text = new StringBuilder();
                for(int i = 0; i < tmpWords.size() - 1; i++){
                    text.append(tmpWords.get(i).getText());
                    text.append(" ");
                }
                if (tmpWords.size() == 0) {
                    return;
                }
                text.append(tmpWords.get(tmpWords.size() - 1).getText());
                text.append("\n");
                //задебажить
                BoundingBox line_bb = BoundingBox.fromCorners(lineStartPoint.x, lineEndPoint.y, lineEndPoint.x, lineStartPoint.y);
                TextLine line = new TextLine(currentPage.getPageNumber(), line_bb, text.toString(), tmpWords,
                        line_bb.getHeight(), order, null, null, tmpWords.get(0).getSpaceWidth());
                mergeLines(line);
                tmpWords.clear();
                // Update the minimal left and maximal right coordinates for calculating page margins
                if (minLeft > lineStartPoint.x) minLeft = lineStartPoint.x;
                if (maxRight < lineEndPoint.x) maxRight = lineEndPoint.x;
            }
        }
    }

    private void mergeLines(TextLine line){
        /*for (TextChunk l: this.lines) {
            l.setTop(l.getTop()+1);
            l.setBottom(l.getBottom()+1);
            if (l.intersects(line)) {
                line.addWors(l.getWords());
                l.retract();
            }
            l.setTop(l.getTop()-1);
            l.setBottom(l.getBottom()-1);
        }*/
        this.lines.add(line);
    }
    private Color getColor(TextPosition textPosition) throws IOException {
        RenderingMode rm = renderingMode.get(textPosition);
        if (rm == RenderingMode.FILL || rm == RenderingMode.NEITHER) {
            PDColor pdColor = nonStrokingColor.get(textPosition);
            Color color;
            try {
                color = new Color(pdColor.toRGB());
            } catch (UnsupportedOperationException e) {
                color = new Color(0);
            }
            return color;
        }
        if (rm == RenderingMode.STROKE) {
            PDColor pdColor = strokingColor.get(textPosition);
            return new Color(pdColor.toRGB());
        }
        return Color.BLACK;
    }

    private PDFFont getFont(TextPosition textPosition) {
        PDFont pdFont = textPosition.getFont();
        float fontSize = textPosition.getFontSizeInPt();
        if (null == pdFont)
            return null;

        String name = pdFont.getName();
        if (null == name)
            return null;

        PDFFont result = new PDFFont();
        result.setFontSize(fontSize);
        result.setName(name);
        final boolean isBoldFontName = name.toLowerCase().contains("bold");
        final boolean isItalicFontName = name.toLowerCase().contains("italic");
        PDFontDescriptor desc = pdFont.getFontDescriptor();
        boolean isForceBold = false;
        if (null != desc) {
            float height = desc.getCapHeight();
            // TODO: Clarify the calculation of the font height. It seems as a not real font height.
            result.setHeight(height);

            boolean italic = desc.isItalic();

            if (italic) {
                result.setItalic(true);
            } else if (name.toLowerCase().contains("oblique")) {
                result.setItalic(true);
            } else if (isItalicFontName) {
                result.setItalic(true);
            } else {
                result.setItalic(false);
            }

            isForceBold = desc.isForceBold();
        }

        // Calculating that the font is bold
        if (isForceBold) {
            result.setBold(true);
        }
        else if (isBoldFontName) {
            result.setBold(true);
        }
        else {
            RenderingMode rm = renderingMode.get(textPosition);
            if (rm == RenderingMode.FILL_STROKE) {
                result.setBold(true);
            }
            else {
                result.setBold(false);
            }
        }

        return result;
    }

    private boolean containsWhitespace(String text) {
        for (char c : text.toCharArray())
            for (char wordSeparator : whitespaces)
                if (c == wordSeparator)
                    return true;

        return false;
    }

    private void extractWords(int order, List<TextPosition> textPositions) throws IOException {
        if (null == textPositions || textPositions.isEmpty()) return;

        if (textPositions.size() > 1)
            textPositions.sort(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj));

        final StringBuilder sb = new StringBuilder(textPositions.size());
        boolean newWordStarted = false;


        // Word coordinates
        BoundingBox wordBBox = BoundingBox.fromCorners(0f,0f ,0f,0f);

        int numberTpInWord = 0;
        int indexTp = 0;

        for (TextPosition tp: textPositions) {
            if (!isWordChar(tp)){continue;}

            if (newWordStarted) {
                if (isNear(wordBBox, tp)) {
                    joinTpAndWord(tp, wordBBox, sb);
                    numberTpInWord += 1;
                }
                else {
                    TextPosition centerTp = textPositions.get(indexTp-numberTpInWord/2);
                    addWordToWordList(wordBBox, sb, order, centerTp);
                    numberTpInWord = 0;
                    setWordFromTp(wordBBox, tp, sb);
                }
            }
            else {
                newWordStarted = true;
                setWordFromTp(wordBBox, tp, sb);
            }
            indexTp += 1;
        }

        if (newWordStarted) {
            TextPosition tp = textPositions.get(indexTp-1);
            addWordToWordList(wordBBox, sb, order, tp);
        }
    }

    private void addWordToWordList(BoundingBox wordBBoc,
                                   StringBuilder sb, int order, TextPosition tpWithStyle) throws IOException {
        String wordText = sb.toString();
        //задебажить (по логике учитывая другую ск должно быть так wordBBoc.getY() - wordBBoc.getHeight())
//        BoundingBox word_bb = new BoundingBox(wordBBoc.getX(), wordBBoc.getY() - wordBBoc.getHeight(), wordBBoc.getWidth(), wordBBoc.getHeight());
        BoundingBox word_bb = BoundingBox.fromTp(tpWithStyle);
        PDFont pd_font = tpWithStyle.getFont();
//        boolean is_bold = pd_font.getName().toLowerCase().contains("bold");
//        boolean is_italic = pd_font.getName().toLowerCase().contains("italic");
//        PDFFont pdf_font = new PDFFont(pd_font.getName(), tpWithStyle.getFontSize(), pd_font.getBoundingBox().getHeight(), is_bold, is_italic);

//        PDColor pdColor = getGraphicsState().getStrokingColor();
//        float[] components = pdColor.getComponents();

//        Color color = Color.black;
//        if (pdColor.getColorSpace() instanceof PDDeviceGray) {
//            float gray = components[0];
//            color = new Color(gray,gray,gray);
//        }

        PDFFont wordFont = getFont(tpWithStyle);
        Color color = null;
        try {
            color = getColor(tpWithStyle);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Word word = new Word(currentPage.getPageNumber(), word_bb, sb.toString(), 0.8f, pd_font.getName(), tpWithStyle.getFontSize(), order, wordFont, color, tpWithStyle.getWidthOfSpace());
//        word.setFont(wordFont);
//        word.updateTextLine();
//        word.setColor(color);
//        word.setSpaceWidth(tpWithStyle.getWidthOfSpace());
//        word.setStartOrder(order);
//        word.setEndOrder(order);
//        word.setId(order);
        words.add(word);
        tmpWords.add(word);
    }

    private void joinTpAndWord(TextPosition tp, BoundingBox wordBBox, StringBuilder sbWord){
        final BoundingBox tpBBox = BoundingBox.fromTp(tp);
        sbWord.append(getTextTp(tp));
        //почему
//        wordBBox.setRight(tpBBox.getRight());
        wordBBox.setWidth(tpBBox.getRight() + wordBBox.getWidth());

        //задебажить
        if (wordBBox.getTop() > tpBBox.getTop())
            wordBBox.setHeight(tpBBox.getY() + tpBBox.getHeight());


        if (wordBBox.getY() < tpBBox.getY())
            wordBBox.setY(tpBBox.getY());
    }

    private void setWordFromTp(BoundingBox wordBBox, TextPosition tp, StringBuilder sb){
        //задебажить
        final BoundingBox tpBBox = BoundingBox.fromTp(tp);
        sb.setLength(0);
        sb.append(getTextTp(tp));
//        wordBBox.setPDFRectangle(tpBBox);
        wordBBox.setRect(tpBBox.getX(), tp.getY(), tp.getWidth(), tp.getHeight());
    }

    private boolean isWordChar(TextPosition tp){
        String text = getTextTp(tp);
        if (text == null || text.isEmpty()) return false;
        if (containsWhitespace(text)) return false;

        // Check if the text position is not directed (rotated)
        //if (tp.getDir() != 0) {
        //    //System.err.println("WARNING: a directed text was ignored");
        //    return false;
        //}
        // Check if the font of the text position is not null
        PDFFont font = getFont(tp);
        if (null == font) {
            //System.err.println("WARNING: a text whose font is null was ignored");
            return false;
        }

        return true;
    }

    private String getTextTp(TextPosition tp){
        String text = tp.getUnicode();
        //ToDO: Fix it. Embedded fonts
        if (tp.getUnicode().equals("\uF0B7")){text = "•";}
        return text;
    }

    private  boolean isNear(BoundingBox wordBBox,  TextPosition tp){
        //задебажить
        final BoundingBox tpBBox = new BoundingBox(tp.getX(), tp.getY() - tp.getHeight(), tp.getWidth(), tp.getHeight());
        return Math.abs(wordBBox.getRight() - tpBBox.getX()) < tp.getWidthOfSpace() * 0.4;
    }

//    @Override
//    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
//        // Increment the order an original chunk in its PDF document
//        order ++;
//
//        // Check if the string contains printable characters
//        if (StringUtils.isBlank(string)) {
//            return;
//        }
//
//        //String s = string.replaceAll("\\P{Print}", "");
//        //if (s.isEmpty()) {
//        //    return;
//        //}
//
//        // Line processing
//        if (!newLineStarted) {
//            newLineStarted = true; // A new line was started here
//            TextPosition tp = textPositions.get(0);
//            final float left = tp.getXDirAdj();
//            final float top =  tp.getYDirAdj() - tp.getHeightDir();
//            lineStartPoint.setLocation(left, top);
//        }
//
//        lineText.append(string);
//
//        // Chunk coordinates
//        float minTop = Float.MAX_VALUE;
//        float maxBottom = Float.MIN_VALUE;
//
//        float minLeft = Float.MAX_VALUE;
//        float maxRight = Float.MIN_VALUE;
//
//        // Char processing
//        for (TextPosition tp: textPositions) {
//            final String text  = tp.getUnicode();
//
//            // Char coordinates
////            final float left   = tp.getXDirAdj();
////            final float top    = tp.getYDirAdj() - tp.getHeightDir();
////            final float right  = tp.getXDirAdj() + tp.getWidthDirAdj();
////            final float bottom = tp.getYDirAdj();
//
//            float left = tp.getXDirAdj();
//            float bottom = tp.getYDirAdj();
//            float top = bottom - tp.getHeightDir();
//            float right = left + tp.getWidthDirAdj();
//
////            TextEntity character = new TextChunk(left, top, right, bottom, text, currentPage);
//            BoundingBox char_bb = BoundingBox.fromCorners(left, bottom, right, top);
//
//            //какую сущность использовать, создать новую?
//            TextEntity character = new Word(currentPage.getPageNumber(), char_bb, text);
//            chars.add(character);
//
//            // Line coordinates
//            if (minLeft > left)     minLeft = left;
//            if (minTop > top)       minTop = top;
//            if (maxRight < right)   maxRight = right;
//            if (maxBottom < bottom) maxBottom = bottom;
//        }
//
//        //то же самое, по идее мы должны использовать textentity, но он абстрактный
//        PdfTextChunk chunk = new PdfTextChunk(minLeft, minTop, maxRight, maxBottom, string, currentPage);
//
//        chunk.addAllTextPositions(textPositions);
//
//        chunk.setStartOrder(order);
//        chunks.add(chunk);
//
//        TextPosition lastTextPos = textPositions.get(textPositions.size() - 1);
//        final float right = lastTextPos.getXDirAdj() + lastTextPos.getWidthDirAdj();
//        lineEndPoint.setLocation(right, maxBottom);
//
//        // Word processing
//        extractWords(order, textPositions);
//    }

}
