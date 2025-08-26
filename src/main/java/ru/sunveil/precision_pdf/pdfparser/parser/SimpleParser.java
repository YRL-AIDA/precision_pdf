package ru.sunveil.precision_pdf.pdfparser.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.extensions.PdfParseException;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.AbstractPdfBoxParser;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.TextExtractionEngine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleParser extends AbstractPdfBoxParser {

    protected PDDocument currentDocument;
    protected ExtractionConfig extractionConfig;

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

    protected PdfDocument parseDocument(PDDocument document, String filename) {
        PdfDocument pdfDocument = new PdfDocument();
        pdfDocument.setFilename(filename);
        pdfDocument.setTotalPages(document.getNumberOfPages());

        if (extractionConfig.isExtractMetadata()) {
            pdfDocument.setMetadata(extractMetadata(document));
        }

        pdfDocument.setPages(extractPages(document));

        if (extractionConfig.isExtractImages()) {
            pdfDocument.setImages(extractImages(document));
        }

        return pdfDocument;
    }

    protected List<PdfPage> extractPages(PDDocument document) {
        List<PdfPage> pages = new ArrayList<>();
        int pageCount = document.getNumberOfPages();

        for (int i = 0; i < pageCount; i++) {
            try {
                PDPage pdPage = document.getPage(i);
                PdfPage page = extractPage(pdPage, i + 1);
                pages.add(page);
            } catch (Exception e) {
                System.err.println("Failed to extract page " + (i + 1) + ": " + e.getMessage());
            }
        }

        return pages;
    }

    protected PdfPage extractPage(PDPage page, int pageNumber) {
        PdfPage pdfPage = new PdfPage();
        pdfPage.setPageNumber(pageNumber);

        if (page.getMediaBox() != null) {
            pdfPage.setWidth(page.getMediaBox().getWidth());
            pdfPage.setHeight(page.getMediaBox().getHeight());
        }

        if (extractionConfig.isExtractText()) {
            try {
                pdfPage.setWords(extractWords(currentDocument));
                pdfPage.setTextLines(extractTextLines(currentDocument));
                pdfPage.setPdfTextChunks(extractTextChunks(currentDocument));
            } catch (Exception e) {
                System.err.println("Failed to extract text from page " + pageNumber + ": " + e.getMessage());
            }
        }

        if (extractionConfig.isExtractTables()) {
            try {
                pdfPage.setTables(extractTables(currentDocument));
            } catch (Exception e) {
                System.err.println("Failed to extract tables from page " + pageNumber + ": " + e.getMessage());
            }
        }

        if (extractionConfig.isExtractImages()) {
            try {
                pdfPage.setImages(extractImagesFromPage(page, pageNumber));
            } catch (Exception e) {
                System.err.println("Failed to extract images from page " + pageNumber + ": " + e.getMessage());
            }
        }

        return pdfPage;
    }

    protected List<PdfImage> extractImagesFromPage(PDPage page, int pageNumber) {
        List<PdfImage> pageImages = new ArrayList<>();
        if (currentDocument != null) {
            List<PdfImage> allImages = extractImages(currentDocument);
            for (PdfImage image : allImages) {
                if (image.getPageNumber() == pageNumber) {
                    pageImages.add(image);
                }
            }
        }
        return pageImages;
    }

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

    public List<PdfImage> extractImages(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        System.out.println("Image extraction not implemented in base class");
        return new ArrayList<>();
    }

    public List<Table> extractTables(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        System.out.println("Table extraction not implemented in base class");
        return new ArrayList<>();
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
