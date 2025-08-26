package ru.sunveil.precision_pdf.pdfparser.model.core;

public interface PdfEntity {
    int getPageNumber();
    BoundingBox getBoundingBox();
    String getType();

    void setBoundingBox(BoundingBox bbox);

    void setPageNumber(int currentPageNumber);
}