package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.util.List;

@Data
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
    private int cl; // Left column index
    private int rt; // Top row index
    private int cr; // Right column index
    private int rb; // Bottom row index

    @Override
    public String getType() {
        return "TABLE_CELL";
    }

    public TableCell(BoundingBox bbox, int invisible, List<TextEntity> contentBlocks, int cl, int rt, int cr, int rb) {
//        super(bbox.getLeft(), bbox.getTop(), bbox.getRight(), bbox.getBottom());
        setBoundingBox(bbox);
        this.contentBlocks = contentBlocks;
        this.order = Integer.MIN_VALUE;
        this.invisible = invisible;
        if (contentBlocks != null) {
            for (TextEntity chunk : contentBlocks) {
                this.order = Math.max(this.order, chunk.getEndOrder());
            }
        }
        assert (cl >= 0);
        this.cl = cl;
        assert (rt >= 0);
        this.rt = rt;
        assert (cr >= cl);
        this.cr = cr;
        assert (rb >= rt);
        this.rb = rb;
    }
}