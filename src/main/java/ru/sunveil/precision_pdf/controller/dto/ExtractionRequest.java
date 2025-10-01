package ru.sunveil.precision_pdf.controller.dto;

import lombok.Data;

@Data
public class ExtractionRequest {
    private Boolean extractText;
    private Boolean extractImages;
    private Boolean extractTables;
    private Boolean extractMetadata;
    private Boolean preserveLayout;
    private String outputFormat;

    public ExtractionRequest(Boolean extractText, Boolean extractImages, Boolean extractTables,
                             Boolean extractMetadata, Boolean preserveLayout, String outputFormat) {
        this.extractText = extractText;
        this.extractImages = extractImages;
        this.extractTables = extractTables;
        this.extractMetadata = extractMetadata;
        this.preserveLayout = preserveLayout;
        this.outputFormat = outputFormat;
    }
}