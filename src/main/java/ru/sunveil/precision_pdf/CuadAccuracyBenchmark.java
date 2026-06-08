package ru.sunveil.precision_pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.benchmark.TextAccuracyMetrics;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.config.ParserConfig;
import ru.sunveil.precision_pdf.pdfparser.export.PlainTextExtractor;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.parser.PdfParseFactory;
import ru.sunveil.precision_pdf.pdfparser.parser.PdfParser;
import ru.sunveil.precision_pdf.pdfparser.parser.DeepSeekOcrParser;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class CuadAccuracyBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(CuadAccuracyBenchmark.class);

    private static final List<ParserSpec> ALL_PARSERS = List.of(
            new ParserSpec("precision-pdf", null, null),
            new ParserSpec("page-r-parser", null, null),
            new ParserSpec("odl-parser", "docling-fast", null),
            new ParserSpec("deepseek-ocr", null, "transformers")
    );

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) {
        try {
            BenchmarkOptions options = BenchmarkOptions.fromArgs(args);
            new CuadAccuracyBenchmark().run(options);
        } catch (Exception e) {
            logger.error("CUAD accuracy benchmark failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void run(BenchmarkOptions options) throws IOException {
        Path pdfRoot = options.cuadRoot().resolve("full_contract_pdf");
        Path txtRoot = options.cuadRoot().resolve("full_contract_txt");
        if (!Files.isDirectory(pdfRoot) || !Files.isDirectory(txtRoot)) {
            throw new IllegalArgumentException("CUAD dataset not found under " + options.cuadRoot());
        }

        Map<String, Path> txtIndex = buildTxtIndex(txtRoot);
        List<CuadDocument> documents = listDocuments(pdfRoot, txtIndex);
        if (documents.isEmpty()) {
            throw new IllegalStateException("No PDF/TXT pairs found in CUAD dataset");
        }

        logger.info("Found {} PDF/TXT pairs. stopAfterFirst={}", documents.size(), options.stopAfterFirst());

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("startedAt", Instant.now().toString());
        run.put("cuadRoot", options.cuadRoot().toString());
        run.put("stopAfterFirst", options.stopAfterFirst());

        List<Map<String, Object>> documentResults = new ArrayList<>();
        PdfParseFactory parseFactory = new PdfParseFactory(new ParserConfig());

        for (int i = 0; i < documents.size(); i++) {
            CuadDocument document = documents.get(i);
            logger.info("[{}/{}] {} | category={} | part={}",
                    i + 1, documents.size(), document.fileName(), document.category(), document.part());

            String groundTruth = Files.readString(document.txtPath());
            List<Map<String, Object>> parserResults = new ArrayList<>();

            for (ParserSpec parserSpec : options.parsers()) {
                parserResults.add(evaluateParser(parseFactory, document, groundTruth, parserSpec));
            }

            Map<String, Object> documentResult = new LinkedHashMap<>();
            documentResult.put("fileName", document.fileName());
            documentResult.put("category", document.category());
            documentResult.put("part", document.part());
            documentResult.put("pdfPath", document.pdfPath().toString());
            documentResult.put("groundTruthPath", document.txtPath().toString());
            documentResult.put("parsers", parserResults);
            documentResults.add(documentResult);

            if (options.stopAfterFirst()) {
                logger.info("Stopping after first document (pass --all to process the full dataset).");
                break;
            }
        }

        List<Map<String, Object>> categoryResults = buildCategorySummary(documentResults);

        run.put("byDocument", documentResults);
        run.put("byCategory", categoryResults);
        objectMapper.writeValue(options.outputPath().toFile(), run);
        logger.info("Results written to {}", options.outputPath());
        logger.info("  byDocument: {} PDF(s), byCategory: {} category/parser group(s)",
                documentResults.size(), categoryResults.size());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildCategorySummary(List<Map<String, Object>> documentResults) {
        Map<String, Map<String, CategoryAccumulator>> grouped = new LinkedHashMap<>();

        for (Map<String, Object> document : documentResults) {
            String category = String.valueOf(document.get("category"));
            List<Map<String, Object>> parsers = (List<Map<String, Object>>) document.get("parsers");
            if (parsers == null) {
                continue;
            }
            for (Map<String, Object> parserResult : parsers) {
                String parser = String.valueOf(parserResult.get("parser"));
                grouped
                        .computeIfAbsent(category, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(parser, ignored -> new CategoryAccumulator())
                        .add(parserResult);
            }
        }

        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map.Entry<String, Map<String, CategoryAccumulator>> categoryEntry : grouped.entrySet()) {
            for (Map.Entry<String, CategoryAccumulator> parserEntry : categoryEntry.getValue().entrySet()) {
                summary.add(parserEntry.getValue().toResult(categoryEntry.getKey(), parserEntry.getKey()));
            }
        }
        return summary;
    }

    private Map<String, Object> evaluateParser(
            PdfParseFactory parseFactory,
            CuadDocument document,
            String groundTruth,
            ParserSpec parserSpec
    ) {
        long started = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parser", parserSpec.name());
        if (parserSpec.odlMode() != null) {
            result.put("odlConversionMode", parserSpec.odlMode());
        }
        if (parserSpec.deepseekMode() != null) {
            result.put("deepseekOcrMode", parserSpec.deepseekMode());
        }
        result.put("groundTruthChars", groundTruth.length());

        try {
            ExtractionConfig config = createParserConfig(parserSpec);
            PdfParser parser = parseFactory.createParser(parserSpec.name());
            PdfDocument parsed = parser.parse(document.pdfPath().toFile(), config);
            String prediction = PlainTextExtractor.extract(parsed);
            TextAccuracyMetrics.Result metrics = TextAccuracyMetrics.compute(groundTruth, prediction);

            result.put("predictionChars", prediction.length());
            result.put("normalizedGroundTruthChars", metrics.normalizedGroundTruthChars());
            result.put("normalizedPredictionChars", metrics.normalizedPredictionChars());
            result.put("accuracy", round(metrics.accuracy()));
            result.put("editSimilarity", round(metrics.editSimilarity()));
            result.put("characterErrorRate", round(metrics.characterErrorRate()));
            result.put("elapsedSeconds", elapsedSeconds(started));
            result.put("error", null);

            logger.info("  [{}] accuracy={}% rawChars={}/{} normChars={}/{}",
                    parserSpec.name(),
                    Math.round(metrics.accuracy() * 10000.0) / 100.0,
                    prediction.length(),
                    groundTruth.length(),
                    metrics.normalizedPredictionChars(),
                    metrics.normalizedGroundTruthChars());
        } catch (Exception e) {
            result.put("predictionChars", 0);
            result.put("normalizedGroundTruthChars", TextAccuracyMetrics.normalize(groundTruth).length());
            result.put("normalizedPredictionChars", 0);
            result.put("accuracy", null);
            result.put("editSimilarity", null);
            result.put("characterErrorRate", null);
            result.put("elapsedSeconds", elapsedSeconds(started));
            result.put("error", e.getMessage());
            logger.warn("  [{}] failed: {}", parserSpec.name(), e.getMessage());
        }

        return result;
    }

    private static ExtractionConfig createParserConfig(ParserSpec parserSpec) {
        ExtractionConfig config = new ExtractionConfig();
        config.setParser(parserSpec.name());
        config.setExtractText(true);
        config.setExtractImages(false);
        config.setExtractTables(false);
        config.setExtractMetadata(false);
        config.setIncludeBoundingBoxes(false);
        config.setIncludeFontInfo(false);
        config.setIncludeConfidenceScores(false);
        if (parserSpec.odlMode() != null) {
            config.setOdlConversionMode(parserSpec.odlMode());
        }
        if (parserSpec.deepseekMode() != null) {
            config.setDeepseekOcrMode(parserSpec.deepseekMode());
        }
        return config;
    }

    private static Map<String, Path> buildTxtIndex(Path txtRoot) throws IOException {
        Map<String, Path> index = new HashMap<>();
        try (Stream<Path> paths = Files.walk(txtRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".txt"))
                    .forEach(path -> index.putIfAbsent(fileStem(path), path));
        }
        return index;
    }

    private static List<CuadDocument> listDocuments(Path pdfRoot, Map<String, Path> txtIndex) throws IOException {
        List<CuadDocument> documents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(pdfRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(pdfPath -> {
                        Path txtPath = txtIndex.get(fileStem(pdfPath));
                        if (txtPath == null) {
                            return;
                        }
                        String category = pdfPath.getParent() != null ? pdfPath.getParent().getFileName().toString() : "";
                        String part = pdfPath.getParent() != null && pdfPath.getParent().getParent() != null
                                ? pdfPath.getParent().getParent().getFileName().toString()
                                : "";
                        documents.add(new CuadDocument(
                                pdfPath.getFileName().toString(),
                                category,
                                part,
                                pdfPath,
                                txtPath
                        ));
                    });
        }
        return documents;
    }

    private static String fileStem(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return stem.toLowerCase(Locale.ROOT);
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private static double elapsedSeconds(long startedNanos) {
        return Math.round((System.nanoTime() - startedNanos) / 10_000_000.0) / 100.0;
    }

    private record ParserSpec(String name, String odlMode, String deepseekMode) {
    }

    private record CuadDocument(String fileName, String category, String part, Path pdfPath, Path txtPath) {
    }

    private static final class CategoryAccumulator {
        private int documentCount;
        private int successfulCount;
        private double accuracySum;
        private double editSimilaritySum;
        private double characterErrorRateSum;
        private int failedCount;

        private void add(Map<String, Object> parserResult) {
            documentCount++;
            if (parserResult.get("error") != null || parserResult.get("accuracy") == null) {
                failedCount++;
                return;
            }
            successfulCount++;
            accuracySum += ((Number) parserResult.get("accuracy")).doubleValue();
            editSimilaritySum += ((Number) parserResult.get("editSimilarity")).doubleValue();
            characterErrorRateSum += ((Number) parserResult.get("characterErrorRate")).doubleValue();
        }

        private Map<String, Object> toResult(String category, String parser) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("category", category);
            result.put("parser", parser);
            result.put("documentCount", documentCount);
            result.put("successfulCount", successfulCount);
            result.put("failedCount", failedCount);
            if (successfulCount > 0) {
                result.put("accuracy", round(accuracySum / successfulCount));
                result.put("editSimilarity", round(editSimilaritySum / successfulCount));
                result.put("characterErrorRate", round(characterErrorRateSum / successfulCount));
            } else {
                result.put("accuracy", null);
                result.put("editSimilarity", null);
                result.put("characterErrorRate", null);
            }
            return result;
        }
    }

    private record BenchmarkOptions(
            Path cuadRoot,
            Path outputPath,
            boolean stopAfterFirst,
            List<ParserSpec> parsers
    ) {

        static BenchmarkOptions fromArgs(String[] args) {
            Path cuadRoot = Path.of("..", "cuad", "CUAD_v1").toAbsolutePath().normalize();
            Path outputPath = Path.of("cuad_accuracy_results.json").toAbsolutePath().normalize();
            boolean stopAfterFirst = true;
            boolean skipOcr = false;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--help", "-h" -> {
                        printUsage();
                        System.exit(0);
                    }
                    case "--all" -> stopAfterFirst = false;
                    case "--skip-ocr" -> skipOcr = true;
                    case "--cuad-root" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing value for --cuad-root");
                        }
                        cuadRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                    }
                    case "--output" -> {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing value for --output");
                        }
                        outputPath = Path.of(args[++i]).toAbsolutePath().normalize();
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            List<ParserSpec> parsers = new ArrayList<>(ALL_PARSERS);
            if (skipOcr) {
                parsers.removeIf(spec -> "deepseek-ocr".equals(spec.name()));
            }
            return new BenchmarkOptions(cuadRoot, outputPath, stopAfterFirst, parsers);
        }

        private static void printUsage() {
            System.out.println("Usage: CuadAccuracyBenchmark [options]");
            System.out.println();
            System.out.println("Writes one JSON file with:");
            System.out.println("  byDocument  - accuracy per PDF (fileName, category, parsers[])");
            System.out.println("  byCategory  - average accuracy per folder/category and parser");
            System.out.println();
            System.out.println("Accuracy is normalized edit similarity (0-1, higher is better).");
            System.out.println("Raw char counts can differ from ground truth due to whitespace in CUAD txt files.");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --cuad-root <path>   CUAD_v1 directory (default: ../cuad/CUAD_v1)");
            System.out.println("  --output <path>      JSON output file (default: cuad_accuracy_results.json)");
            System.out.println("  --all                Process all matching documents");
            System.out.println("  --skip-ocr           Skip deepseek-ocr parser (faster runs)");
            System.out.println();
            System.out.println("By default only the first matching PDF/TXT pair is processed.");
            System.out.println();
            System.out.println("Run:");
            System.out.println("  mvn compile exec:java -Dexec.mainClass=ru.sunveil.precision_pdf.CuadAccuracyBenchmark");
        }
    }
}
