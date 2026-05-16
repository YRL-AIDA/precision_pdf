package ru.sunveil.precision_pdf.pdfparser.parser;

import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.config.ParserConfig;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.AbstractPdfBoxParser;

@Component
public class PdfParseFactory {

    private final ParserConfig parserConfig;

    public PdfParseFactory(ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
    }

    public PdfParser createParser() {
        return createParser(parserConfig.getParserType());
    }

    public PdfParser createParser(String parserType) {
        return createParser(parserType, parserConfig);
    }

    public PdfParser createParser(String parserType, ParserConfig config) {
        ParserType type = ParserType.fromString(parserType);
        return switch (type) {
            case GNN_SEGMENTS -> new GnnSegmentsParser();
            case ODL_PARSER -> new OdlParser();
            case DEEPSEEK_OCR -> new DeepSeekOcrParser();
            case PRECISION, DEFAULT -> createPdfBoxParser(config);
        };
    }

    private PdfParser createPdfBoxParser(ParserConfig config) {
        SimpleParser parser = new SimpleParser();
        configureParser(parser, config);
        return parser;
    }

    private void configureParser(AbstractPdfBoxParser parser, ParserConfig config) {
    }
}