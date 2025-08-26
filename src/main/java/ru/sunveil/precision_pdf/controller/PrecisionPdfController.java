package ru.sunveil.precision_pdf.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.export.ExportFormat;
import ru.sunveil.precision_pdf.service.PrecisionPdfExtractionService;

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

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> extractPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam(value = "extractText", required = false) Boolean extractText,
            @RequestParam(value = "extractImages", required = false) Boolean extractImages,
            @RequestParam(value = "extractTables", required = false) Boolean extractTables,
            @RequestParam(value = "extractMetadata", required = false) Boolean extractMetadata) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"File is empty\"}");
            }

            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body("{\"error\": \"File is not a PDF\"}");
            }

            ExportFormat exportFormat;
            try {
                exportFormat = ExportFormat.valueOf(format.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("{\"error\": \"Unsupported format: " + format + "\"}");
            }

            ExtractionConfig config = createCustomConfig(extractText, extractImages, extractTables, extractMetadata);

            String result = pdfExtractionService.processPdf(file, exportFormat, config);

            return createResponse(result, exportFormat, file.getOriginalFilename());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Error processing PDF: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping(value = "/extract/advanced", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> extractPdfAdvanced(
            @RequestParam("file") MultipartFile file,
            @RequestBody(required = false) ExtractionConfig config) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"File is empty\"}");
            }

            if (config == null) {
                config = pdfExtractionService.getDefaultConfig();
            }

            ExportFormat format = ExportFormat.valueOf(config.getOutputFormat().toUpperCase());
            String result = pdfExtractionService.processPdf(file, format, config);

            return createResponse(result, format, file.getOriginalFilename());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Error processing PDF: " + e.getMessage() + "\"}");
        }
    }

    private ExtractionConfig createCustomConfig(Boolean extractText, Boolean extractImages,
                                                Boolean extractTables, Boolean extractMetadata) {
        ExtractionConfig defaultConfig = pdfExtractionService.getDefaultConfig();
        ExtractionConfig config = new ExtractionConfig();

        config.setExtractText(extractText != null ? extractText : defaultConfig.isExtractText());
        config.setExtractImages(extractImages != null ? extractImages : defaultConfig.isExtractImages());
        config.setExtractTables(extractTables != null ? extractTables : defaultConfig.isExtractTables());
        config.setExtractMetadata(extractMetadata != null ? extractMetadata : defaultConfig.isExtractMetadata());
        config.setOutputFormat(defaultConfig.getOutputFormat());

        return config;
    }

    private ResponseEntity<String> createResponse(String content, ExportFormat format, String filename) {
        HttpHeaders headers = new HttpHeaders();
        String contentType;
        String fileExtension;

        switch (format) {
            case JSON:
                contentType = MediaType.APPLICATION_JSON_VALUE;
                fileExtension = "json";
                break;
            case HTML:
                contentType = MediaType.TEXT_HTML_VALUE;
                fileExtension = "html";
                break;
            case XML:
                contentType = MediaType.APPLICATION_XML_VALUE;
                fileExtension = "xml";
                break;
            case CSV:
                contentType = "text/csv";
                fileExtension = "csv";
                break;
            case TEXT:
                contentType = MediaType.TEXT_PLAIN_VALUE;
                fileExtension = "txt";
                break;
            default:
                contentType = MediaType.APPLICATION_JSON_VALUE;
                fileExtension = "json";
        }

        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment",
                filename.replace(".pdf", "") + "." + fileExtension);

        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }
}