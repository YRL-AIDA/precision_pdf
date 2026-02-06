package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Data;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;
import ru.sunveil.precision_pdf.pdfparser.table.Row;
import ru.sunveil.precision_pdf.pdfparser.table.TableType;
import ru.sunveil.precision_pdf.pdfparser.table.bordered.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        rows = new ArrayList<>(1000);
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
                rows.add(new ArrayList<>());
            }
        }
        rows.get(rowId).add(cell);

    }
    public int getNumOfCells() {
        return rows == null ? 0 : rows.stream()
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();
    }
}