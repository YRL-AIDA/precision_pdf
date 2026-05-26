package ru.sunveil.precision_pdf.pdfparser.parser;

public enum ParserType {
    PRECISION("precision-pdf"),
    PAGE_R_PARSER("page-r-parser"),
    ODL_PARSER("odl-parser"),
    DEEPSEEK_OCR("deepseek-ocr"),
    DEFAULT("default");

    private final String value;

    ParserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ParserType fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        String normalized = value.trim().toLowerCase();

        for (ParserType type : ParserType.values()) {
            if (type.value.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return DEFAULT;
    }
}
