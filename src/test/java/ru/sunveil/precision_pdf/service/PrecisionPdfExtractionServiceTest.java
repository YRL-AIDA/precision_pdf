package ru.sunveil.precision_pdf.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PrecisionPdfExtractionService Tests")
class PrecisionPdfExtractionServiceTest {
    
    private ExtractionConfig config;

    @BeforeEach
    void setUp() {
        config = new ExtractionConfig();
        config.setExtractText(true);
        config.setExtractTables(false);
        config.setOutputFormat("JSON");
    }

    @Test
    @DisplayName("Should create default extraction config")
    void testCreateDefaultExtractionConfig() {
        ExtractionConfig testConfig = new ExtractionConfig();
        assertNotNull(testConfig);
    }

    @Test
    @DisplayName("Should validate extraction config with text extraction")
    void testExtractionConfigWithTextExtraction() {
        config.setExtractText(true);
        assertTrue(config.isExtractText());
    }

    @Test
    @DisplayName("Should validate extraction config with table extraction")
    void testExtractionConfigWithTableExtraction() {
        config.setExtractTables(true);
        assertTrue(config.isExtractTables());
    }

    @Test
    @DisplayName("Should validate extraction config with image extraction")
    void testExtractionConfigWithImageExtraction() {
        config.setExtractImages(true);
        assertTrue(config.isExtractImages());
    }

    @Test
    @DisplayName("Should validate extraction config with metadata extraction")
    void testExtractionConfigWithMetadataExtraction() {
        config.setExtractMetadata(true);
        assertTrue(config.isExtractMetadata());
    }

    @Test
    @DisplayName("Should handle PDF export format JSON")
    void testJsonExportFormat() {
        config.setOutputFormat("JSON");
        ExportFormat format = ExportFormat.valueOf("JSON");
        assertEquals(ExportFormat.JSON, format);
    }

    @Test
    @DisplayName("Should handle PDF export format HTML")
    void testHtmlExportFormat() {
        config.setOutputFormat("HTML");
        ExportFormat format = ExportFormat.valueOf("HTML");
        assertEquals(ExportFormat.HTML, format);
    }

    @Test
    @DisplayName("Should handle PDF export format TEXT")
    void testTextExportFormat() {
        config.setOutputFormat("TEXT");
        ExportFormat format = ExportFormat.valueOf("TEXT");
        assertEquals(ExportFormat.TEXT, format);
    }

    @Test
    @DisplayName("Should handle PDF export format CSV")
    void testCsvExportFormat() {
        config.setOutputFormat("CSV");
        ExportFormat format = ExportFormat.valueOf("CSV");
        assertEquals(ExportFormat.CSV, format);
    }

    @Test
    @DisplayName("Should handle PDF export format XML")
    void testXmlExportFormat() {
        config.setOutputFormat("XML");
        ExportFormat format = ExportFormat.valueOf("XML");
        assertEquals(ExportFormat.XML, format);
    }

    @Test
    @DisplayName("Should reject invalid export format")
    void testInvalidExportFormat() {
        assertThrows(IllegalArgumentException.class, 
                () -> ExportFormat.valueOf("INVALID"));
    }

    @Test
    @DisplayName("Should set and get image DPI")
    void testImageDpiConfiguration() {
        config.setImageDpi(300);
        assertEquals(300, config.getImageDpi());
    }

    @Test
    @DisplayName("Should set and get max image size")
    void testMaxImageSizeConfiguration() {
        config.setMaxImageSize(2048);
        assertEquals(2048, config.getMaxImageSize());
    }

    @Test
    @DisplayName("Should set and get preserve layout flag")
    void testPreserveLayoutConfiguration() {
        config.setPreserveLayout(false);
        assertFalse(config.isPreserveLayout());
    }

    @Test
    @DisplayName("Should create valid PdfDocument")
    void testCreatePdfDocument() {
        PdfDocument doc = new PdfDocument(null);
        doc.setFilename("test.pdf");
        doc.setTotalPages(1);
        
        assertEquals("test.pdf", doc.getFilename());
        assertEquals(1, doc.getTotalPages());
    }

    @Test
    @DisplayName("Should add pages to PdfDocument")
    void testPdfDocumentWithPages() {
        PdfDocument doc = new PdfDocument(null);
        doc.setFilename("test.pdf");
        doc.setTotalPages(2);
        
        List<PdfPage> pages = new ArrayList<>();
        PdfPage page1 = new PdfPage();
        page1.setPageNumber(1);
        pages.add(page1);
        
        PdfPage page2 = new PdfPage();
        page2.setPageNumber(2);
        pages.add(page2);
        
        doc.setPages(pages);
        
        assertEquals(2, doc.getPages().size());
    }

    @Test
    @DisplayName("Should validate page properties")
    void testPdfPageProperties() {
        PdfPage page = new PdfPage();
        page.setPageNumber(1);
        page.setWidth(612);
        page.setHeight(792);
        page.setIndex(0);
        
        assertEquals(1, page.getPageNumber());
        assertEquals(612, page.getWidth());
        assertEquals(792, page.getHeight());
        assertEquals(0, page.getIndex());
    }


    @Test
    @DisplayName("Should support multi-page documents")
    void testMultiPageDocument() {
        PdfDocument doc = new PdfDocument(null);
        doc.setFilename("multipage.pdf");
        doc.setTotalPages(3);
        
        List<PdfPage> pages = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            PdfPage page = new PdfPage();
            page.setPageNumber(i);
            page.setWidth(612);
            page.setHeight(792);
            pages.add(page);
        }
        doc.setPages(pages);
        
        assertEquals(3, doc.getPages().size());
        assertEquals(3, doc.getTotalPages());
    }
}

