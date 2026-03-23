package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.PdfEntity;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class PdfPage implements PdfEntity {
    private int pageNumber;
    private double width;
    private double height;
    private int index;
    private static final float MIN_MARGIN = 5f;

    @JsonIgnore
    private PDDocument document;
    private List<Word> words = new ArrayList<>();
    private List<TextLine> textLines = new ArrayList<>();
    private List<PdfTextChunk> pdfTextChunks = new ArrayList<>();
    private List<Table> tables = new ArrayList<>();
    private List<PdfImage> images = new ArrayList<>();
    private List<TextEntity> blocks = new ArrayList<>();
    @JsonIgnore
    private List<Ruling> joinedRulings = new ArrayList<>();
    @JsonIgnore
    private List<Ruling> verticalRulings = new ArrayList<>();
    @JsonIgnore
    private List<Ruling> horizontalRulings = new ArrayList<>();
//    @JsonIgnore
    private List<Ruling> rulings = new ArrayList<>();
    private List<BoundingBox> cells = new ArrayList<>();
    private List<BoundingBox> possibleTables = new ArrayList<>();

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

    public void addVisibleRulings(List<Ruling> visibleRulings) {
        this.visibleRulings.clear();
        this.visibleRulings.addAll(visibleRulings);
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
        this.cells.addAll(cells);
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

    public boolean canPrint(Point2D.Float point) {
        double btm_x = getBoundingBox().getX()   + MIN_MARGIN;
        double top_y = getBoundingBox().getTop()    + MIN_MARGIN;
        double top_x = getBoundingBox().getRight()  - MIN_MARGIN;
        double btm_y = getBoundingBox().getY() - MIN_MARGIN;
        return btm_x < point.x && point.x < top_x && top_y < point.y && point.y < btm_y;
    }
}
