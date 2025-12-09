package ru.sunveil.precision_pdf.pdfparser.visualizer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.PdfTextChunk;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

public class PdfBoundingBoxRenderer {
    public enum BoxType {
        WORDS, LINES, CHUNKS
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