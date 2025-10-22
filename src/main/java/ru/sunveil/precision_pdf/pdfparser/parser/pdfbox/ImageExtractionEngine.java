package ru.sunveil.precision_pdf.pdfparser.parser.pdfbox;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.pdfparser.model.PdfImage;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.parser.ImageExtractor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Движок для извлечения изображений из PDF документов с использованием PDFBox
 */
public class ImageExtractionEngine implements ImageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ImageExtractionEngine.class);

    private final float imageDpi;
    private final int maxImageSize;
    private final boolean preserveQuality;

    /**
     * Конструктор с параметрами по умолчанию
     */
    public ImageExtractionEngine() {
        this(150, 2048, true);
    }

    /**
     * Конструктор с настраиваемыми параметрами
     *
     * @param imageDpi DPI для рендеринга изображений
     * @param maxImageSize максимальный размер изображения в пикселях
     * @param preserveQuality сохранять ли качество изображения
     */
    public ImageExtractionEngine(float imageDpi, int maxImageSize, boolean preserveQuality) {
        this.imageDpi = imageDpi;
        this.maxImageSize = maxImageSize;
        this.preserveQuality = preserveQuality;
    }

    @Override
    public List<PdfImage> extractImages(PDDocument document) throws IOException {
        List<PdfImage> allImages = new ArrayList<>();
        int totalPages = document.getNumberOfPages();

        logger.info("Starting image extraction from {} pages", totalPages);

        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            List<PdfImage> pageImages = extractImagesFromPage(document, pageNumber);
            allImages.addAll(pageImages);

            logger.debug("Extracted {} images from page {}", pageImages.size(), pageNumber);
        }

        logger.info("Total images extracted: {}", allImages.size());
        return allImages;
    }

    @Override
    public List<PdfImage> extractImagesFromPage(PDDocument document, int pageNumber) throws IOException {
        List<PdfImage> images = new ArrayList<>();
        PDPage page = document.getPage(pageNumber - 1); // PDFBox uses 0-based indexing

        if (page == null) {
            logger.warn("Page {} not found in document", pageNumber);
            return images;
        }

        // Извлечение встроенных изображений
        images.addAll(extractInlineImages(page, pageNumber));

        // Извлечение XObject изображений
        images.addAll(extractXObjectImages(page, pageNumber));

        // Рендеринг страницы как изображения (если нужно)
        if (images.isEmpty()) {
            images.addAll(renderPageAsImage(document, page, pageNumber));
        }

        return images;
    }

    /**
     * Извлекает встроенные изображения со страницы
     */
    private List<PdfImage> extractInlineImages(PDPage page, int pageNumber) throws IOException {
        List<PdfImage> images = new ArrayList<>();
        PDResources resources = page.getResources();

        if (resources == null) {
            return images;
        }

        Iterable<COSName> xObjectNames = resources.getXObjectNames();
        for (COSName xObjectName : xObjectNames) {
            PDXObject xObject = resources.getXObject(xObjectName);

            if (xObject instanceof PDImageXObject) {
                PDImageXObject pdImage = (PDImageXObject) xObject;
                PdfImage pdfImage = convertPdImageToPdfImage(pdImage, pageNumber);
                images.add(pdfImage);

                logger.debug("Extracted inline image: {}x{}, format: {}",
                        pdfImage.getWidth(), pdfImage.getHeight(), pdfImage.getImageFormat());
            }
        }

        return images;
    }

    /**
     * Извлекает XObject изображения со страницы
     */
    private List<PdfImage> extractXObjectImages(PDPage page, int pageNumber) {
        List<PdfImage> images = new ArrayList<>();
        // Реализация извлечения XObject изображений
        // Этот метод может быть расширен для обработки специфических типов XObject

        return images;
    }

    /**
     * Рендерит всю страницу как изображение
     */
    private List<PdfImage> renderPageAsImage(PDDocument document, PDPage page, int pageNumber) throws IOException {
        List<PdfImage> images = new ArrayList<>();

        try {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage bufferedImage = renderer.renderImageWithDPI(pageNumber - 1, imageDpi, ImageType.RGB);

            // Масштабирование изображения если необходимо
            bufferedImage = scaleImageIfNeeded(bufferedImage);

            PdfImage pdfImage = convertBufferedImageToPdfImage(bufferedImage, pageNumber);
            images.add(pdfImage);

            logger.debug("Rendered page as image: {}x{}, format: {}",
                    pdfImage.getWidth(), pdfImage.getHeight(), pdfImage.getImageFormat());

        } catch (Exception e) {
            logger.warn("Failed to render page {} as image: {}", pageNumber, e.getMessage());
        }

        return images;
    }

    /**
     * Конвертирует PDImageXObject в PdfImage
     */
    private PdfImage convertPdImageToPdfImage(PDImageXObject pdImage, int pageNumber) throws IOException {
        PdfImage pdfImage = new PdfImage();

        // Установка базовых свойств
        pdfImage.setPageNumber(pageNumber);
        pdfImage.setWidth(pdImage.getWidth());
        pdfImage.setHeight(pdImage.getHeight());
        pdfImage.setImageFormat(determineImageFormat(pdImage));
        pdfImage.setResolution(imageDpi);
        pdfImage.setColorSpace(pdImage.getColorSpace().getName());
        pdfImage.setId(generateImageId(pageNumber, pdfImage.getImageFormat()));

        // Установка ограничивающей рамки
        pdfImage.setBoundingBox(createImageBoundingBox(pdImage, pageNumber));

        // Получение данных изображения
        pdfImage.setImageData(getImageData(pdImage));

        return pdfImage;
    }

    /**
     * Конвертирует BufferedImage в PdfImage
     */
    private PdfImage convertBufferedImageToPdfImage(BufferedImage bufferedImage, int pageNumber) throws IOException {
        PdfImage pdfImage = new PdfImage();

        pdfImage.setPageNumber(pageNumber);
        pdfImage.setWidth(bufferedImage.getWidth());
        pdfImage.setHeight(bufferedImage.getHeight());
        pdfImage.setImageFormat("PNG");
        pdfImage.setResolution(imageDpi);
        pdfImage.setColorSpace("RGB");
        pdfImage.setId(generateImageId(pageNumber, "PNG"));

        // Создание ограничивающей рамки для всей страницы
        pdfImage.setBoundingBox(new BoundingBox(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight()));

        // Конвертация в байтовый массив
        pdfImage.setImageData(convertBufferedImageToByteArray(bufferedImage, "PNG"));

        return pdfImage;
    }

    /**
     * Определяет формат изображения
     */
    private String determineImageFormat(PDImage pdImage) {
        try {
            String suffix = pdImage.getSuffix();
            if (suffix != null && !suffix.isEmpty()) {
                return suffix.toUpperCase();
            }
        } catch (Exception e) {
            logger.debug("Could not determine image format: {}", e.getMessage());
        }

        // Формат по умолчанию
        return "JPEG";
    }

    /**
     * Создает ограничивающую рамку для изображения
     */
    private BoundingBox createImageBoundingBox(PDImageXObject pdImage, int pageNumber) {
        // В реальной реализации здесь должна быть логика определения позиции изображения на странице
        // Для упрощения возвращаем рамку с размерами изображения
        return new BoundingBox(0, 0, pdImage.getWidth(), pdImage.getHeight());
    }

    /**
     * Получает данные изображения в виде байтового массива
     */
    private byte[] getImageData(PDImageXObject pdImage) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String format = determineImageFormat(pdImage);

        BufferedImage bufferedImage = pdImage.getImage();
        ImageIO.write(bufferedImage, format, baos);

        return baos.toByteArray();
    }

    /**
     * Конвертирует BufferedImage в байтовый массив
     */
    private byte[] convertBufferedImageToByteArray(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    /**
     * Масштабирует изображение если оно превышает максимальный размер
     */
    private BufferedImage scaleImageIfNeeded(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        if (width <= maxImageSize && height <= maxImageSize) {
            return originalImage;
        }

        double scaleFactor = Math.min(
                (double) maxImageSize / width,
                (double) maxImageSize / height
        );

        int newWidth = (int) (width * scaleFactor);
        int newHeight = (int) (height * scaleFactor);

        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, originalImage.getType());
        java.awt.Graphics2D g2d = scaledImage.createGraphics();

        if (preserveQuality) {
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }

        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        logger.debug("Scaled image from {}x{} to {}x{}", width, height, newWidth, newHeight);

        return scaledImage;
    }

    /**
     * Генерирует уникальный идентификатор для изображения
     */
    private String generateImageId(int pageNumber, String format) {
        return String.format("img_%d_%s_%d", pageNumber, format, System.currentTimeMillis());
    }

    @Override
    public boolean supportsImageExtraction() {
        return true;
    }

    /**
     * Очищает ресурсы (если необходимо)
     */
    public void cleanup() {
        // В текущей реализации очистка не требуется
    }
}