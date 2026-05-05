package ru.sunveil.precision_pdf.controller;

import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import ru.sunveil.precision_pdf.controller.dto.ApiResponse;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfMetadata;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.visualizer.PdfBoundingBoxRenderer;
import ru.sunveil.precision_pdf.service.PrecisionPdfExtractionService;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pdf")
public class PrecisionPdfController {

    private final PrecisionPdfExtractionService pdfExtractionService;

    public PrecisionPdfController(PrecisionPdfExtractionService pdfExtractionService) {
        this.pdfExtractionService = pdfExtractionService;
    }

    @GetMapping("/")
    public String index() {
        return "Precision PDF Extraction Service is running!";
    }

    @GetMapping("/config")
    public ExtractionConfig getConfig() {
        return pdfExtractionService.getDefaultConfig();
    }

    @PostMapping("/extract/text")
    public ResponseEntity<ApiResponse<String>> extractPdfSimple(
            @RequestParam("pdfFile") MultipartFile file,
            @RequestParam(value = "extractText", required = false) Boolean extractText,
            @RequestParam(value = "extractImages", required = false) Boolean extractImages,
            @RequestParam(value = "extractTables", required = false) Boolean extractTables,
            @RequestParam(value = "extractMetadata", required = false) Boolean extractMetadata,
            @RequestParam(value = "parser", required = false) String parser,
            @RequestParam(value = "outputFormat", defaultValue = "JSON") String outputFormat) {

        long startTime = System.currentTimeMillis();

        try {
            ExtractionConfig config = createCustomConfig(extractText, extractImages, extractTables, extractMetadata, parser);

            config.setOutputFormat(outputFormat);

            String result = pdfExtractionService.processPdf(file, config);

            long processingTime = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(ApiResponse.success(result,
                    "PDF extracted successfully", processingTime));

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Extraction failed: " + e.getMessage(), processingTime));
        }
    }

    @GetMapping("/visualize/{type}")
    public ResponseEntity<Resource> VisualizeBbox(@PathVariable("type") String type) throws IOException {
        try {

            if (pdfExtractionService.getLastUploadedFile() == null ||
                    pdfExtractionService.getLastDocument() == null) {
                return ResponseEntity.badRequest().body(null);
            }

            PdfBoundingBoxRenderer renderer = new PdfBoundingBoxRenderer();

            File inputFile = pdfExtractionService.getLastUploadedFile();
            File outputFile = File.createTempFile("processed_" + type, ".pdf");
            List<PdfPage> pdfPages = pdfExtractionService.getLastDocument().getPages();
            PdfBoundingBoxRenderer.BoxType boxType = PdfBoundingBoxRenderer.BoxType.valueOf(type.toUpperCase());

            renderer.renderBoundingBoxes(inputFile, outputFile, pdfPages, boxType);

            byte[] fileContent = Files.readAllBytes(outputFile.toPath());
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=visualized_" + type + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(outputFile.length())
                    .body(resource);

        } catch (IllegalArgumentException e ){
          return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/formats")
    public ResponseEntity<ApiResponse<List<String>>> getSupportedFormats() {
        try {
            List<String> formats = Arrays.stream(ExportFormat.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(formats,
                    "Supported formats retrieved", 0));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get formats: " + e.getMessage(), 0));
        }
    }

    @PostMapping("/extract/metadata")
    public ResponseEntity<ApiResponse<PdfMetadata>> extractMetadata(
            @RequestParam("file") MultipartFile file) {

        long startTime = System.currentTimeMillis();

        try {
            ExtractionConfig config = new ExtractionConfig();
            config.setExtractText(false);
            config.setExtractImages(false);
            config.setExtractTables(false);
            config.setExtractMetadata(true);

            PdfDocument document = pdfExtractionService.parsePdf(file, config);
            long processingTime = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(ApiResponse.success(document.getMetadata(),
                    "Metadata extracted successfully", processingTime));

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Metadata extraction failed: " + e.getMessage(), processingTime));
        }
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + e.getMessage(), 0));
    }

    private ExtractionConfig createCustomConfig(Boolean extractText, Boolean extractImages,
                                                Boolean extractTables, Boolean extractMetadata,
                                                String parser) {
        ExtractionConfig config = new ExtractionConfig();
        config.setExtractText(extractText != null ? extractText : true);
        config.setExtractImages(extractImages != null ? extractImages : false);
        config.setExtractTables(extractTables != null ? extractTables : false);
        config.setExtractMetadata(extractMetadata != null ? extractMetadata : true);
        if (parser != null && !parser.isBlank()) {
            config.setParser(parser);
        }
        config.setPreserveLayout(true);
        return config;
    }
}