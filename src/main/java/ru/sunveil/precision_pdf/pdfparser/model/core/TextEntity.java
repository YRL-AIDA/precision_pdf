package ru.sunveil.precision_pdf.pdfparser.model.core;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;

@Data
public abstract class TextEntity implements PdfEntity {
    protected int pageNumber;
    protected BoundingBox boundingBox;
    protected String text;
    protected int startOrder;
    protected int endOrder;
    private PdfPage page;

    protected TextEntity() {
        this.pageNumber = 0;
        this.boundingBox = null;
        this.text = null;
    }

//    public void updateTextLine(){}

    protected TextEntity(int pageNumber, BoundingBox boundingBox, String text) {
        this.pageNumber = pageNumber;
        this.boundingBox = boundingBox;
        this.text = text;
    }

    public void retract(){
        assert (null != page);
        page.removeBlock(this);
    }

    public String getText() { return text; }

    public abstract String getType();
}