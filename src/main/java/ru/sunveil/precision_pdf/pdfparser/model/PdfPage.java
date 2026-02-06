package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.PdfEntity;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.util.ArrayList;
import java.util.Iterator;
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
    private List<TextEntity> blocks = new ArrayList<>();
    private List<Ruling> joinedRulings = new ArrayList<>();
    private List<Ruling> verticalRulings = new ArrayList<>();
    private List<Ruling> horizontalRulings = new ArrayList<>();
    private List<BoundingBox> cells;
    private List<BoundingBox> possibleTables;

    @JsonIgnore
    private List<Ruling> visibleRulings = new ArrayList<>();

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

    public void removeBlock(TextEntity block){
        blocks.remove(block);
    }
    public void addVisibleRuling(Ruling r){
        this.visibleRulings.add(r);
    }

    public void addJoinedRulings(ArrayList<Ruling> joinedHorizontalRulings) {
        joinedRulings.addAll(joinedHorizontalRulings);
    }

    public void addVerticalRulings(List<Ruling> rulings) {
        verticalRulings.addAll(rulings);
    }

    public void addHorizontalRulings(List<Ruling> rulings) {
        horizontalRulings.addAll(rulings);
    }

    public void addCells(List<BoundingBox> cells){
        cells.addAll(cells);
    }

    public void addCell(BoundingBox cell) {
        cells.add(cell);
    }

    public void addPossibleTableArea (BoundingBox tableArea) {
        possibleTables.add(tableArea);
    }
    public Iterator<TextEntity> getBlocks() {
        return blocks.iterator();
    }
    public void addTable(Table table) {
        if (tables.contains(table)) {
            tables.remove(table);
        }
        tables.add(table);
    }
    @JsonIgnore
    public Iterator<Ruling> getBorderedTableRulings() {
        return visibleRulings.iterator();
    }
}
