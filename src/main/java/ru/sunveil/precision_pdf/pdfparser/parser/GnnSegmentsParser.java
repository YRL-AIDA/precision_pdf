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

public class GnnSegmentsParser extends AbstractPdfBoxParser {

    private static final String PAGER_OUTPUTS_DIR = "pageroutputs";
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            Path pagerOutputPath = executeGnnAndSaveJson(pdfFile);
            pdfDocument.setPages(extractPages(document, pagerOutputPath));
            return pdfDocument;
        } catch (Exception e) {
            throw new PdfParseException("Failed to parse PDF with GNN segment JSON", e);
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

    private Path executeGnnAndSaveJson(File pdfFile) throws IOException, InterruptedException {
        Path outputsDir = Path.of(PAGER_OUTPUTS_DIR).toAbsolutePath();
        if (!Files.exists(outputsDir)) {
            Files.createDirectories(outputsDir);
        }
        if (!Files.isDirectory(outputsDir)) {
            throw new IOException("Path is not a directory: " + outputsDir);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                "curl.exe",
                "--silent",
                "--show-error",
                "-X", "POST",
                "http://127.0.0.1:8000/",
                "-F", "file=@" + pdfFile.getAbsolutePath(),
                "-F", "process={\"glam_rows\": true}"
        );
        Process process = processBuilder.start();

        String responseBody;
        String errorBody;
        try (var inputStream = process.getInputStream();
             var errorStream = process.getErrorStream()) {
            responseBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || responseBody.isBlank()) {
            throw new IOException("curl.exe failed for GNN segmentation. Exit code: " + exitCode +
                    (errorBody.isBlank() ? "" : ", stderr: " + errorBody));
        }

        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            String responsePrefix = responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody;
            throw new IOException("GNN service returned non-JSON response: " + responsePrefix, e);
        }
        Path responsePath = outputsDir.resolve("response_" + System.currentTimeMillis() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(responsePath.toFile(), jsonNode);
        return responsePath;
    }

    private List<PdfPage> extractPages(PDDocument document, Path pagerOutputPath) throws IOException {
        List<PdfPage> pages = new ArrayList<>();
        JsonNode root = objectMapper.readTree(pagerOutputPath.toFile());
        JsonNode pagesNode = root.path("pages");

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage pdPage = document.getPage(i);
            PdfPage page = createBasePage(pdPage, i + 1, i, document);
            if (pagesNode.isArray() && i < pagesNode.size()) {
                JsonNode jsonPage = pagesNode.get(i);
                fillSegments(page, jsonPage.path("regions"));
            }
            pages.add(page);
        }

        return pages;
    }

    private PdfPage createBasePage(PDPage pdPage, int pageNumber, int index, PDDocument document) {
        PdfPage page = new PdfPage();
        page.setPageNumber(pageNumber);
        page.setIndex(index);
        page.setDocument(document);

        PDRectangle mediaBox = pdPage.getMediaBox();
        page.setWidth(mediaBox.getWidth());
        page.setHeight(mediaBox.getHeight());
        page.setBoundingBox(new BoundingBox(mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY(),
                mediaBox.getWidth(), mediaBox.getHeight()));
        return page;
    }

    private void fillSegments(PdfPage page, JsonNode regionsNode) {
        if (!regionsNode.isArray()) {
            return;
        }

        for (JsonNode region : regionsNode) {
            JsonNode segment = region.path("segment");
            BoundingBox bbox = toBoundingBox(segment, page.getHeight());
            if (bbox != null && bbox.isValid()) {
                PdfSegment pdfSegment = new PdfSegment();
                pdfSegment.setPageNumber(page.getPageNumber());
                pdfSegment.setBoundingBox(bbox);
                String label = region.path("label").asText("");
                pdfSegment.setLabel(label.isBlank() ? "unknown" : label);
                page.getSegments().add(pdfSegment);
            }
        }
    }

    private BoundingBox toBoundingBox(JsonNode segmentNode, double pageHeight) {
        if (segmentNode.isMissingNode() || !segmentNode.isObject()) {
            return null;
        }

        float xTopLeft = (float) segmentNode.path("x_top_left").asDouble(Float.NaN);
        float yTopLeft = (float) segmentNode.path("y_top_left").asDouble(Float.NaN);
        float xBottomRight = (float) segmentNode.path("x_bottom_right").asDouble(Float.NaN);
        float yBottomRight = (float) segmentNode.path("y_bottom_right").asDouble(Float.NaN);

        if (Float.isNaN(xTopLeft) || Float.isNaN(yTopLeft) || Float.isNaN(xBottomRight) || Float.isNaN(yBottomRight)) {
            return null;
        }

        float width = xBottomRight - xTopLeft;
        float height = yBottomRight - yTopLeft;
        if (width <= 0 || height <= 0) {
            return null;
        }

        float pdfY = (float) pageHeight - yBottomRight;
        return new BoundingBox(xTopLeft, pdfY, width, height);
    }
}
