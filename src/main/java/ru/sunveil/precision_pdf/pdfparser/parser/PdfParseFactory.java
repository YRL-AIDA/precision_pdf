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
        return createParser(ParserType.fromString(parserConfig.getParserType()));
    }

    public PdfParser createParser(ParserType parserType) {
        return createParser(parserType, parserConfig);
    }

    public PdfParser createParser(ParserType parserType, ParserConfig config) {
        switch (parserType) {
            case PRECISION:
                return createPdfBoxParser(config);
            case DEFAULT:
            default:
                return createPdfBoxParser(config);
        }
    }

    private PdfParser createPdfBoxParser(ParserConfig config) {
        SimpleParser parser = new SimpleParser();
        configureParser(parser, config);
        return parser;
    }

    private void configureParser(AbstractPdfBoxParser parser, ParserConfig config) {
    }
}