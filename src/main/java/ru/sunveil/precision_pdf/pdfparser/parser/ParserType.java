package ru.sunveil.precision_pdf.pdfparser.parser;

public enum ParserType {
    PRECISION("precision-pdf"),
    GNN_SEGMENTS("gnn-segments"),
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

        for (ParserType type : ParserType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return DEFAULT;
    }
}
