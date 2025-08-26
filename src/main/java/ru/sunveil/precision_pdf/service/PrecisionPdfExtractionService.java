package ru.sunveil.precision_pdf.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.export.Exporter;
import ru.sunveil.precision_pdf.pdfparser.export.ExporterFactory;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.pdfparser.parser.PdfParseFactory;
import ru.sunveil.precision_pdf.pdfparser.parser.PdfParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PrecisionPdfExtractionService {

    private final PdfParseFactory pdfParseFactory;
    private final ExtractionConfig extractionConfig;
    private final ExporterFactory exporterFactory;
    private static final Logger logger = LoggerFactory.getLogger(PrecisionPdfExtractionService.class);

    public PrecisionPdfExtractionService(PdfParseFactory pdfParseFactory,
                                         ExtractionConfig extractionConfig,
                                         ExporterFactory exporterFactory) {
        this.pdfParseFactory = pdfParseFactory;
        this.extractionConfig = extractionConfig;
        this.exporterFactory = exporterFactory;
    }

    public String processPdf(MultipartFile file, ExportFormat format) {
        return processPdf(file, format, extractionConfig);
    }

    public String processPdf(MultipartFile file, ExportFormat format, ExtractionConfig config) {
        try {

            Path tempFile = Files.createTempFile("pdf_", ".pdf");
            file.transferTo(tempFile);

            PdfParser parser = pdfParseFactory.createParser();
            PdfDocument pdfDocument = parseWithConfig(parser, tempFile.toFile(), config);

            Exporter Exporter = exporterFactory.getExporter(format);
            String result = Exporter.export(pdfDocument, format);

            Files.deleteIfExists(tempFile);

            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error processing PDF file", e);
        }
    }

    public PdfDocument parsePdf(MultipartFile file) {
        return parsePdf(file, extractionConfig);
    }

    public PdfDocument parsePdf(MultipartFile file, ExtractionConfig config) {
        try {
            Path tempFile = Files.createTempFile("pdf_", ".pdf");
            file.transferTo(tempFile);

            PdfParser parser = pdfParseFactory.createParser();
            PdfDocument result = parseWithConfig(parser, tempFile.toFile(), config);

            Files.deleteIfExists(tempFile);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error parsing PDF file", e);
        }
    }

    private PdfDocument parseWithConfig(PdfParser parser, File file, ExtractionConfig config) throws IOException {

        PdfDocument document = parser.parse(file, config);

        if (!config.isExtractImages()) {
            document.getPages().forEach(page -> page.setImages(null));
        }

        if (!config.isExtractTables()) {
            document.getPages().forEach(page -> page.setTables(null));
        }

        if (!config.isExtractMetadata()) {
            document.setMetadata(null);
        }

        return document;
    }

    public ExtractionConfig getDefaultConfig() {
        return extractionConfig;
    }

}
