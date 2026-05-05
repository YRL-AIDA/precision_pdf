package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

@Data
public class PdfSegment {
    private int pageNumber;
    private BoundingBox boundingBox;
    private String label;
}
