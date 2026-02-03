package ru.sunveil.precision_pdf.pdfparser.table.bordered.filters;

import ru.sunveil.precision_pdf.pdfparser.model.Table;

public class BorderedTableSeparator {

    private final IBorderedTableSeparator[] borderedTableSeparators;

    public BorderedTableSeparator() {
        borderedTableSeparators = new IBorderedTableSeparator[] {
                new CellsCountSeparator(),
                new CRCountCompositionSeparator(),
                new AverageChunksPerCellSeparator(),
                new CellsCountSeparator()
        };
    }

    public boolean isFullBorderedTable(Table table) {
        for (IBorderedTableSeparator bts : borderedTableSeparators) {
            if (!bts.isFullBorderedTable(table)) {
                return false;
            }
        }
        return true;
    }

}
