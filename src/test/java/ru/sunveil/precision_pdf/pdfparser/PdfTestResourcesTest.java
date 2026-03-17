package ru.sunveil.precision_pdf.pdfparser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.TableCell;
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
        assertEquals(firstRow.get(0).getContent(), "N, п/п");
        assertEquals(firstRow.get(1).getContent(), "Тех характеристика");
        assertEquals(firstRow.get(2).getContent(), "Показатель");

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
        File pdf = new File(res.toURI());

        ExtractionConfig config = new ExtractionConfig();
        config.setExtractMetadata(true);

        PdfDocument document = parser.parse(pdf, config);
        
        assertNotNull(document);
        assertNotNull(document.getMetadata());
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
        assertTrue(document.getPages().size() > 0);
        
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
}
