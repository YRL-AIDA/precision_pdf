package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PdfPage {
    private int pageNumber;
    private double width;
    private double height;
    private List<Word> words = new ArrayList<>();
    private List<TextLine> textLines = new ArrayList<>();
    private List<PdfTextChunk> pdfTextChunks = new ArrayList<>();
    private List<Table> tables = new ArrayList<>();
    private List<PdfImage> images = new ArrayList<>();
}
