package ru.sunveil.precision_pdf.pdfparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pdf.extraction")
public class ExtractionConfig {
    private String parser = "simple";
    private boolean extractText = true;
    private boolean extractImages = true;
    private boolean extractTables = true;
    private boolean extractMetadata = true;
    private boolean preserveLayout = true;
    private float imageDpi = 150;
    private int maxImageSize = 2048;
    private String outputFormat = "JSON";
    private boolean includeBoundingBoxes = true;
    private boolean includeFontInfo = true;
    private boolean includeConfidenceScores = false;
}
