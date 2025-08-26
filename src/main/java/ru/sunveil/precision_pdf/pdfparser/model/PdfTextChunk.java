package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.util.List;

@Data
public class PdfTextChunk extends TextEntity {
    private List<TextLine> lines;
    private String style;

    public PdfTextChunk(){
        super();
        lines = null;
        style = null;
    }

    public PdfTextChunk(int pageNumber, BoundingBox boundingBox, String text,
                        List<TextLine> lines, String style) {
        super(pageNumber, boundingBox, text);
        this.lines = lines;
        this.style = style;
    }

    @Override
    public String getType() { return "TEXT_CHUNK"; }

    public List<TextLine> getLines() { return lines; }
    public String getStyle() { return style; }
}
