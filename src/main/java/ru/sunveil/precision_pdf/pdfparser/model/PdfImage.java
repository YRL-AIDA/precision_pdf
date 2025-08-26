package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

@Data
public class PdfImage implements PdfEntity {
    private int pageNumber;
    private BoundingBox boundingBox;
    private byte[] imageData;
    private String imageFormat;
    private float resolution;
    private String id;
    private String colorSpace;
    private int width;
    private int height;

    @Override
    public String getType() {
        return "IMAGE";
    }
}