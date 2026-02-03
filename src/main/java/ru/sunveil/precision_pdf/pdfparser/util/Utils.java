package ru.sunveil.precision_pdf.pdfparser.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;

public class Utils {
    private final static float EPSILON = 0.01f;
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

    public static float round(double d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    public static boolean feq(double f1, double f2) {
        return (Math.abs(f1 - f2) < EPSILON);
    }

}
