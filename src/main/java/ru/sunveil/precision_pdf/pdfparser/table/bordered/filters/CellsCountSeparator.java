package ru.sunveil.precision_pdf.pdfparser.table.bordered.filters;

import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.table.bordered.Factors;

public class CellsCountSeparator implements IBorderedTableSeparator {
    @Override
    public boolean isFullBorderedTable(Table table) {
        return table.getNumOfCells() > Factors.MIN_CELLS_COUNT_FACTOR;
    }
}
