package ru.sunveil.precision_pdf.pdfparser.util;

import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

public class GeometryUtils {
    public static boolean isOverlapping(BoundingBox box1, BoundingBox box2) {
        return box1.getX() < box2.getX() + box2.getWidth() &&
                box1.getX() + box1.getWidth() > box2.getX() &&
                box1.getY() < box2.getY() + box2.getHeight() &&
                box1.getY() + box1.getHeight() > box2.getY();
    }

    public static double calculateDistance(BoundingBox box1, BoundingBox box2) {
        float centerX1 = box1.getX() + box1.getWidth() / 2;
        float centerY1 = box1.getY() + box1.getHeight() / 2;
        float centerX2 = box2.getX() + box2.getWidth() / 2;
        float centerY2 = box2.getY() + box2.getHeight() / 2;

        return Math.sqrt(Math.pow(centerX2 - centerX1, 2) + Math.pow(centerY2 - centerY1, 2));
    }
}