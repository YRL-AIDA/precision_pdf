package ru.sunveil.precision_pdf.pdfparser.visualizer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.table.BorderedTableExtractor;

public class PdfBoundingBoxRenderer {
    public enum BoxType {
        WORDS, LINES, CHUNKS, RULINGS, AREAS, TABLES
    }
    /**
     * Нарисовать bounding box слов/линий/чанков
     *
     * @param inputPdf  исходный PDF-файл
     * @param outputPdf путь для сохранения PDF с рамками
     * @param pdfPages  результат парсинга PDF (список PdfPage)
     * @param boxType какие bbox отрисовать на странице
     */
    public void renderBoundingBoxes(File inputPdf, File outputPdf, List<PdfPage> pdfPages, BoxType boxType) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputPdf)) {
            for (PdfPage pdfPage : pdfPages) {
                int pageIndex = pdfPage.getPageNumber() - 1;
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) continue;

                PDPage page = document.getPage(pageIndex);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {

                    if (boxType == BoxType.WORDS) {
                        contentStream.setStrokingColor(Color.RED);
                        contentStream.setLineWidth(0.5f);
                        for (Word word : pdfPage.getWords()) {
                            BoundingBox bbox = word.getBoundingBox();
                            float x = bbox.getX();
                            float y = bbox.getY();
                            float width = bbox.getWidth();
                            float height = bbox.getHeight();
                            contentStream.addRect(x, y, width, height);
                            contentStream.stroke();
                            drawOrderText(contentStream, String.valueOf(word.getOrder()), x, y + height + 2);
                        }
                    }

                    if (boxType == BoxType.LINES) {
                        contentStream.setStrokingColor(Color.BLUE);
                        contentStream.setLineWidth(0.3f);
                        for (TextLine line : pdfPage.getTextLines()) {
                            BoundingBox bbox = line.getBoundingBox();
                            float x = bbox.getX();
                            float y = bbox.getY();
                            float width = bbox.getWidth();
                            float height = bbox.getHeight();
                            contentStream.addRect(x, y, width, height);
                            contentStream.stroke();
                            drawOrderText(contentStream, String.valueOf(line.getOrder()), x, y + height + 2);
                        }
                    }

                    if (boxType == BoxType.CHUNKS) {
                        contentStream.setStrokingColor(Color.GREEN);
                        contentStream.setLineWidth(0.3f);
                        for (PdfTextChunk chunk : pdfPage.getPdfTextChunks()) {
                            BoundingBox bbox = chunk.getBoundingBox();
                            if (bbox != null && bbox.isValid()) {
                                float x = bbox.getX();
                                float y = bbox.getY();
                                float width = bbox.getWidth();
                                float height = bbox.getHeight();
                                contentStream.addRect(x, y, width, height);
                                contentStream.stroke();
                                drawOrderText(contentStream, String.valueOf(chunk.getOrder()), x, y + height + 2);
                            }
                        }
                    }

                    if (boxType == BoxType.RULINGS){
                        contentStream.setStrokingColor(Color.GREEN);
                        contentStream.setLineWidth(0.3f);
                        float pageHeight = pdfPage.getBoundingBox().getHeight();
                        for (Ruling ruling : pdfPage.getVisibleRulings()) {
                            if (ruling != null) {
//                                Rectangle2D rec = ruling.getRect(pageHeight);
                                Rectangle rec = ruling.getBounds();
                                float x = (float) rec.getX();
                                float y = (float) rec.getY();
                                float width = (float) rec.getWidth();
                                float height = (float) rec.getHeight();
                                contentStream.addRect(x, y, width, height);
                                contentStream.stroke();
                            }
                        }
                    }

                    if (boxType == BoxType.AREAS){
                        contentStream.setStrokingColor(Color.red);
                        contentStream.setLineWidth(0.3f);
                        for (Table table : pdfPage.getTables()){
                            if (table == null)
                                return;
                            BoundingBox bb = table.getBoundingBox();

                            float pageHeight = (float) pdfPage.getHeight();

//                            float pdfY = pageHeight - bb.getY() - bb.getHeight();

                            contentStream.addRect(bb.getX(), bb.getY(), bb.getWidth(), bb.getHeight());
                            contentStream.stroke();
                        }
                    }

                    if (boxType == BoxType.TABLES){
                        contentStream.setStrokingColor(Color.red);
                        contentStream.setLineWidth(0.3f);
                        for (Table table : pdfPage.getTables()){
                            if (table == null)
                                return;
                            BoundingBox bb = table.getBoundingBox();

                            float pageHeight = (float) pdfPage.getHeight();

//                            float pdfY = pageHeight - bb.getY() - bb.getHeight();

                            contentStream.addRect(bb.getX(), bb.getY(), bb.getWidth(), bb.getHeight());
                            contentStream.stroke();
                            List<List<TableCell>> rows = table.getRows();
//                            if (rows == null) continue;
                            for (List<TableCell> row : rows) {
                                //                                if (row == null) continue;
                                for (TableCell cell : row) {
                                    BoundingBox cellBb = cell.getBoundingBox();
                                    float cellX = cellBb.getX();
                                    float cellY = pageHeight - cellBb.getY() - cellBb.getHeight();
                                    float cellWidth = cellBb.getWidth();
                                    float cellHeight = cellBb.getHeight();
                                    contentStream.addRect(cellX, cellBb.getY(), cellWidth, cellHeight);
                                    contentStream.stroke();
                                }
                            }
                        }
                    }
                }
            }
            document.save(outputPdf);
        }
    }

    /**
     * Draw order text above a bounding box
     */
    private void drawOrderText(PDPageContentStream contentStream, String orderText, float x, float y) throws IOException {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
        contentStream.setNonStrokingColor(Color.BLACK); // Black text
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(orderText);
        contentStream.endText();
    }
}