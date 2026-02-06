package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import ru.sunveil.precision_pdf.pdfparser.table.VisibleRulingExtractor;

import java.io.IOException;
import java.util.List;

@Data
public class PdfDocument {
    private String filename;
    private int totalPages;
    private PdfMetadata metadata;
    private List<PdfPage> pages;
    private List<PdfImage> images;

    private final PDDocument pdDocument;
    @JsonIgnore
    private VisibleRulingExtractor visibleRulingExtractor;

    public PDPage getPDPage(int index){
        return pdDocument.getPage(index) != null ? pdDocument.getPage(index) : null;
    }

    public void extractLines() throws IOException {
        for (PdfPage page: pages) {
            visibleRulingExtractor.process(page);
        }
    }
}
