package ru.sunveil.precision_pdf.pdfparser.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ExtractionConfig.class, ParserConfig.class})
public class AppConfig {
}