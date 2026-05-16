package ru.sunveil.precision_pdf.pdfparser.parser;

public enum ParserType {
    PRECISION("precision-pdf"),
    GNN_SEGMENTS("gnn-segments"),
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
        if ("simple".equals(normalized) || "pdfbox".equals(normalized) || "precision".equals(normalized)) {
            return PRECISION;
        }
        if ("gnn".equals(normalized) || "segments".equals(normalized)) {
            return GNN_SEGMENTS;
        }
        if ("odl".equals(normalized) || "dataloaderpdf".equals(normalized) || "opendataloader".equals(normalized)) {
            return ODL_PARSER;
        }
        if ("deepseek".equals(normalized)
                || "deepseekocr".equals(normalized)
                || "deepseek-ocr".equals(normalized)
                || "dsk".equals(normalized)
                || "dsk-ocr".equals(normalized)) {
            return DEEPSEEK_OCR;
        }

        for (ParserType type : ParserType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return DEFAULT;
    }
}
