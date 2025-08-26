package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

@Data
public class TableCell implements PdfEntity {
    private int pageNumber;
    private BoundingBox boundingBox;
    private String content;
    private int row;
    private int column;
    private int rowSpan;
    private int colSpan;

    @Override
    public String getType() {
        return "TABLE_CELL";
    }
}