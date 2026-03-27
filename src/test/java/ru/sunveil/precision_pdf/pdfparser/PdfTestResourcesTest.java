package ru.sunveil.precision_pdf.pdfparser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.parser.SimpleParser;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PDF Test Resources Integration Tests")
class PdfTestResourcesTest {
    private static SimpleParser parser;
    private static ExtractionConfig defaultConfig;

    @BeforeAll
    static void setUp() {
        parser = new SimpleParser();
        defaultConfig = new ExtractionConfig();
        defaultConfig.setExtractText(true);
        defaultConfig.setExtractImages(false);
        defaultConfig.setExtractTables(true);
        defaultConfig.setExtractMetadata(true);
        defaultConfig.setPreserveLayout(true);
        defaultConfig.setOutputFormat("JSON");
    }

    @Test
    @DisplayName("Should parse correct amount of tables from page")
    void testParseTablePdf() throws Exception {
        URL res = getClass().getClassLoader().getResource("table.pdf");
        assertNotNull(res, "table.pdf should exist in test resources");
        
        File pdf = new File(res.toURI());
        assertTrue(pdf.exists(), "table.pdf file should exist on disk");

        PdfDocument document = parser.parse(pdf, defaultConfig);
        
        assertNotNull(document);
        assertEquals(1, document.getTotalPages());
        assertNotNull(document.getPages());
        assertEquals(1, document.getPages().size());

        List<Table> tables = document.getPages().getFirst().getTables();
        assertEquals(2, tables.size());
    }

    @Test
    @DisplayName("Should parse table with merged cells")
    void testParseMultiPageTablePdf() throws Exception {
        URL res = getClass().getClassLoader().getResource("big_table_with_merged_cells.pdf");
        assertNotNull(res, "big_table_with_merged_cells.pdf should exist in test resources");
        
        File pdf = new File(res.toURI());
        assertTrue(pdf.exists(), "multipage_table.pdf file should exist on disk");

        PdfDocument document = parser.parse(pdf, defaultConfig);
        assertNotNull(document);
        assertEquals(1, document.getTotalPages());
        assertNotNull(document.getPages());
        assertEquals(document.getTotalPages(), document.getPages().size());

        List<List<TableCell>> rows = document.getPages().getFirst().getTables().getFirst().getRows();
        assertEquals(8, rows.get(0).size());
        assertEquals(1, rows.get(1).size());
        assertEquals(10, rows.get(2).size());
        assertEquals(7, rows.get(3).size());
        assertEquals(6, rows.get(4).size());
        assertEquals(2, rows.get(5).size());

        assertEquals("9", rows.get(1).getFirst().getContent());
    }

    @Test
    @DisplayName("Should parse multipage_table.pdf successfully")
    void testParseBigTableWithMergedCellsPdf() throws Exception {
        URL res = getClass().getClassLoader().getResource("multipage_table.pdf");
        assertNotNull(res, "big_table_with_merged_cells.pdf should exist in test resources");
        
        File pdf = new File(res.toURI());
        assertTrue(pdf.exists(), "big_table_with_merged_cells.pdf file should exist on disk");

        PdfDocument document = parser.parse(pdf, defaultConfig);

        assertNotNull(document);
        assertEquals(3, document.getTotalPages());
        assertNotNull(document.getPages());

        List<List<TableCell>> rows = document.getPages().getFirst().getTables().getFirst().getRows();
        List<TableCell> firstRow = rows.getFirst();
        assertEquals("N, п/п", firstRow.get(0).getContent());
        assertEquals("Тех характеристика", firstRow.get(1).getContent());
        assertEquals("Показатель", firstRow.get(2).getContent());

        int pageOneTableSize = rows.size() * firstRow.size();
        assertEquals(24, pageOneTableSize);

        List<List<TableCell>> rows2 = document.getPages().get(1).getTables().getFirst().getRows();
        int pageTwoTableSize = rows2.size() * rows2.getFirst().size();
        assertEquals(27, pageTwoTableSize);

    }

    @Test
    @DisplayName("Should extract metadata from PDF files")
    void testExtractMetadata() throws Exception {
        URL res = getClass().getClassLoader().getResource("table.pdf");
        assertNotNull(res);
        File pdf = new File(res.toURI());

        ExtractionConfig config = new ExtractionConfig();
        config.setExtractMetadata(true);

        PdfDocument document = parser.parse(pdf, config);
        
        assertNotNull(document);
        assertNotNull(document.getMetadata());
    }

    @Test
    @DisplayName("Should split lines in multiple lines if there are rulings between words")
    void testLinesSpliting() throws Exception {
        URL res = getClass().getClassLoader().getResource("table.pdf");
        assertNotNull(res);
        File pdf = new File(res.toURI());
        PdfDocument document = parser.parse(pdf, defaultConfig);
        PdfPage page = document.getPages().getFirst();
        List<TextLine> lines = page.getTextLines();
        TextLine firstCellLine = lines.get(3);
        TextLine secondCellLine = lines.get(4);
        TextLine thirdCellLine = lines.get(5);

        assertEquals(3, firstCellLine.getOrder());
        assertEquals(3, secondCellLine.getOrder());
        assertEquals(3, thirdCellLine.getOrder());

        assertEquals("№", firstCellLine.getText());
        assertEquals("Задача", secondCellLine.getText());
        assertEquals("Результат", thirdCellLine.getText());
    }

    @Test
    @DisplayName("Should handle multiple PDFs with consistent results")
    void testConsistentParsingAcrossMultiplePdfs() throws Exception {
        String[] pdfNames = {"table.pdf", "Document635.pdf"};
        
        for (String pdfName : pdfNames) {
            URL res = getClass().getClassLoader().getResource(pdfName);
            assertNotNull(res, pdfName + " should exist in test resources");
            
            File pdf = new File(res.toURI());
            assertTrue(pdf.exists(), pdfName + " should exist on disk");

            PdfDocument document = parser.parse(pdf, defaultConfig);
            
            assertNotNull(document, "Document should be parsed for " + pdfName);
            assertTrue(document.getTotalPages() > 0, "Should have at least 1 page");
            assertNotNull(document.getPages(), "Pages list should not be null");
            assertFalse(document.getPages().isEmpty(), "Pages list should not be empty");
        }
    }

    @Test
    @DisplayName("Should preserve page dimensions")
    void testPreservePageDimensions() throws Exception {
        URL res = getClass().getClassLoader().getResource("table.pdf");
        File pdf = new File(res.toURI());

        PdfDocument document = parser.parse(pdf, defaultConfig);
        
        assertNotNull(document);
        assertNotNull(document.getPages());
        assertFalse(document.getPages().isEmpty());
        
        for (int i = 0; i < document.getPages().size(); i++) {
            double width = document.getPages().get(i).getWidth();
            double height = document.getPages().get(i).getHeight();
            
            assertTrue(width > 0, "Page width should be positive for page " + i);
            assertTrue(height > 0, "Page height should be positive for page " + i);
        }
    }

    @Test
    @DisplayName("Should handle parsing with different configurations")
    void testParseWithDifferentConfigs() throws Exception {
        URL res = getClass().getClassLoader().getResource("table.pdf");
        assertNotNull(res);
        File pdf = new File(res.toURI());

        ExtractionConfig config1 = new ExtractionConfig();
        config1.setExtractText(true);
        config1.setExtractImages(false);
        config1.setExtractTables(false);

        PdfDocument doc1 = parser.parse(pdf, config1);
        assertNotNull(doc1);

        ExtractionConfig config2 = new ExtractionConfig();
        config2.setExtractText(true);
        config2.setExtractTables(true);
        config2.setExtractImages(false);

        PdfDocument doc2 = parser.parse(pdf, config2);
        assertNotNull(doc2);

        assertEquals(doc1.getTotalPages(), doc2.getTotalPages());
    }
    @Test
    @DisplayName("Should extract proper bbox's coordinates with different mediabox from cropbox")
    void testPdfWithCropboxOffset() throws Exception{
        URL res = getClass().getClassLoader().getResource("cropbox_offset.pdf");
        assertNotNull(res);
        File pdf = new File((res.toURI()));
        PdfDocument document = parser.parse(pdf, defaultConfig);
        PdfPage page = document.getPages().getFirst();
        List<Word> words = page.getWords();
        List<TextLine> lines = page.getTextLines();
        List<PdfTextChunk> chunks = page.getPdfTextChunks();

        BoundingBox firstWordBB = new BoundingBox(192.35f, 723.30f, 25.7f, 3.34f);
        BoundingBox lastWordBB = new BoundingBox(90.27f, 209.95f, 42f, 3.95f);

        BoundingBox firstLineBB = new BoundingBox(136.91f, 722.75f, 359.5f, 4.62f);
        BoundingBox lastLineBB = new BoundingBox(90.27f, 209.95f, 42f, 3.95f);

        BoundingBox firstChunkBB = new BoundingBox(176.87f, 723.18f, 41.18f, 3.45f);
        BoundingBox lastChunkBB = new BoundingBox(90.27f, 209.95f, 45.14f, 3.95f);

        assertBoundingBoxEquals(firstWordBB, words.getFirst().getBoundingBox(), 1f);
        assertBoundingBoxEquals(lastWordBB, words.getLast().getBoundingBox(), 1f);

        assertBoundingBoxEquals(firstLineBB, lines.getFirst().getBoundingBox(), 1f);
        assertBoundingBoxEquals(lastLineBB, lines.getLast().getBoundingBox(), 1f);

        assertBoundingBoxEquals(firstChunkBB, chunks.getFirst().getBoundingBox(), 1f);
        assertBoundingBoxEquals(lastChunkBB, chunks.getLast().getBoundingBox(), 1f);
    }
    @Test
    @DisplayName("Should extract proper bbox's coordinates in pdf which visible area is smaller than mediabox")
    void testPdfWithSmallerCropBox() throws Exception{
        URL res = getClass().getClassLoader().getResource("smaller_cropbox.pdf");
        assertNotNull(res);
        File pdf = new File((res.toURI()));
        PdfDocument document = parser.parse(pdf, defaultConfig);
        PdfPage page = document.getPages().getFirst();

        List<Word> words = page.getWords();
        List<TextLine> lines = page.getTextLines();
        List<PdfTextChunk> chunks = page.getPdfTextChunks();

        BoundingBox firstWordBB = new BoundingBox(236.61f, 786.77f, 22.35f, 4.38f);
        BoundingBox lastWordBB = new BoundingBox(503.66f, 131.89f, 32.11f, 4.45f);

        BoundingBox firstLineBB = new BoundingBox(236.60f, 786.77f, 350.64f, 4.44f);
        BoundingBox lastLineBB = new BoundingBox(84.63f, 131.89f, 451.13f, 4.44f);

        BoundingBox firstChunkBB = new BoundingBox(236.61f, 786.77f, 22.35f, 4.38f);
        BoundingBox lastChunkBB = new BoundingBox(503.66f, 131.89f, 32.11f, 4.45f);

        assertBoundingBoxEquals(firstWordBB, words.getFirst().getBoundingBox(), 1f);
        assertBoundingBoxEquals(lastWordBB, words.getLast().getBoundingBox(), 1f);

        assertBoundingBoxEquals(firstLineBB, lines.getFirst().getBoundingBox(), 1f);
        assertBoundingBoxEquals(lastLineBB, lines.getLast().getBoundingBox(), 1f);

        assertBoundingBoxEquals(firstChunkBB, chunks.getFirst().getBoundingBox(), 1f);
        assertBoundingBoxEquals(lastChunkBB, chunks.getLast().getBoundingBox(), 1f);
    }

    private void assertBoundingBoxEquals(BoundingBox expected, BoundingBox actual, float epsilon) {
        assertEquals(expected.getX(), actual.getX(), epsilon);
        assertEquals(expected.getY(), actual.getY(), epsilon);
        assertEquals(expected.getWidth(), actual.getWidth(), epsilon);
        assertEquals(expected.getHeight(), actual.getHeight(), epsilon);
    }
}
