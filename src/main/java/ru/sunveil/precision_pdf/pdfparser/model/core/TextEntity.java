package ru.sunveil.precision_pdf.pdfparser.model.core;

import lombok.Data;

@Data
public abstract class TextEntity implements PdfEntity {
    protected int pageNumber;
    protected BoundingBox boundingBox;
    protected String text;

    protected TextEntity() {
        this.pageNumber = 0;
        this.boundingBox = null;
        this.text = null;
    }

    protected TextEntity(int pageNumber, BoundingBox boundingBox, String text) {
        this.pageNumber = pageNumber;
        this.boundingBox = boundingBox;
        this.text = text;
    }

    public String getText() { return text; }

    public abstract String getType();
}