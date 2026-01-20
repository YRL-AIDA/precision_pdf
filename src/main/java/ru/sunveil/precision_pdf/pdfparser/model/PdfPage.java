package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.PdfEntity;

import java.util.ArrayList;
import java.util.List;

@Data
public class PdfPage implements PdfEntity {
    private int pageNumber;
    private double width;
    private double height;

    private int index;

    @JsonIgnore
    private PDDocument document;

    private List<Word> words = new ArrayList<>();
    private List<TextLine> textLines = new ArrayList<>();
    private List<PdfTextChunk> pdfTextChunks = new ArrayList<>();
    private List<Table> tables = new ArrayList<>();
    private List<PdfImage> images = new ArrayList<>();

    @JsonIgnore
    private List<Ruling> visibleRulings = new ArrayList<>();

    @JsonIgnore
    private BoundingBox pageBbox;

    @Override
    public BoundingBox getBoundingBox() {
        return this.pageBbox;
    }

    @Override
    public String getType() {
        return "PdfPage";
    }

    @Override
    public void setBoundingBox(BoundingBox bbox) {
        this.pageBbox = bbox;
    }
    @JsonIgnore
    public PDPage getPDPage() {
        return document.getPage(index);
    }
    public boolean addVisibleRulings(List<Ruling> visibleRulings) {
        this.visibleRulings.clear();
        boolean result = visibleRulings == null ? false : this.visibleRulings.addAll(visibleRulings);
        return result;
    }
    public void addVisibleRuling(Ruling r){
        this.visibleRulings.add(r);
    }
}
