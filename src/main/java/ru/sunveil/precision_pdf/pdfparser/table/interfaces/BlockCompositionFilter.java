package ru.sunveil.precision_pdf.pdfparser.table.interfaces;

import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

public interface BlockCompositionFilter {
    boolean canMerge(TextLine block, TextLine textChunk);
}