package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OtherBlock extends TextEntity {
    private int order;
    private String label;
    private List<TextLine> lines = new ArrayList<>();

    public OtherBlock() {
        super();
        this.order = -1;
        this.label = "other";
    }

    public OtherBlock(int pageNumber, BoundingBox boundingBox, String text, int order, String label, List<TextLine> lines) {
        super(pageNumber, boundingBox, text);
        this.order = order;
        this.label = label;
        this.lines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }

    @Override
    public String getType() {
        return "OTHER_BLOCK";
    }
}
