package ru.sunveil.precision_pdf.pdfparser.visualizer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
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

    /**
     * Нарисовать bounding box слов/линий/чанков
     *
     * @param inputPdf  исходный PDF-файл
     * @param outputPdf путь для сохранения PDF с рамками
     * @param pdfPages  результат парсинга PDF (список PdfPage)
     */
    public void renderBoundingBoxes(File inputPdf, File outputPdf, List<PdfPage> pdfPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputPdf)) {
            for (PdfPage pdfPage : pdfPages) {
                int pageIndex = pdfPage.getPageNumber() - 1;
                if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) continue;

                PDPage page = document.getPage(pageIndex);
                PDRectangle mediaBox = page.getMediaBox();

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {

                    // слова
//                    contentStream.setStrokingColor(Color.RED);
//                    contentStream.setLineWidth(0.5f);
//
//                    for (Word word : pdfPage.getWords()) {
//                        BoundingBox bbox = word.getBoundingBox();
//                        float x = bbox.getX();
//                        float y = bbox.getY();
//                        float width = bbox.getWidth();
//                        float height = bbox.getHeight();
//                        contentStream.addRect(x, y, width, height);
//                        contentStream.stroke();
//                    }

//                    // линии
//                    contentStream.setStrokingColor(Color.BLUE);
//                    contentStream.setLineWidth(0.3f);
//                    for (TextLine line : pdfPage.getTextLines()) {
//                        BoundingBox bbox = line.getBoundingBox();
//                        float x = bbox.getX();
//                        float y = bbox.getY();
//                        float width = bbox.getWidth();
//                        float height = bbox.getHeight();
//                        contentStream.addRect(x, y, width, height);
//                        contentStream.stroke();
//                    }
                    contentStream.setStrokingColor(Color.GREEN); // выбрал зелёный для отличия
                    contentStream.setLineWidth(0.3f);

                    // чанки
                    for (PdfTextChunk chunk : pdfPage.getPdfTextChunks()) {
                        BoundingBox bbox = chunk.getBoundingBox();
                        if (bbox != null && bbox.isValid()) {
                            float x = bbox.getX();
                            float y = bbox.getY();
                            float width = bbox.getWidth();
                            float height = bbox.getHeight();
                            contentStream.addRect(x, y, width, height);
                            contentStream.stroke();
                        }
                    }
                }
            }

            document.save(outputPdf);
        }
    }
}
