package ru.sunveil.precision_pdf.pdfparser.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.exceptions.PdfParseException;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.AbstractPdfBoxParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OdlParser extends AbstractPdfBoxParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public PdfDocument parse(File pdfFile, ExtractionConfig config) throws IOException {
        validateFile(pdfFile);

        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);
            PdfDocument pdfDocument = new PdfDocument(document);
            pdfDocument.setFilename(pdfFile.getName());
            pdfDocument.setTotalPages(document.getNumberOfPages());

            if (config != null && config.isExtractMetadata()) {
                pdfDocument.setMetadata(extractMetadata(document));
            }

            List<PdfPage> pages = createBasePages(document);
            Path odlJsonPath = runOdlPythonAndGetJson(pdfFile);
            JsonNode root = OBJECT_MAPPER.readTree(odlJsonPath.toFile());
            JsonNode kids = root.path("kids");

            if (kids.isArray()) {
                for (JsonNode node : kids) {
                    collectSegments(node, pages);
                }
            }

            pdfDocument.setPages(pages);
            return pdfDocument;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfParseException("ODL parser process was interrupted", e);
        } catch (Exception e) {
            throw new PdfParseException("Failed to parse PDF with ODL parser: " + e.getMessage(), e);
        } finally {
            closeDocument(document);
        }
    }

    @Override
    public List<PdfTextChunk> extractTextChunks(PDDocument document) {
        return List.of();
    }

    @Override
    public List<TextLine> extractTextLines(PDDocument document) {
        return List.of();
    }

    @Override
    public List<Word> extractWords(PDDocument document) {
        return List.of();
    }

    @Override
    public TextExtractionResult extractTextAllTextEntities(PDDocument document) {
        return new TextExtractionResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    @Override
    public TableExtractionResult extractTables(PdfPage page) {
        return new TableExtractionResult(new ArrayList<>());
    }

    @Override
    public List<PdfImage> extractImages(PDDocument document) {
        return List.of();
    }

    @Override
    public List<PdfImage> extractImagesFromPage(PDDocument document, int pageNumber) {
        return List.of();
    }

    @Override
    public boolean supportsImageExtraction() {
        return false;
    }

    private List<PdfPage> createBasePages(PDDocument document) {
        List<PdfPage> pages = new ArrayList<>();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage pdPage = document.getPage(i);
            PDRectangle mediaBox = pdPage.getMediaBox();

            PdfPage page = new PdfPage();
            page.setPageNumber(i + 1);
            page.setIndex(i);
            page.setDocument(document);
            page.setWidth(mediaBox.getWidth());
            page.setHeight(mediaBox.getHeight());
            page.setBoundingBox(
                    new BoundingBox(
                            mediaBox.getLowerLeftX(),
                            mediaBox.getLowerLeftY(),
                            mediaBox.getWidth(),
                            mediaBox.getHeight()
                    )
            );
            pages.add(page);
        }
        return pages;
    }

    private Path runOdlPythonAndGetJson(File pdfFile) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("odl_parser_output_");
        Path scriptPath = Path.of("odl_parser", "main.py").toAbsolutePath();
        Path dotVenvUnixPython = Path.of("odl_parser", ".venv", "bin", "python").toAbsolutePath();
        Path dotVenvWindowsPython = Path.of("odl_parser", ".venv", "Scripts", "python.exe").toAbsolutePath();
        Path venvUnixPython = Path.of("odl_parser", "venv", "bin", "python").toAbsolutePath();
        Path venvWindowsPython = Path.of("odl_parser", "venv", "Scripts", "python.exe").toAbsolutePath();
        String pythonExecutable;
        if (Files.exists(dotVenvUnixPython)) {
            pythonExecutable = dotVenvUnixPython.toString();
        } else if (Files.exists(dotVenvWindowsPython)) {
            pythonExecutable = dotVenvWindowsPython.toString();
        } else if (Files.exists(venvUnixPython)) {
            pythonExecutable = venvUnixPython.toString();
        } else if (Files.exists(venvWindowsPython)) {
            pythonExecutable = venvWindowsPython.toString();
        } else {
            pythonExecutable = "python";
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable,
                scriptPath.toString(),
                "--pdf",
                pdfFile.getAbsolutePath(),
                "--output-dir",
                outputDir.toAbsolutePath().toString()
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String outputText = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ODL python parser failed with code " + exitCode + ". Output: " + outputText);
        }

        String fileName = pdfFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        Path jsonPath = outputDir.resolve(stem + ".json");
        if (!Files.exists(jsonPath)) {
            throw new IOException("ODL JSON output not found: " + jsonPath + ". Parser output: " + outputText);
        }
        return jsonPath;
    }

    private void collectSegments(JsonNode node, List<PdfPage> pages) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        JsonNode bboxNode = node.path("bounding box");
        int pageNumber = node.path("page number").asInt(0);
        if (bboxNode.isArray() && bboxNode.size() == 4 && pageNumber > 0 && pageNumber <= pages.size()) {
            float x1 = (float) bboxNode.get(0).asDouble(Float.NaN);
            float y1 = (float) bboxNode.get(1).asDouble(Float.NaN);
            float x2 = (float) bboxNode.get(2).asDouble(Float.NaN);
            float y2 = (float) bboxNode.get(3).asDouble(Float.NaN);
            float width = x2 - x1;
            float height = y2 - y1;

            if (!Float.isNaN(x1) && !Float.isNaN(y1) && width > 0 && height > 0) {
                PdfSegment segment = new PdfSegment();
                segment.setPageNumber(pageNumber);
                segment.setBoundingBox(new BoundingBox(x1, y1, width, height));
                segment.setLabel(node.path("type").asText("segment"));
                pages.get(pageNumber - 1).getSegments().add(segment);
            }
        }

        JsonNode kids = node.path("kids");
        if (kids.isArray()) {
            for (JsonNode kid : kids) {
                collectSegments(kid, pages);
            }
        }

        JsonNode listItems = node.path("list items");
        if (listItems.isArray()) {
            for (JsonNode item : listItems) {
                collectSegments(item, pages);
            }
        }
    }
}
