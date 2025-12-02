package ru.sunveil.precision_pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.config.ParserConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.export.Exporter;
import ru.sunveil.precision_pdf.pdfparser.export.ExporterFactory;
import ru.sunveil.precision_pdf.pdfparser.export.JsonExporter;
import ru.sunveil.precision_pdf.pdfparser.export.TextExporter;
import ru.sunveil.precision_pdf.pdfparser.parser.PdfParseFactory;
import ru.sunveil.precision_pdf.service.PrecisionPdfExtractionService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PdfProcessorCLI {

    private static final Logger logger = LoggerFactory.getLogger(PdfProcessorCLI.class);

    public static void main(String[] args) {
        if (args.length < 1 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            System.exit(0);
        }

        try {
            PdfProcessorCLI processor = new PdfProcessorCLI();
            processor.processPdf(args);
        } catch (Exception e) {
            logger.error("Error processing PDF: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void processPdf(String[] args) throws IOException {
        String inputFilePath = args[0];
        String outputFilePath = args.length > 1 ? args[1] : null;
        ExtractionConfig config = createConfigFromArgs(args);

        // Проверяем входной файл
        Path inputPath = Paths.get(inputFilePath);
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFilePath);
        }

        if (!Files.isReadable(inputPath)) {
            throw new IllegalArgumentException("Cannot read input file: " + inputFilePath);
        }

        // Создаем компоненты
        ParserConfig parserConfig = createParserConfig();
        PdfParseFactory parseFactory = new PdfParseFactory(parserConfig);

        List<Exporter> exporters = new ArrayList<>();
        exporters.add(new JsonExporter());
        exporters.add(new TextExporter());
        ExporterFactory exporterFactory = new ExporterFactory(exporters);

        PrecisionPdfExtractionService service = new PrecisionPdfExtractionService(
            parseFactory, config, exporterFactory);

        // Создаем MultipartFile-like объект для файла
        File inputFile = inputPath.toFile();
        CustomMultipartFile multipartFile = new CustomMultipartFile(inputFile);

        logger.info("Processing PDF: {}", inputFilePath);
        logger.info("Configuration: extractText={}, extractImages={}, extractTables={}",
            config.isExtractText(), config.isExtractImages(), config.isExtractTables());

        // Обрабатываем PDF
        String result = service.processPdf(multipartFile, config);

        // Выводим результат
        if (outputFilePath != null) {
            Files.writeString(Paths.get(outputFilePath), result);
            logger.info("Result saved to: {}", outputFilePath);
        } else {
            System.out.println(result);
        }

        logger.info("PDF processing completed successfully");
    }

    private ParserConfig createParserConfig() {
        ParserConfig config = new ParserConfig();
        config.setParserType("pdfbox");
        config.setEnableMemoryMapping(false);
        config.setMaxMemoryUsageMb(256);
        config.setIgnoreCorrupted(false);
        config.setEnableValidation(true);
        return config;
    }

    private ExtractionConfig createConfigFromArgs(String[] args) {
        ExtractionConfig config = new ExtractionConfig();

        // По умолчанию - текст только
        config.setExtractText(true);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(true);
        config.setPreserveLayout(true);
        config.setOutputFormat("JSON");
        config.setIncludeBoundingBoxes(true);
        config.setIncludeFontInfo(false);
        config.setIncludeConfidenceScores(false);
        config.setImageDpi(150);
        config.setMaxImageSize(2048);

        // Парсим аргументы
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--extract-images":
                    config.setExtractImages(true);
                    break;
                case "--extract-tables":
                    config.setExtractTables(true);
                    break;
                case "--no-metadata":
                    config.setExtractMetadata(false);
                    break;
                case "--full-extraction":
                    config.setExtractText(true);
                    config.setExtractImages(true);
                    config.setExtractTables(true);
                    config.setExtractMetadata(true);
                    config.setIncludeBoundingBoxes(true);
                    config.setIncludeFontInfo(true);
                    config.setIncludeConfidenceScores(true);
                    break;
                case "--text-only":
                    config.setExtractText(true);
                    config.setExtractImages(false);
                    config.setExtractTables(false);
                    config.setExtractMetadata(false);
                    config.setIncludeBoundingBoxes(false);
                    config.setIncludeFontInfo(false);
                    config.setIncludeConfidenceScores(false);
                    break;
                case "--metadata-only":
                    config.setExtractText(false);
                    config.setExtractImages(false);
                    config.setExtractTables(false);
                    config.setExtractMetadata(true);
                    break;
                case "--format":
                    if (i + 1 < args.length) {
                        config.setOutputFormat(args[i + 1].toUpperCase());
                        i++; // Пропускаем следующий аргумент
                    }
                    break;
                case "--dpi":
                    if (i + 1 < args.length) {
                        try {
                            config.setImageDpi(Float.parseFloat(args[i + 1]));
                            i++;
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid DPI value: {}", args[i + 1]);
                        }
                    }
                    break;
                case "--max-image-size":
                    if (i + 1 < args.length) {
                        try {
                            config.setMaxImageSize(Integer.parseInt(args[i + 1]));
                            i++;
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid max image size: {}", args[i + 1]);
                        }
                    }
                    break;
            }
        }

        return config;
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar pdf-processor.jar <input_file> [output_file] [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  input_file    Path to input PDF file (required)");
        System.out.println("  output_file   Path to output file (optional, prints to stdout if not specified)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --extract-images          Extract images from PDF");
        System.out.println("  --extract-tables          Extract tables from PDF");
        System.out.println("  --no-metadata             Skip metadata extraction");
        System.out.println("  --full-extraction         Extract everything (text, images, tables, metadata)");
        System.out.println("  --text-only               Extract only text (default)");
        System.out.println("  --metadata-only           Extract only metadata");
        System.out.println("  --format <format>         Output format: JSON, TEXT (default: JSON)");
        System.out.println("  --dpi <value>             Image DPI for extraction (default: 150)");
        System.out.println("  --max-image-size <value>  Maximum image size in pixels (default: 2048)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar pdf-processor.jar document.pdf");
        System.out.println("  java -jar pdf-processor.jar document.pdf output.json --full-extraction");
        System.out.println("  java -jar pdf-processor.jar document.pdf output.txt --format TEXT --text-only");
    }

    /**
     * Простая реализация MultipartFile для работы с файлами
     */
    private static class CustomMultipartFile implements org.springframework.web.multipart.MultipartFile {
        private final File file;

        public CustomMultipartFile(File file) {
            this.file = file;
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public String getOriginalFilename() {
            return file.getName();
        }

        @Override
        public String getContentType() {
            return "application/pdf";
        }

        @Override
        public boolean isEmpty() {
            return file.length() == 0;
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(file.toPath());
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(file.toPath(), dest.toPath());
        }
    }
}
