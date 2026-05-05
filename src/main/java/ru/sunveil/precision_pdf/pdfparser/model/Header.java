package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class Header extends TextEntity {
    private int order;
    private List<TextLine> lines = new ArrayList<>();

    public Header() {
        super();
        this.order = -1;
    }

    public Header(int pageNumber, BoundingBox boundingBox, String text, int order, List<TextLine> lines) {
        super(pageNumber, boundingBox, text);
        this.order = order;
        this.lines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }

    @Override
    public String getType() {
        return "HEADER";
    }
}
