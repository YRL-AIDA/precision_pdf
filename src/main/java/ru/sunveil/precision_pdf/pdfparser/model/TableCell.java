package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@JsonIgnoreType
public class TableCell implements PdfEntity {
    public static final float MIN_CELL_WIDTH = 10;
    public static final float MIN_CELL_HEIGHT = 5;
    private int pageNumber;
    private BoundingBox boundingBox;
    private String content;
    private int row;
    private int column;
    private int rowSpan;
    private int colSpan;
    private List<TextEntity> contentBlocks;
    private int order;
    private int invisible = 0;

    @Override
    public String getType() {
        return "TABLE_CELL";
    }

    public TableCell(BoundingBox bbox, int rowSpan, int colSpan, List<TextEntity> contentBlocks) {
        setBoundingBox(bbox);
        this.rowSpan = rowSpan;
        this.colSpan = colSpan;
        this.contentBlocks = contentBlocks;
        if (contentBlocks != null) {
            this.content = contentBlocks.stream().map(TextEntity::getText).collect(Collectors.joining(" ")).trim();
        } else {
            this.content = "";
        }
    }
}