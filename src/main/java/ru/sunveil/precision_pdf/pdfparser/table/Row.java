package ru.sunveil.precision_pdf.pdfparser.table;

import ru.sunveil.precision_pdf.pdfparser.model.TableCell;

import java.util.ArrayList;
import java.util.List;

public class Row {
    private final int id;
    private final List<TableCell> cells = new ArrayList<>();

    public Row(int id) {
        this.id = id;
    }

    public List<TableCell> getCells() {
        return cells;
    }

    public int getId() {
        return id;
    }

    public boolean existCell(int cl, int cr) {
        for (TableCell cell: cells){
            if (cell.getCl() == cl && cell.getCr() == cr)
                return true;
        }
        return false;
    }

    public void addCell(TableCell cell) {
        cells.add(cell);
        cells.sort((c1, c2) -> Integer.compare(c1.getCl(), c2.getCl()));
    }

}
