package ru.sunveil.precision_pdf.pdfparser.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HtmlExporter Tests")
class HtmlExporterTest {
    private HtmlExporter exporter;
    private PdfDocument testDocument;

    @BeforeEach
    void setUp() {
        exporter = new HtmlExporter();
        testDocument = createTestDocument();
    }

    @Test
    @DisplayName("Should export document to HTML string")
    void testExportToHtml() {
        String result = exporter.export(testDocument, ExportFormat.HTML);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Should include HTML DOCTYPE")
    void testHtmlIncludesDoctype() {
        String result = exporter.export(testDocument, ExportFormat.HTML);

        assertTrue(result.contains("<!DOCTYPE html>"));
        assertTrue(result.contains("</html>"));
    }

    @Test
    @DisplayName("Should include page elements")
    void testHtmlIncludesPageElements() {
        String result = exporter.export(testDocument, ExportFormat.HTML);

        assertTrue(result.contains("<body>"));
        assertTrue(result.contains("</body>"));
        assertTrue(result.contains("class=\"page\""));
    }

    @Test
    @DisplayName("Should include text content")
    void testHtmlIncludesTextContent() {
        String result = exporter.export(testDocument, ExportFormat.HTML);

        assertTrue(result.contains("Test Content"));
    }

    @Test
    @DisplayName("Should include CSS styles")
    void testHtmlIncludesStyles() {
        String result = exporter.export(testDocument, ExportFormat.HTML);

        assertTrue(result.contains("<style>"));
        assertTrue(result.contains("</style>"));
        assertTrue(result.contains("body{"));
        assertTrue(result.contains(".page{"));
    }

    @Test
    @DisplayName("Should support HTML export format")
    void testSupportsHtmlFormat() {
        assertTrue(exporter.supportsFormat(ExportFormat.HTML));
    }

    @Test
    @DisplayName("Should not support JSON export format")
    void testDoesNotSupportJsonFormat() {
        assertFalse(exporter.supportsFormat(ExportFormat.JSON));
    }

    @Test
    @DisplayName("Should handle multiple pages")
    void testExportMultiplePages() {
        PdfDocument multiPageDoc = new PdfDocument(null);
        multiPageDoc.setFilename("multi.pdf");
        multiPageDoc.setTotalPages(2);

        List<PdfPage> pages = new ArrayList<>();
        
        for (int i = 0; i < 2; i++) {
            PdfPage page = new PdfPage();
            page.setPageNumber(i + 1);
            page.setWidth(612);
            page.setHeight(792);
            page.setIndex(i);
            page.setTextLines(new ArrayList<>());
            page.setTables(new ArrayList<>());
            page.setImages(new ArrayList<>());
            pages.add(page);
        }

        multiPageDoc.setPages(pages);

        String result = exporter.export(multiPageDoc, ExportFormat.HTML);
        
        assertNotNull(result);
        // Should contain 2 page divs
        int pageCount = countOccurrences(result, "class=\"page\"");
        assertEquals(2, pageCount);
    }

    @Test
    @DisplayName("Should handle empty document")
    void testExportEmptyDocument() {
        PdfDocument emptyDoc = new PdfDocument(null);
        emptyDoc.setFilename("empty.pdf");
        emptyDoc.setTotalPages(0);
        emptyDoc.setPages(new ArrayList<>());

        String result = exporter.export(emptyDoc, ExportFormat.HTML);
        
        assertNotNull(result);
        assertTrue(result.contains("<!DOCTYPE html>"));
        assertTrue(result.contains("</html>"));
    }

    @Test
    @DisplayName("Should escape special HTML characters")
    void testEscapesSpecialCharacters() {
        PdfDocument doc = new PdfDocument(null);
        doc.setFilename("special.pdf");
        doc.setTotalPages(1);

        PdfPage page = new PdfPage();
        page.setPageNumber(1);
        page.setWidth(612);
        page.setHeight(792);
        page.setIndex(0);

        Word word = new Word();
        word.setText("<script>");
        word.setBoundingBox(new BoundingBox(10, 10, 50, 25));
        
        List<Word> words = new ArrayList<>();
        words.add(word);

        TextLine line = new TextLine();
        line.setText("<script>");
        line.setWords(words);
        line.setBoundingBox(new BoundingBox(10, 10, 100, 25));
        line.setOrder(0);
        line.setLineHeight(15);

        List<TextLine> lines = new ArrayList<>();
        lines.add(line);
        page.setTextLines(lines);
        page.setTables(new ArrayList<>());
        page.setImages(new ArrayList<>());

        List<PdfPage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);

        String result = exporter.export(doc, ExportFormat.HTML);
        
        assertNotNull(result);
        // Should still be valid HTML
        assertTrue(result.contains("<!DOCTYPE html>"));
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    private PdfDocument createTestDocument() {
        PdfDocument doc = new PdfDocument(null);
        doc.setFilename("test.pdf");
        doc.setTotalPages(1);

        PdfPage page = new PdfPage();
        page.setPageNumber(1);
        page.setWidth(612);
        page.setHeight(792);
        page.setIndex(0);

        List<TextLine> lines = new ArrayList<>();
        
        Word word = new Word();
        word.setText("Test");
        word.setBoundingBox(new BoundingBox(10, 10, 50, 25));
        
        List<Word> words = new ArrayList<>();
        words.add(word);

        TextLine line = new TextLine();
        line.setText("Test Content");
        line.setWords(words);
        line.setBoundingBox(new BoundingBox(10, 10, 100, 25));
        line.setOrder(0);
        line.setLineHeight(15);

        lines.add(line);
        page.setTextLines(lines);
        page.setTables(new ArrayList<>());
        page.setImages(new ArrayList<>());

        List<PdfPage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);

        return doc;
    }
}
