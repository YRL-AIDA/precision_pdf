package ru.sunveil.precision_pdf.pdfparser.table;

import org.apache.commons.lang3.StringUtils;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractTableExtractor {
    private PdfPage page;

    protected AbstractTableExtractor(PdfPage page) {
        this.page = page;
    }

    protected PdfPage getPage() {
        return page;
    }

    protected abstract List<Table> extract();

    protected final TextEntity mergeCellBlocks(List<TextEntity> blocks) {
        if (null == blocks || blocks.isEmpty())
            return null;

        if (blocks.size() == 1)
            return blocks.get(0);

        // New algorithm
        List<TextEntity> inLineBlocks = new ArrayList();
        List<TextEntity> linedBlocks = new ArrayList();

        blocks.sort(Comparator.comparing(entity -> entity.getBoundingBox().getTop()));

        TextEntity block0 = blocks.get(0);
        double lineBottom = block0.getBoundingBox().getY();
        inLineBlocks.add(block0);

        for (int i = 1; i < blocks.size(); i++) {
            TextEntity block = blocks.get(i);
            double tp = block.getBoundingBox().getTop();
            double bm = block.getBoundingBox().getY();
            if (tp <= lineBottom && lineBottom <= bm) {
                lineBottom = bm;
                inLineBlocks.add(block);
            } else {
                TextEntity linedBlock = mergeInLineBlocks(inLineBlocks);
                linedBlocks.add(linedBlock);
                inLineBlocks.clear();

                inLineBlocks.add(block);
                lineBottom = block.getBoundingBox().getY();
            }
        }
        TextEntity linedBlock = mergeInLineBlocks(inLineBlocks);
        linedBlocks.add(linedBlock);

        TextEntity result = mergeLinedBlocks(linedBlocks);
        return result;

    }

    private TextEntity mergeInLineBlocks(List<TextEntity> inLineBlocks) {
        if (inLineBlocks.isEmpty())
            return null;

        if (inLineBlocks.size() == 1)
            return inLineBlocks.get(0);

//        inLineBlocks.sort(Comparator.comparing(PDFRectangle::getLeft));
        inLineBlocks.sort(Comparator.comparing(entity -> entity.getBoundingBox().getX()));
        TextEntity result = inLineBlocks.get(0);

//        double left = result.getLeft();
//        double bottom = result.getBottom();
//        double right = result.getRight();
//        double top = result.getTop();

        double left = result.getBoundingBox().getX();
        double bottom = result.getBoundingBox().getY();
        double right = result.getBoundingBox().getRight();
        double top = result.getBoundingBox().getTop();

        String text = result.getText();
        int startOrder = result.getEndOrder();
        int endOrder = result.getEndOrder();

        String separator = " ";

        for (int i = 1; i < inLineBlocks.size(); i++) {
            TextEntity block = inLineBlocks.get(i);
            String blockText = block.getText();

            if (StringUtils.isNotBlank(blockText))
                text = text.concat(separator).concat(blockText);

//            left = Math.min(left, block.getLeft());
//            top = Math.min(top, block.getTop());
//            right = Math.max(right, block.getRight());
//            bottom = Math.max(bottom, block.getBottom());

            left = Math.min(left, block.getBoundingBox().getX());
            top = Math.min(top, block.getBoundingBox().getTop());
            right = Math.max(right, block.getBoundingBox().getRight());
            bottom = Math.max(bottom, block.getBoundingBox().getY());

            startOrder = Math.min(startOrder, block.getStartOrder());
            endOrder = Math.max(endOrder, block.getEndOrder());

            // Remove merged blocks except the first one from the page
            block.retract();
        }

//        result.setLeft(left);
//        result.setTop(top);
//        result.setRight(right);
//        result.setBottom(bottom);

        result.getBoundingBox().setX((float) left);
        result.getBoundingBox().setHeight((float) top);
        result.getBoundingBox().setWidth((float) right);
        result.getBoundingBox().setY((float) bottom);

        result.setText(text);
        result.setStartOrder(startOrder);
        result.setEndOrder(endOrder);

        result.updateTextLine();
        return result;
    }

    private TextEntity mergeLinedBlocks(List<TextEntity> linedBlocks) {
        if (linedBlocks.isEmpty())
            return null;

        if (linedBlocks.size() == 1)
            return linedBlocks.get(0);

//        linedBlocks.sort(Comparator.comparing(PDFRectangle::getTop));
        linedBlocks.sort(Comparator.comparing(entity -> entity.getBoundingBox().getTop()));
        TextEntity result = linedBlocks.get(0);

//        double left = result.getLeft();
//        double bottom = result.getBottom();
//        double right = result.getRight();
//        double top = result.getTop();

        double left = result.getBoundingBox().getX();
        double bottom = result.getBoundingBox().getY();
        double right = result.getBoundingBox().getRight();
        double top = result.getBoundingBox().getTop();

        String text = result.getText();
        int startOrder = result.getEndOrder();
        int endOrder = result.getEndOrder();

        String separator = System.lineSeparator();

        for (int i = 1; i < linedBlocks.size(); i++) {
            TextEntity block = linedBlocks.get(i);

            String blockText = block.getText();
            if (StringUtils.isNotBlank(blockText))
                text = text.concat(separator).concat(blockText);

//            left = Math.min(left, block.getLeft());
//            top = Math.min(top, block.getTop());
//            right = Math.max(right, block.getRight());
//            bottom = Math.max(bottom, block.getBottom());

            left = Math.min(left, block.getBoundingBox().getX());
            top = Math.min(top, block.getBoundingBox().getTop());
            right = Math.max(right, block.getBoundingBox().getRight());
            bottom = Math.max(bottom, block.getBoundingBox().getY());

            startOrder = Math.min(startOrder, block.getStartOrder());
            endOrder = Math.max(endOrder, block.getEndOrder());

            result.newTextLine(block);

            // Remove merged blocks except the first one from the page
            block.retract();
        }

//        result.setLeft(left);
//        result.setTop(top);
//        result.setRight(right);
//        result.setBottom(bottom);

        result.getBoundingBox().setX((float) left);
        result.getBoundingBox().setHeight((float) top);
        result.getBoundingBox().setWidth((float) right);
        result.getBoundingBox().setY((float) bottom);

        result.setText(text);
        result.setEndOrder(endOrder);

        return result;
    }
}