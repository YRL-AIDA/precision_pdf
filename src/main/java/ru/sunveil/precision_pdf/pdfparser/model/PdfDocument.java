package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;

import java.util.List;

@Data
public class PdfDocument {
    private String filename;
    private int totalPages;
    private PdfMetadata metadata;
    private List<PdfPage> pages;
    private List<PdfImage> images;
}
