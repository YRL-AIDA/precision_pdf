package ru.sunveil.precision_pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.config.ParserConfig;
import ru.sunveil.precision_pdf.pdfparser.export.*;
import ru.sunveil.precision_pdf.pdfparser.parser.PdfParseFactory;
import ru.sunveil.precision_pdf.service.PrecisionPdfExtractionService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI процессор для PageR с совместимым форматом JSON
 * Использование: java -jar precision_pdf_pager.jar -i <input_file> [-o <output_file>]
 */
public class PdfProcessorPagerCLI {

    private static final Logger logger = LoggerFactory.getLogger(PdfProcessorPagerCLI.class);

    public static void main(String[] args) {
        String inputFilePath = null;
        String outputFilePath = null;

        for (int i = 0; i < args.length; i++) {
            if ("-i".equals(args[i]) || "--input".equals(args[i])) {
                if (i + 1 < args.length) {
                    inputFilePath = args[++i];
                } else {
                    logger.error("Missing value for -i argument");
                    printUsage();
                    System.exit(1);
                }
            } else if ("-o".equals(args[i]) || "--output".equals(args[i])) {
                if (i + 1 < args.length) {
                    outputFilePath = args[++i];
                } else {
                    logger.error("Missing value for -o argument");
                    printUsage();
                    System.exit(1);
                }
            } else if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                printUsage();
                System.exit(0);
            }
        }

        if (inputFilePath == null) {
            logger.error("Требуется опция \"-i (--input)\"");
            printUsage();
            System.exit(1);
        }

        try {
            PdfProcessorPagerCLI processor = new PdfProcessorPagerCLI();
            processor.processPdf(inputFilePath, outputFilePath);
        } catch (Exception e) {
            logger.error("Error processing PDF: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void processPdf(String inputFilePath, String outputFilePath) throws IOException {
        Path inputPath = Paths.get(inputFilePath);
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFilePath);
        }

        if (!Files.isReadable(inputPath)) {
            throw new IllegalArgumentException("Cannot read input file: " + inputFilePath);
        }

        ExtractionConfig config = createConfig();
        ParserConfig parserConfig = createParserConfig();
        PdfParseFactory parseFactory = new PdfParseFactory(parserConfig);

        List<Exporter> exporters = new ArrayList<>();
        exporters.add(new PagerJsonExporter());
        exporters.add(new JsonExporter());
        exporters.add(new TextExporter());
        ExporterFactory exporterFactory = new ExporterFactory(exporters);

        PrecisionPdfExtractionService service = new PrecisionPdfExtractionService(
            parseFactory, config, exporterFactory);

        File inputFile = inputPath.toFile();
        CustomMultipartFile multipartFile = new CustomMultipartFile(inputFile);

        logger.info("Processing PDF: {}", inputFilePath);
        logger.info("Configuration: extractText={}, extractImages={}, extractTables={}",
            config.isExtractText(), config.isExtractImages(), config.isExtractTables());

        String result = service.processPdf(multipartFile, config);

        if (outputFilePath != null) {
            Files.writeString(Paths.get(outputFilePath), result);
            logger.info("Result saved to: {}", outputFilePath);
        } else {
            System.out.print(result);
        }

        logger.info("PDF processing completed successfully");
    }

    private ParserConfig createParserConfig() {
        ParserConfig config = new ParserConfig();
        config.setParserType("precision-pdf");
        config.setEnableMemoryMapping(false);
        config.setMaxMemoryUsageMb(256);
        config.setIgnoreCorrupted(false);
        config.setEnableValidation(true);
        return config;
    }

    private ExtractionConfig createConfig() {
        ExtractionConfig config = new ExtractionConfig();
        config.setExtractText(true);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(true);
        config.setPreserveLayout(true);
        config.setOutputFormat("JSON");
        config.setIncludeBoundingBoxes(true);
        config.setIncludeFontInfo(true);
        config.setIncludeConfidenceScores(false);
        config.setImageDpi(150);
        config.setMaxImageSize(2048);
        return config;
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar precision_pdf_pager.jar -i <input_file> [-o <output_file>]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  -i, --input <file>    Input PDF file (required)");
        System.out.println("  -o, --output <file>   Output file (optional, prints to stdout if not specified)");
        System.out.println("  -h, --help            Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar precision_pdf_pager.jar -i document.pdf");
        System.out.println("  java -jar precision_pdf_pager.jar -i document.pdf -o output.json");
    }

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
