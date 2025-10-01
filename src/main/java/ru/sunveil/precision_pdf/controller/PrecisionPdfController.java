package ru.sunveil.precision_pdf.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sunveil.precision_pdf.controller.dto.ApiResponse;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfMetadata;
import ru.sunveil.precision_pdf.service.PrecisionPdfExtractionService;

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
            @RequestParam(value = "outputFormat", defaultValue = "JSON") String outputFormat) {

        long startTime = System.currentTimeMillis();

        try {
            ExtractionConfig config = createCustomConfig(true, false,
                    false, extractMetadata);

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
                                                Boolean extractTables, Boolean extractMetadata) {
        ExtractionConfig config = new ExtractionConfig();
        config.setExtractText(extractText != null ? extractText : true);
        config.setExtractImages(extractImages != null ? extractImages : false);
        config.setExtractTables(extractTables != null ? extractTables : false);
        config.setExtractMetadata(extractMetadata != null ? extractMetadata : true);
        config.setPreserveLayout(true);
        return config;
    }
}