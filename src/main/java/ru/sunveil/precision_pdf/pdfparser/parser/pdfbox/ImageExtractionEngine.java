package ru.sunveil.precision_pdf.pdfparser.parser.pdfbox;

import lombok.Getter;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.contentstream.operator.state.*;
import org.apache.pdfbox.util.Matrix;
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
 * с определением координат изображений на странице
 */
public class ImageExtractionEngine extends PDFStreamEngine implements ImageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ImageExtractionEngine.class);

    private final float imageDpi;
    private final int maxImageSize;
    private final boolean preserveQuality;

    private PDPage currentPage;
    private List<PdfImage> currentPageImages;
    private int currentPageNumber;

    private float pageWidth;
    private float pageHeight;

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

        // Добавляем необходимые операторы для обработки трансформаций и отрисовки
        addOperator(new Concatenate(this));
        addOperator(new DrawObject(this));
        addOperator(new SetGraphicsStateParameters(this));
        addOperator(new Save(this));
        addOperator(new Restore(this));
        addOperator(new SetMatrix(this));
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

        this.currentPage = page;
        this.currentPageNumber = pageNumber;
        this.currentPageImages = new ArrayList<>();

        PDRectangle mediaBox = page.getMediaBox();
        this.pageWidth = mediaBox.getWidth();
        this.pageHeight = mediaBox.getHeight();

        try {
            processPage(page);
        } catch (Exception e) {
            logger.warn("Failed to process page {} for image positions: {}", pageNumber, e.getMessage());
        }

        if (currentPageImages.isEmpty()) {
            logger.debug("No images found via stream processing, trying alternative extraction for page {}", pageNumber);

            images.addAll(extractInlineImages(page, pageNumber));

            images.addAll(extractXObjectImages(page, pageNumber));
        } else {
            images.addAll(currentPageImages);
        }

        this.currentPage = null;
        this.currentPageImages = null;

        return images;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();

        if ("Do".equals(operation)) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject) xobject;

                Matrix ctmNew = getGraphicsState().getCurrentTransformationMatrix();

                float pdfX = ctmNew.getTranslateX();
                float pdfY = ctmNew.getTranslateY();
                float pdfWidth = ctmNew.getScalingFactorX();
                float pdfHeight = ctmNew.getScalingFactorY();

                BoundingBox boundingBox = new BoundingBox(pdfX, pdfY, pdfWidth, pdfHeight);

                PdfImage pdfImage = convertPdImageToPdfImage(image, currentPageNumber);
                pdfImage.setBoundingBox(boundingBox);

                currentPageImages.add(pdfImage);
                logger.debug("Extracted image with bounding box: {}", boundingBox);

            } else if (xobject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xobject;
                showForm(form);
            }
        } else {
            super.processOperator(operator, operands);
        }
    }

    /**
     * Извлекает встроенные изображения со страницы (альтернативный метод, если stream engine не сработал)
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
                pdfImage.setBoundingBox(createDefaultBoundingBox(pdImage));
                images.add(pdfImage);

                logger.debug("Extracted inline image: {}x{}, format: {}",
                        pdfImage.getWidth(), pdfImage.getHeight(), pdfImage.getImageFormat());
            }
        }

        return images;
    }

    /**
     * Извлекает XObject изображения со страницы (альтернативный метод)
     */
    private List<PdfImage> extractXObjectImages(PDPage page, int pageNumber) {
        List<PdfImage> images = new ArrayList<>();
        PDResources resources = page.getResources();

        if (resources == null) {
            return images;
        }

        try {
            Iterable<COSName> xObjectNames = resources.getXObjectNames();
            for (COSName xObjectName : xObjectNames) {
                PDXObject xObject = resources.getXObject(xObjectName);

                if (xObject instanceof PDImageXObject) {
                    PDImageXObject pdImage = (PDImageXObject) xObject;
                    PdfImage pdfImage = convertPdImageToPdfImage(pdImage, pageNumber);
                    pdfImage.setBoundingBox(createDefaultBoundingBox(pdImage));
                    images.add(pdfImage);

                    logger.debug("Extracted XObject image: {}x{}, format: {}",
                            pdfImage.getWidth(), pdfImage.getHeight(), pdfImage.getImageFormat());
                } else if (xObject instanceof PDFormXObject) {
                    PDFormXObject form = (PDFormXObject) xObject;
                    List<PdfImage> formImages = extractImagesFromForm(form, pageNumber);
                    images.addAll(formImages);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to extract XObject images from page {}: {}", pageNumber, e.getMessage());
        }

        return images;
    }

    /**
     * Рекурсивно извлекает изображения из PDFormXObject
     */
    private List<PdfImage> extractImagesFromForm(PDFormXObject form, int pageNumber) throws IOException {
        List<PdfImage> images = new ArrayList<>();
        PDResources formResources = form.getResources();

        if (formResources == null) {
            return images;
        }

        Iterable<COSName> xObjectNames = formResources.getXObjectNames();
        for (COSName xObjectName : xObjectNames) {
            PDXObject xObject = formResources.getXObject(xObjectName);

            if (xObject instanceof PDImageXObject) {
                PDImageXObject pdImage = (PDImageXObject) xObject;
                PdfImage pdfImage = convertPdImageToPdfImage(pdImage, pageNumber);
                pdfImage.setBoundingBox(createDefaultBoundingBox(pdImage));
                images.add(pdfImage);

                logger.debug("Extracted image from form: {}x{}, format: {}",
                        pdfImage.getWidth(), pdfImage.getHeight(), pdfImage.getImageFormat());
            } else if (xObject instanceof PDFormXObject) {
                // Recursively extract from nested forms
                PDFormXObject nestedForm = (PDFormXObject) xObject;
                List<PdfImage> nestedImages = extractImagesFromForm(nestedForm, pageNumber);
                images.addAll(nestedImages);
            }
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

        // Получение данных изображения
        pdfImage.setImageData(getImageData(pdImage));

        return pdfImage;
    }

    /**
     * Создает ограничивающую рамку по умолчанию для изображений без координат
     */
    private BoundingBox createDefaultBoundingBox(PDImageXObject pdImage) {
        return new BoundingBox(0, 0, pdImage.getWidth(), pdImage.getHeight());
    }

    /**
     * Определяет формат изображения
     */
    private String determineImageFormat(PDImageXObject pdImage) {
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