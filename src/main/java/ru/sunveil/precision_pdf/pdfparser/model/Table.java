package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;
import ru.sunveil.precision_pdf.pdfparser.table.Row;
import ru.sunveil.precision_pdf.pdfparser.table.TableType;
import ru.sunveil.precision_pdf.pdfparser.table.bordered.Range;

import java.util.ArrayList;
import java.util.List;

@Data
public class Table implements PdfEntity {
    private int pageNumber;
    private BoundingBox boundingBox;
    private List<List<TableCell>> rows;
    private int rowCount;
    private int columnCount;
    private TableType type;
    private int order;
    private ArrayList<Range> vertical = new ArrayList<>();
    private ArrayList<Range> horizontal = new ArrayList<>();

    @Override
    public String getType() {
        return "TABLE";
    }

    public Table(double left, double top, double right, double bottom, TableType type) {
        BoundingBox b = new BoundingBox((float)left,(float)bottom,(float)right,(float)top);
        setBoundingBox(b);
        this.type = type;
        this.order = Integer.MIN_VALUE;
    }
    public void setHorizontal(ArrayList<Range> horizontal) {
        this.horizontal = horizontal;
    }
    public void setVertical(ArrayList<Range> vertical) {
        this.vertical = vertical;
    }

    public void addCell(TableCell cell, int rowId) {
        this.order = Math.max(this.order, cell.getOrder());
        if (rows.size() < rowId + 1) {
            for (int i = rows.size(); i < rowId + 1; i++) {
                rows.add(new Row(rowId));
            }
        }
        rows.get(rowId).addCell(cell);

        cells.add(cell); // I added this code to read cells in the draw debugging (A. Shigarov)
    }
    public int getNumOfCells(){
        return cells.size();
    }
}