package ru.sunveil.precision_pdf.pdfparser.parser;

public enum ParserType {
    PRECISION("precision-pdf"),
    DEFAULT("default");

    private final String value;

    ParserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ParserType fromString(String value) {
        for (ParserType type : ParserType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return DEFAULT;
    }
}
