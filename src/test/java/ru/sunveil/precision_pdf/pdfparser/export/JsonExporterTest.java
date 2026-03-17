package ru.sunveil.precision_pdf.pdfparser.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonExporter Tests")
class JsonExporterTest {
    private JsonExporter exporter;
    private PdfDocument testDocument;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        exporter = new JsonExporter();
        objectMapper = new ObjectMapper();
        testDocument = createTestDocument();
    }

    @Test
    @DisplayName("Should export document to valid JSON")
    void testExportToValidJson() throws Exception {
        String result = exporter.export(testDocument, ExportFormat.JSON);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        JsonNode rootNode = objectMapper.readTree(result);
        assertNotNull(rootNode);
    }

    @Test
    @DisplayName("Should include document metadata in JSON")
    void testExportIncludesMetadata() throws Exception {
        String result = exporter.export(testDocument, ExportFormat.JSON);
        JsonNode rootNode = objectMapper.readTree(result);

        assertTrue(rootNode.has("filename"));
        assertEquals("test.pdf", rootNode.get("filename").asText());
        assertTrue(rootNode.has("totalPages"));
        assertEquals(1, rootNode.get("totalPages").asInt());
    }

    @Test
    @DisplayName("Should include pages in JSON output")
    void testExportIncludesPages() throws Exception {
        String result = exporter.export(testDocument, ExportFormat.JSON);
        JsonNode rootNode = objectMapper.readTree(result);

        assertTrue(rootNode.has("pages"));
        JsonNode pagesNode = rootNode.get("pages");
        assertTrue(pagesNode.isArray());
        assertEquals(1, pagesNode.size());
    }

    @Test
    @DisplayName("Should include text lines in page")
    void testExportIncludesTextLines() throws Exception {
        String result = exporter.export(testDocument, ExportFormat.JSON);
        JsonNode rootNode = objectMapper.readTree(result);

        JsonNode pagesNode = rootNode.get("pages");
        JsonNode firstPageNode = pagesNode.get(0);
        assertTrue(firstPageNode.has("textLines"));
        
        JsonNode textLinesNode = firstPageNode.get("textLines");
        assertTrue(textLinesNode.isArray());
        assertTrue(textLinesNode.size() > 0);
    }

    @Test
    @DisplayName("Should support JSON export format")
    void testSupportsJsonFormat() {
        assertTrue(exporter.supportsFormat(ExportFormat.JSON));
    }

    @Test
    @DisplayName("Should not support XML export format")
    void testDoesNotSupportXmlFormat() {
        assertFalse(exporter.supportsFormat(ExportFormat.XML));
    }

    @Test
    @DisplayName("Should not support HTML export format")
    void testDoesNotSupportHtmlFormat() {
        assertFalse(exporter.supportsFormat(ExportFormat.HTML));
    }

    @Test
    @DisplayName("Should handle empty document")
    void testExportEmptyDocument() throws Exception {
        PdfDocument emptyDoc = new PdfDocument(null);
        emptyDoc.setFilename("empty.pdf");
        emptyDoc.setTotalPages(0);
        emptyDoc.setPages(new ArrayList<>());

        String result = exporter.export(emptyDoc, ExportFormat.JSON);
        assertNotNull(result);
        JsonNode rootNode = objectMapper.readTree(result);
        assertTrue(rootNode.has("pages"));
    }

    @Test
    @DisplayName("Should serialize text line properties correctly")
    void testSerializesTextLineProperties() throws Exception {
        String result = exporter.export(testDocument, ExportFormat.JSON);
        JsonNode rootNode = objectMapper.readTree(result);

        JsonNode firstPage = rootNode.get("pages").get(0);
        JsonNode firstLine = firstPage.get("textLines").get(0);

        assertTrue(firstLine.has("text"));
        assertTrue(firstLine.has("order"));
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
        line.setColor(Color.BLACK);

        lines.add(line);
        page.setTextLines(lines);

        List<PdfPage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);

        return doc;
    }
}
