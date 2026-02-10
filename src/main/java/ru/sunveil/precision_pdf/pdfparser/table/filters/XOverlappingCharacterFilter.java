package ru.sunveil.precision_pdf.pdfparser.table.filters;

import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;
import ru.sunveil.precision_pdf.pdfparser.table.interfaces.BlockCompositionFilter;

public class XOverlappingCharacterFilter implements BlockCompositionFilter {

    private static final float DEFAULT_EPSILON = 0.01f;
    private static final float epsilon;

    static {
        epsilon = DEFAULT_EPSILON;
    }

    @Override
    public boolean canMerge(TextLine block, TextLine textChunk) {
        if (Math.abs(block.getBoundingBox().getTop() - textChunk.getBoundingBox().getTop()) > epsilon)
            return false;

        if (Math.abs(block.getBoundingBox().getY() - textChunk.getBoundingBox().getY()) > epsilon)
            return false;

        double distance = textChunk.getBoundingBox().getX() - block.getBoundingBox().getRight();
        // The distance must be negative but more than the space width
        return distance < 0f && distance > -block.getSpaceWidth();
    }
}

