package ru.sunveil.precision_pdf.pdfparser.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class Utils {
    public static BufferedImage convertPageToImage(PDPage page, int dpi, ImageType imageType) {
        try (PDDocument document = new PDDocument()) {
            document.addPage(page);
            PDFRenderer renderer = new PDFRenderer(document);
            //document.close();
            return renderer.renderImageWithDPI(0, dpi, imageType);
        } catch (IOException e) {
            return null;
        }
    }
}
