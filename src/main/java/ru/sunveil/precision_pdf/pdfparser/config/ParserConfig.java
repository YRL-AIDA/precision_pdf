package ru.sunveil.precision_pdf.pdfparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pdf.parser")
public class ParserConfig {
    private String parserType = "pdfbox";
    private boolean enableMemoryMapping = false;
    private int maxMemoryUsageMb = 100;
    private boolean ignoreCorrupted = false;
    private boolean enableValidation = true;
    private String tempDirectory = System.getProperty("java.io.tmpdir");
}