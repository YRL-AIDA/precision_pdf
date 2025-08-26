package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.util.List;

@Data
public class Table implements PdfEntity {
    private int pageNumber;
    private BoundingBox boundingBox;
    private List<List<TableCell>> rows;
    private int rowCount;
    private int columnCount;

    @Override
    public String getType() {
        return "TABLE";
    }
}