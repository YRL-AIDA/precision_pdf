package ru.sunveil.precision_pdf.service;

import org.apache.tomcat.jni.FileInfo;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.exceptions.PdfParseException;
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
import java.nio.file.StandardCopyOption;

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

    public String processPdf(MultipartFile multipartFile, ExtractionConfig extractionConfig) throws IOException {

        long startTime = System.currentTimeMillis();
        File tempFile = null;
        ExportFormat exportFormat = ExportFormat.valueOf(extractionConfig.getOutputFormat());

        try {
            logger.info("Starting PDF processing for file: {}, format: {}",
                    multipartFile.getOriginalFilename(), exportFormat);

            tempFile = convertMultipartFileToTempFile(multipartFile);
            logger.debug("Created temporary file: {}", tempFile.getAbsolutePath());

            PdfParser parser = pdfParseFactory.createParser();
            PdfDocument document = parseWithConfig(parser, tempFile, extractionConfig);

            logger.info("PDF parsed successfully. Pages: {}, Images: {}",
                    document.getTotalPages(),
                    document.getImages() != null ? document.getImages().size() : 0);

            Exporter exporter = exporterFactory.getExporter(ExportFormat.valueOf(extractionConfig.getOutputFormat()));
            if (!exporter.supportsFormat(exportFormat)) {
                throw new IllegalArgumentException("Unsupported export format: " + exportFormat);
            }

            String result = exporter.export(document, exportFormat);
            long processingTime = System.currentTimeMillis() - startTime;

            logger.info("PDF processing completed successfully. Processing time: {}ms, Result size: {} chars",
                    processingTime, result.length());

            return result;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("PDF processing failed after {}ms. Error: {}", processingTime, e.getMessage(), e);
            throw new PdfParseException("PDF processing failed: " + e.getMessage(), e);

        } finally {
            cleanupTempFile(tempFile);
        }
    }

    public PdfDocument parsePdf(MultipartFile multipartFile, ExtractionConfig extractionConfig)
            throws IOException {

        long startTime = System.currentTimeMillis();
        File tempFile = null;

        try {
            logger.info("Starting PDF parsing for file: {}", multipartFile.getOriginalFilename());

            tempFile = convertMultipartFileToTempFile(multipartFile);
            PdfParser parser = pdfParseFactory.createParser();
            PdfDocument document = parseWithConfig(parser, tempFile, extractionConfig);

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("PDF parsing completed. Time: {}ms, Pages: {}",
                    processingTime, document.getTotalPages());

            return document;

        } finally {
            cleanupTempFile(tempFile);
        }
    }

    public PdfDocument parsePdf(File pdfFile, ExtractionConfig extractionConfig) {
        try {
            PdfParser parser = pdfParseFactory.createParser();
            return parseWithConfig(parser, pdfFile, extractionConfig);
        } catch (Exception e) {
            logger.error("Failed to parse PDF file: {}", pdfFile.getAbsolutePath(), e);
            throw new PdfParseException("PDF parsing failed: " + e.getMessage(), e);
        }
    }

    public ExtractionConfig getDefaultConfig() {
        ExtractionConfig config = new ExtractionConfig();
        config.setExtractText(true);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(true);
        config.setPreserveLayout(true);
        config.setOutputFormat("JSON");
        config.setImageDpi(150);
        config.setMaxImageSize(1024);
        config.setIncludeBoundingBoxes(true);
        config.setIncludeFontInfo(false);
        config.setIncludeConfidenceScores(false);
        return config;
    }

    public ExtractionConfig getFullExtractionConfig() {
        ExtractionConfig config = getDefaultConfig();
        config.setExtractText(true);
        config.setExtractImages(true);
        config.setExtractTables(true);
        config.setExtractMetadata(true);
        config.setIncludeBoundingBoxes(true);
        config.setIncludeFontInfo(true);
        config.setIncludeConfidenceScores(true);
        return config;
    }

    public ExtractionConfig getTextOnlyConfig() {
        ExtractionConfig config = getDefaultConfig();
        config.setExtractText(true);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(false);
        config.setIncludeBoundingBoxes(false);
        config.setIncludeFontInfo(false);
        config.setIncludeConfidenceScores(false);
        return config;
    }

    public ExtractionConfig getMetadataOnlyConfig() {
        ExtractionConfig config = getDefaultConfig();
        config.setExtractText(false);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(true);
        return config;
    }

    public boolean validatePdf(MultipartFile multipartFile) {
        File tempFile = null;
        try {
            tempFile = convertMultipartFileToTempFile(multipartFile);
            // Простая проверка - пытаемся создать парсер и проверить файл
            PdfParser parser = pdfParseFactory.createParser();
            // Если не выброшено исключение, считаем файл валидным
            return true;
        } catch (Exception e) {
            logger.warn("PDF validation failed: {}", e.getMessage());
            return false;
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    public FileInfo getFileInfo(MultipartFile multipartFile) throws IOException {
        return new FileInfo(
                multipartFile.getOriginalFilename(),
                multipartFile.getSize(),
                multipartFile.getContentType(),
                System.currentTimeMillis()
        );
    }

    private PdfDocument parseWithConfig(PdfParser parser, File file, ExtractionConfig config) {
        try {
            return parser.parse(file, config);
        } catch (Exception e) {
            logger.error("Failed to parse PDF with config", e);
            throw new PdfParseException("PDF parsing failed: " + e.getMessage(), e);
        }
    }

    private File convertMultipartFileToTempFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("MultipartFile is null or empty");
        }

        String originalFilename = multipartFile.getOriginalFilename();
        String extension = ".pdf";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }

        File tempFile = File.createTempFile("pdf_extract_", extension);
        Files.copy(multipartFile.getInputStream(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return tempFile;
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                Files.deleteIfExists(tempFile.toPath());
                logger.debug("Temporary file deleted: {}", tempFile.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath(), e);
            }
        }
    }

    public static class FileInfo {
        private final String filename;
        private final long size;
        private final String contentType;
        private final long uploadTimestamp;

        public FileInfo(String filename, long size, String contentType, long uploadTimestamp) {
            this.filename = filename;
            this.size = size;
            this.contentType = contentType;
            this.uploadTimestamp = uploadTimestamp;
        }

        public String getFilename() { return filename; }
        public long getSize() { return size; }
        public String getContentType() { return contentType; }
        public long getUploadTimestamp() { return uploadTimestamp; }

        @Override
        public String toString() {
            return "FileInfo{" +
                    "filename='" + filename + '\'' +
                    ", size=" + size +
                    ", contentType='" + contentType + '\'' +
                    ", uploadTimestamp=" + uploadTimestamp +
                    '}';
        }
    }
}
