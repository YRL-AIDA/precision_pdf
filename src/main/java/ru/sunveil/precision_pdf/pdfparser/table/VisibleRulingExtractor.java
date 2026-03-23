package ru.sunveil.precision_pdf.pdfparser.table;

import lombok.Getter;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDAbstractPattern;
import org.apache.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import org.apache.pdfbox.rendering.ImageType;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.util.GeometryUtils;
import ru.sunveil.precision_pdf.pdfparser.util.Utils;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class VisibleRulingExtractor {

    private static final int GRAYSCALE_INTENSITY_THRESHOLD = 25;
    private static final int VERTICAL_EDGE_HEIGHT_MINIMUM = 10;
    private static final int HORIZONTAL_EDGE_WIDTH_MINIMUM = 30;
    private static final int EXPAND_AMOUNT = 1;

    private final PDDocument pdDocument;
    @Getter
    private final List<List<Ruling>> visibleRulings;

    private void release() {
        visibleRulings.clear();
    }

    public VisibleRulingExtractor(PDDocument pdDocument) {
        this.pdDocument = pdDocument;
        visibleRulings = new ArrayList<>(200);
    }

    public List<Ruling> extractVisibleRulingsForOnePage(PDPage pdPage) throws IOException {
        List<Ruling> resultRulings = new ArrayList<>();

        List<Object> newTokens = createTokensWithoutText(pdPage);
        PDStream newContents = new PDStream(pdDocument);
        writeTokensToStream(newContents, newTokens);

        PDRectangle cropBox = pdPage.getCropBox();
        float cropOffsetX = cropBox.getLowerLeftX();
        float cropOffsetY = cropBox.getLowerLeftY();

        PDPage tempPage = new PDPage(pdPage.getMediaBox());
        tempPage.setResources(pdPage.getResources());
        tempPage.setContents(newContents);

        BufferedImage image = Utils.convertPageToImage(tempPage, 144, ImageType.GRAY);
        List<Ruling> horizontalRulings = getHorizontalRulings(image);
        List<Ruling> verticalRulings = getVerticalRulings(image);

        List<Ruling> allEdges = new ArrayList<>();
        allEdges.addAll(horizontalRulings);
        allEdges.addAll(verticalRulings);

        if (!allEdges.isEmpty()) {
            GeometryUtils.snapPoints(allEdges, GeometryUtils.POINT_SNAP_DISTANCE_THRESHOLD, GeometryUtils.POINT_SNAP_DISTANCE_THRESHOLD);

            for (List<Ruling> rulings : Arrays.asList(horizontalRulings, verticalRulings)) {
                for (Iterator<Ruling> iterator = rulings.iterator(); iterator.hasNext(); ) {
                    Ruling ruling = iterator.next();

                    ruling.normalize();
                    if (ruling.oblique()) {
                        iterator.remove();
                    }
                }
            }

            horizontalRulings = GeometryUtils.collapseOrientedRulings(horizontalRulings, 5);
            verticalRulings = GeometryUtils.collapseOrientedRulings(verticalRulings, 5);

            for (Ruling ruling : horizontalRulings) {
                float x1 = cropOffsetX + ruling.x1 / 2;
                float x2 = cropOffsetX + ruling.x2 / 2;
                float y2 = cropOffsetY + pdPage.getMediaBox().getHeight() - ruling.y1 / 2;
                float y1 = cropOffsetY + pdPage.getMediaBox().getHeight() - ruling.y2 / 2;
                ruling.setLine(x1, y1, x2, y2);
            }
            resultRulings.addAll(horizontalRulings);
            for (Ruling ruling : verticalRulings) {
                float x1 = cropOffsetX + ruling.x1 / 2;
                float x2 = cropOffsetX + ruling.x2 / 2;
                float y2 = cropOffsetY + pdPage.getMediaBox().getHeight() - ruling.y1 / 2;
                float y1 = cropOffsetY + pdPage.getMediaBox().getHeight() - ruling.y2 / 2;
                ruling.setLine(x1, y1, x2, y2);
            }
            resultRulings.addAll(verticalRulings);
        }

        return resultRulings;
    }

    public void extractVisibleRulings(PDDocument document) throws IOException {
        for(int i = 0; i < document.getNumberOfPages(); i++){
            List<Ruling> pageRulings = extractVisibleRulingsForOnePage(document.getPage(i));
            visibleRulings.add(pageRulings);
        }
    }


    private void removeAllImages(PDResources resources) throws IOException {
        for (COSName objectName: resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(objectName);
            if (xObject != null && xObject instanceof PDImageXObject) {

                PDImageXObject image = (PDImageXObject) xObject;
                COSStream cosStream = image.getCOSObject();
                //image.setHeight(1);
                //image.setWidth(1);
                //deleteObjects(cosStream);
/*                if (cosStream != null) {
                    cosStream.clear();
                }*/
            }
        }
    }

    private void deleteObjects(COSDictionary cosStream) {
        Iterator<COSName> cosNameIterator = cosStream.keySet().iterator();
        while (cosNameIterator.hasNext()) {
            COSName cosName = cosNameIterator.next();
            COSBase object = cosStream.getDictionaryObject(cosName);
            if (object instanceof COSDictionary) {
                deleteObjects((COSDictionary) object);
            }
            cosNameIterator.remove();
        }
    }

    private static void processResources(PDResources resources) throws IOException {
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject != null && xObject instanceof PDFormXObject) {
                PDFormXObject formXObject = (PDFormXObject) xObject;
                writeTokensToStream(formXObject.getContentStream(),
                        createTokensWithoutText(formXObject));
                if (formXObject.getResources() != null
                        && formXObject.getResources() != resources
                        && formXObject.getResources().getCOSObject() != resources.getCOSObject()) {
                    processResources(formXObject.getResources());
                }
            }
        }
        for (COSName name : resources.getPatternNames()) {
            PDAbstractPattern pattern = resources.getPattern(name);
            if (pattern != null && pattern instanceof PDTilingPattern) {
                PDTilingPattern tilingPattern = (PDTilingPattern) pattern;
                writeTokensToStream(tilingPattern.getContentStream(),
                        createTokensWithoutText(tilingPattern));
                processResources(tilingPattern.getResources());
            }
        }
    }

    private static void writeTokensToStream(PDStream newContents, List<Object> newTokens) throws IOException {
        if (newContents != null) {
            try (OutputStream out = newContents.createOutputStream(COSName.FLATE_DECODE)) {
                ContentStreamWriter writer = new ContentStreamWriter(out);
                writer.writeTokens(newTokens);
                out.close();
            }
        }
    }

    private static List<Object> createTokensWithoutText(PDContentStream contentStream) throws IOException {

        PDFStreamParser parser = new PDFStreamParser(contentStream);
        Object token = parser.parseNextToken();
        List<Object> newTokens = new ArrayList<>();

        while (token != null) {
            if (token instanceof Operator) {
                Operator operation = (Operator) token;
                if ("TJ".equals(operation.getName())
                        || "Tj".equals(operation.getName())
                        || "'".equals(operation.getName())
                        || "\"".equals(operation.getName())
                        || "Do".equals(operation.getName()))
                {
                    newTokens.remove(newTokens.size() - 1);
                    token = parser.parseNextToken();
                    continue;
                }
            }
            newTokens.add(token);
            token = parser.parseNextToken();
        }
        return newTokens;
    }

    public List<Ruling> getHorizontalRulings(BufferedImage image) throws IOException {

        ArrayList<Ruling> horizontalRulings = new ArrayList<>();

        if (image == null) {
            return new ArrayList<>();
        }

        Raster r = image.getRaster();

        int width = r.getWidth();
        int height = r.getHeight();

        for (int x = 0; x < width; x++) {

            int[] lastPixel = r.getPixel(x, 0, (int[]) null);

            for (int y = 1; y < height - 1; y++) {

                int[] currPixel = r.getPixel(x, y, (int[]) null);

                int diff = Math.abs(currPixel[0] - lastPixel[0]);
                if (diff > GRAYSCALE_INTENSITY_THRESHOLD) {
                    boolean alreadyChecked = false;
                    for (Ruling line : horizontalRulings) {
                        if (y == line.getP1().getY()
                                && x >= line.getP1().getX()
                                && x <= line.getP2().getX()) {
                            alreadyChecked = true;
                            break;
                        }
                    }

                    if (alreadyChecked) {
                        lastPixel = currPixel;
                        continue;
                    }
                    int lineX = x + 1;

                    while (lineX < width) {
                        int[] linePixel = r.getPixel(lineX, y, (int[]) null);
                        int[] abovePixel = r.getPixel(lineX, y - 1, (int[]) null);

                        if (Math.abs(linePixel[0] - abovePixel[0]) <= GRAYSCALE_INTENSITY_THRESHOLD
                                || Math.abs(currPixel[0] - linePixel[0]) > GRAYSCALE_INTENSITY_THRESHOLD) {
                            break;
                        }
                        lineX++;
                    }
                    int endX = lineX - 1;
                    int lineWidth = endX - x;
                    if (lineWidth > HORIZONTAL_EDGE_WIDTH_MINIMUM) {
                        horizontalRulings.add(new Ruling(new Point2D.Float(x, y), new Point2D.Float(endX, y)));
                    }
                }
                lastPixel = currPixel;
            }
        }
        return horizontalRulings;
    }
    public List<Ruling> getVerticalRulings(BufferedImage image) {
        ArrayList<Ruling> verticalRulings = new ArrayList<>();

        if (image == null) {
            return new ArrayList<>();
        }

        Raster r = image.getRaster();
        int width = r.getWidth();
        int height = r.getHeight();

        for (int y = 0; y < height; y++) {

            int[] lastPixel = r.getPixel(0, y, (int[]) null);

            for (int x = 1; x < width - 1; x++) {

                int[] currPixel = r.getPixel(x, y, (int[]) null);

                int diff = Math.abs(currPixel[0] - lastPixel[0]);
                if (diff > GRAYSCALE_INTENSITY_THRESHOLD) {
                    boolean alreadyChecked = false;
                    for (Ruling line : verticalRulings) {
                        if (x == line.getP1().getX()
                                && y >= line.getP1().getY()
                                && y <= line.getP2().getY()) {
                            alreadyChecked = true;
                            break;
                        }
                    }

                    if (alreadyChecked) {
                        lastPixel = currPixel;
                        continue;
                    }

                    int lineY = y + 1;

                    while (lineY < height) {
                        int[] linePixel = r.getPixel(x, lineY, (int[]) null);
                        int[] leftPixel = r.getPixel(x - 1, lineY, (int[]) null);

                        if (Math.abs(linePixel[0] - leftPixel[0]) <= GRAYSCALE_INTENSITY_THRESHOLD
                                || Math.abs(currPixel[0] - linePixel[0]) > GRAYSCALE_INTENSITY_THRESHOLD) {
                            break;
                        }

                        lineY++;
                    }

                    int endY = lineY - 1;
                    int lineLength = endY - y;
                    if (lineLength > VERTICAL_EDGE_HEIGHT_MINIMUM) {
                        verticalRulings.add(new Ruling(new Point2D.Float(x, y), new Point2D.Float(x, endY)));
                    }
                }

                lastPixel = currPixel;
            }
        }

        return verticalRulings;
    }

}
