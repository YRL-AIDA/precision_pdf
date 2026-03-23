package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.exceptions.PdfParseException;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.AbstractPdfBoxParser;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.ImageExtractionEngine;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.TextExtractionEngine;
import ru.sunveil.precision_pdf.pdfparser.table.BorderedTableExtractor;
import ru.sunveil.precision_pdf.pdfparser.table.VisibleRulingExtractor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleParser extends AbstractPdfBoxParser {

    protected PDDocument currentDocument;
    protected ExtractionConfig extractionConfig;
    private final ImageExtractionEngine imageExtractionEngine;
    private static final Logger logger = LoggerFactory.getLogger(SimpleParser.class);
    private VisibleRulingExtractor visibleRulingExtractor;
    private TextExtractionEngine textExtractionEngine;

    public SimpleParser() {
        this.imageExtractionEngine = new ImageExtractionEngine();
    }

    public SimpleParser(float imageDpi, int maxImageSize) {
        this.imageExtractionEngine = new ImageExtractionEngine(imageDpi, maxImageSize, true);
    }

    @Override
    public PdfDocument parse(File pdfFile, ExtractionConfig config) {
        validateFile(pdfFile);
        this.extractionConfig = config != null ? config : getDefaultExtractionConfig();

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);
            this.currentDocument = document;
            return parseDocument(document, pdfFile.getName());
        } catch (IOException e) {
            throw new PdfParseException("Failed to load PDF document: " + pdfFile.getAbsolutePath(), e);
        } finally {
            closeDocument(document);
            this.currentDocument = null;
        }
    }

    @Override
    public TextExtractionResult extractTextAllTextEntities(PDDocument document) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        return textExtractionEngine.extractText(document);
    }

    protected PdfDocument parseDocument(PDDocument document, String filename) throws IOException {
        PdfDocument pdfDocument = new PdfDocument(document);
        pdfDocument.setFilename(filename);
        pdfDocument.setTotalPages(document.getNumberOfPages());

        visibleRulingExtractor = new VisibleRulingExtractor(document);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        document.save(baos);
//        byte[] pdfBytes = baos.toByteArray();
//        PDDocument docCopy = Loader.loadPDF(pdfBytes);
        visibleRulingExtractor.extractVisibleRulings(document);

        textExtractionEngine = new TextExtractionEngine();
        textExtractionEngine.setPageRulings(visibleRulingExtractor.getVisibleRulings());

        pdfDocument.setVisibleRulingExtractor(visibleRulingExtractor);

        if (extractionConfig.isExtractMetadata()) {
            pdfDocument.setMetadata(extractMetadata(document));
        }
        TextExtractionResult textResult = null;
        if (extractionConfig.isExtractText()) {
            textResult = extractTextAllTextEntities(document);
        }
        List<PdfPage> pages = extractPages(document, textResult);

        pdfDocument.setPages(pages);

        pdfDocument.extractLines();
        if (extractionConfig.isExtractTables()) {
            extractTablesFromDocument(pdfDocument);
        }
        return pdfDocument;
    }

    protected void extractTablesFromDocument(PdfDocument pdfDocument){
        for (PdfPage page: pdfDocument.getPages()) {
            try {
                page.setTables(extractTables(page).getTables());
            } catch (Exception e) {
                logger.error("Failed to extract tables from page {}: {}", page.getPageNumber(), e.getMessage());
            }
        }
//        return pages;
    }

    protected List<PdfPage> extractPages(PDDocument document, TextExtractionResult globalTextResult) {
        List<PdfPage> pages = new ArrayList<>();
        int pageCount = document.getNumberOfPages();

        for (int i = 0; i < pageCount; i++) {
            try {
                PDPage pdPage = document.getPage(i);
                PdfPage page = extractPage(pdPage, i + 1, globalTextResult);
                page.setDocument(document);
                page.setIndex(i);
                pages.add(page);
            } catch (Exception e) {
                logger.error("Failed to extract page {}: {}", (i + 1), e.getMessage());
            }
        }

        return pages;
    }

    protected PdfPage extractPage(PDPage page, int pageNumber, TextExtractionResult globalTextResult) {
        PdfPage pdfPage = new PdfPage();
        pdfPage.setPageNumber(pageNumber);
        PDRectangle pdfbbox = page.getMediaBox();
        BoundingBox q = new BoundingBox(pdfbbox.getLowerLeftX(), pdfbbox.getLowerLeftY(), pdfbbox.getWidth(), pdfbbox.getHeight());
        pdfPage.setBoundingBox(q);

        if (page.getMediaBox() != null) {
            pdfPage.setWidth(page.getMediaBox().getWidth());
            pdfPage.setHeight(page.getMediaBox().getHeight());
        }

        if (extractionConfig.isExtractText() && globalTextResult != null) {
            try {
                List<Word> words = globalTextResult.getWords().stream()
                        .filter(w -> w.getPageNumber() == pageNumber)
                        .toList();
                List<TextLine> lines = globalTextResult.getTextLines().stream()
                        .filter(l -> l.getPageNumber() == pageNumber)
                        .toList();
                List<PdfTextChunk> chunks = globalTextResult.getTextChunks().stream()
                        .filter(c -> c.getPageNumber() == pageNumber)
                        .toList();

                pdfPage.setWords(words);
                pdfPage.setTextLines(lines);
                pdfPage.setPdfTextChunks(chunks);

            } catch (Exception e) {
                logger.error("Failed to assign text for page {}: {}", pageNumber, e.getMessage());
            }
        }

//        if (extractionConfig.isExtractTables()) {
//            try {
//                pdfPage.setTables(extractTables(pdfPage).getTables());
//            } catch (Exception e) {
//                logger.error("Failed to extract tables from page {}: {}", pageNumber, e.getMessage());
//            }
//        }

        if (extractionConfig.isExtractImages()) {
            try {
                List<PdfImage> pageImages = imageExtractionEngine.extractImagesFromPage(
                        getCurrentDocument(), pageNumber);
                pdfPage.setImages(pageImages);
            } catch (IOException e) {
                logger.warn("Failed to extract images from page {}", pageNumber, e);
            }
        }

        return pdfPage;
    }

    @Override
    public List<PdfTextChunk> extractTextChunks(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        try {
            TextExtractionEngine extractionEngine = new TextExtractionEngine();
            return extractionEngine.extractTextChunks(document);
        } catch (IOException e) {
            throw new PdfParseException("Failed to extract text chunks", e);
        }
    }

    @Override
    public List<TextLine> extractTextLines(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        try {
            TextExtractionEngine extractionEngine = new TextExtractionEngine();
            return extractionEngine.extractTextLines(document);
        } catch (IOException e) {
            throw new PdfParseException("Failed to extract text lines", e);
        }
    }

    @Override
    public List<Word> extractWords(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        try {
            TextExtractionEngine extractionEngine = new TextExtractionEngine();
            return extractionEngine.extractWords(document);
        } catch (IOException e) {
            throw new PdfParseException("Failed to extract words", e);
        }
    }

    @Override
    public List<PdfImage> extractImages(PDDocument document) {
        try {
            return imageExtractionEngine.extractImages(document);
        } catch (IOException e) {
            logger.error("Failed to extract images from PDF", e);
            throw new PdfParseException("Image extraction failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PdfImage> extractImagesFromPage(PDDocument document, int pageNumber) throws IOException {
        return imageExtractionEngine.extractImagesFromPage(document, pageNumber);
    }

    @Override
    public boolean supportsImageExtraction() {
        return true;
    }

    @Override
    public TableExtractionResult extractTables(PdfPage page) throws IOException {
        if (page == null) {
            throw new IllegalArgumentException("Page cannot be null");
        }
        BorderedTableExtractor bte = new BorderedTableExtractor();
        return bte.extractTables(page);
    }

    protected ExtractionConfig getDefaultExtractionConfig() {
        ExtractionConfig config = new ExtractionConfig();
        config.setExtractText(true);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(true);
        config.setPreserveLayout(true);
        config.setImageDpi(150);
        config.setMaxImageSize(1024);
        return config;
    }

    protected void setCurrentDocument(PDDocument document) {
        this.currentDocument = document;
    }

    protected PDDocument getCurrentDocument() {
        return currentDocument;
    }

    protected ExtractionConfig getExtractionConfig() {
        return extractionConfig;
    }
}