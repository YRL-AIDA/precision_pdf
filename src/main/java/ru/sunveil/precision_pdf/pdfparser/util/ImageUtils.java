package ru.sunveil.precision_pdf.pdfparser.util;

import ru.sunveil.precision_pdf.pdfparser.model.PdfImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Утилиты для работы с изображениями PDF
 */
public class ImageUtils {

    private ImageUtils() {
        // Utility class
    }

    /**
     * Конвертирует изображение в base64 строку
     */
    public static String toBase64(PdfImage pdfImage) {
        if (pdfImage.getImageData() == null) {
            return "";
        }

        return Base64.getEncoder().encodeToString(pdfImage.getImageData());
    }

    /**
     * Получает MIME тип изображения
     */
    public static String getMimeType(PdfImage pdfImage) {
        String format = pdfImage.getImageFormat().toLowerCase();

        switch (format) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "tiff":
            case "tif":
                return "image/tiff";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * Получает информацию о размере изображения из байтовых данных
     */
    public static ImageSize getImageSize(byte[] imageData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
            BufferedImage bufferedImage = ImageIO.read(bais);
            if (bufferedImage != null) {
                return new ImageSize(bufferedImage.getWidth(), bufferedImage.getHeight());
            }
        }
        return new ImageSize(0, 0);
    }

    /**
     * Проверяет, является ли изображение валидным
     */
    public static boolean isValidImage(PdfImage pdfImage) {
        if (pdfImage.getImageData() == null || pdfImage.getImageData().length == 0) {
            return false;
        }

        try {
            ImageSize size = getImageSize(pdfImage.getImageData());
            return size.getWidth() > 0 && size.getHeight() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Класс для представления размеров изображения
     */
    public static class ImageSize {
        private final int width;
        private final int height;

        public ImageSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }

        @Override
        public String toString() {
            return width + "x" + height;
        }
    }
}