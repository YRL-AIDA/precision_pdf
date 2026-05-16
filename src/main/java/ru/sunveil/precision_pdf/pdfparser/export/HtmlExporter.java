package ru.sunveil.precision_pdf.pdfparser.export;

import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Header;
import ru.sunveil.precision_pdf.pdfparser.model.OtherBlock;
import ru.sunveil.precision_pdf.pdfparser.model.Paragraph;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.TableCell;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.PdfImage;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class HtmlExporter implements Exporter {

    private static final Pattern EMBEDDED_HTML_TABLE = Pattern.compile(
            "(?is)<table\\b[\\s\\S]*?</table>");

    @Override
    public String export(PdfDocument document, ExportFormat format) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<style>\n");
        sb.append("body{margin:0;padding:0;}\n");
        sb.append(".page{position:relative;margin:1rem auto; border:1px solid #ccc; overflow-y: visible;}\n");
        sb.append(".word{position:absolute;white-space:pre;}\n");
        sb.append(".textline{white-space:pre-wrap;overflow:hidden;}\n");
        sb.append(".header-block{font-weight:700;font-size:1.1em;margin:0.2rem 0;}\n");
        sb.append(".paragraph-block{margin:0.2rem 0;}\n");
        sb.append(".other-block{margin:0.2rem 0;color:#333;}\n");
        sb.append(".table-wrapper{overflow:auto;}\n");
        sb.append(".bold{font-weight:bold;}\n");
        sb.append(".italic{font-style:italic;}\n");
        sb.append("</style>\n</head>\n<body>\n");

        for (PdfPage page : document.getPages()) {
            sb.append("<div class=\"page\" style=\"width:")
                    .append(page.getWidth())
                    .append("pt;min-height:")
                    .append(page.getHeight())
                    .append("pt;\">\n");

            List<PageElement> textElements = new ArrayList<>();
            List<PdfImage> images = new ArrayList<>();

            if (hasSemanticBlocks(page)) {
                for (Header header : page.getHeaders()) {
                    if (header != null && header.getBoundingBox() != null) {
                        textElements.add(new PageElement(PageElementType.HEADER, header, header.getOrder()));
                    }
                }
                for (Paragraph paragraph : page.getParagraphs()) {
                    if (paragraph != null && paragraph.getBoundingBox() != null) {
                        textElements.add(new PageElement(PageElementType.PARAGRAPH, paragraph, paragraph.getOrder()));
                    }
                }
                for (OtherBlock otherBlock : page.getOtherBlocks()) {
                    if (otherBlock != null && otherBlock.getBoundingBox() != null) {
                        textElements.add(new PageElement(PageElementType.OTHER_BLOCK, otherBlock, otherBlock.getOrder()));
                    }
                }
            } else {
                for (TextLine tl : page.getTextLines()) {
                    if (tl == null || tl.getBoundingBox() == null) continue;
                    boolean insideTable = false;
                    for (Table table : page.getTables()) {
                        if (table != null && table.getBoundingBox() != null &&
                                table.getBoundingBox().intersects(tl.getBoundingBox())) {
                            insideTable = true;
                            break;
                        }
                    }
                    if (!insideTable) {
                        textElements.add(new PageElement(PageElementType.TEXT_LINE, tl, tl.getOrder()));
                    }
                }
            }

            for (Table table : page.getTables()) {
                if (table == null || table.getBoundingBox() == null) continue;
                textElements.add(new PageElement(PageElementType.TABLE, table, table.getOrder()));
            }

            for (PdfImage image : page.getImages()) {
                if (image == null || image.getBoundingBox() == null) continue;
                images.add(image);
            }

            textElements.sort(Comparator.comparingInt(e -> e.order));

            List<PageElement> finalElements = new ArrayList<>(textElements);
            boolean gnnLikePage = page.getSegments() != null && !page.getSegments().isEmpty();

            for (PdfImage image : images) {
                double imageTop = image.getBoundingBox().getY() + image.getBoundingBox().getHeight();
                double imageCenterY = image.getBoundingBox().getCenterY();
                int insertIndex = 0;
                for (int i = 0; i < finalElements.size(); i++) {
                    var elem = finalElements.get(i);
                    var bb = getBoundingBox(elem.element);
                    if (bb != null) {
                        if (gnnLikePage) {
                            double elemCenterY = bb.getCenterY();
                            if (elemCenterY < imageCenterY - 3.0) {
                                insertIndex = i;
                                break;
                            }
                            insertIndex = i + 1;
                        } else {
                            // Precision flow: preserve legacy top-based placement.
                            double elemTop = bb.getY() + bb.getHeight();
                            if (elemTop < imageTop - 5.0) {
                                insertIndex = i;
                                break;
                            }
                            insertIndex = i + 1;
                        }
                    }
                }
                finalElements.add(insertIndex, new PageElement(PageElementType.IMAGE, image, 0));
            }

            for (PageElement element : finalElements) {
                switch (element.type) {
                    case TEXT_LINE -> renderTextLine((TextLine) element.element, sb);
                    case HEADER -> renderHeader((Header) element.element, sb);
                    case PARAGRAPH -> renderParagraph((Paragraph) element.element, sb);
                    case OTHER_BLOCK -> renderOtherBlock((OtherBlock) element.element, sb);
                    case TABLE -> renderTable((Table) element.element, sb);
                    case IMAGE -> renderImage((PdfImage) element.element, sb);
                }
            }

            sb.append("</div>\n");
        }

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    /**
     * Получает BoundingBox из элемента любого типа
     */
    private BoundingBox getBoundingBox(Object element) {
        return switch (element) {
            case TextLine tl -> tl.getBoundingBox();
            case Header header -> header.getBoundingBox();
            case Paragraph paragraph -> paragraph.getBoundingBox();
            case OtherBlock otherBlock -> otherBlock.getBoundingBox();
            case Table table -> table.getBoundingBox();
            case PdfImage img -> img.getBoundingBox();
            default -> null;
        };
    }

    private boolean hasSemanticBlocks(PdfPage page) {
        return (page.getHeaders() != null && !page.getHeaders().isEmpty())
                || (page.getParagraphs() != null && !page.getParagraphs().isEmpty())
                || (page.getOtherBlocks() != null && !page.getOtherBlocks().isEmpty());
    }

    private void renderTextLine(TextLine tl, StringBuilder sb) {
        List<String> classes = new ArrayList<>();
        classes.add("textline");

        if (tl.getFont() != null) {
            if (tl.getFont().isBold()) classes.add("bold");
            if (tl.getFont().isItalic()) classes.add("italic");
        }

        sb.append("<div class=\"").append(String.join(" ", classes)).append("\">")
                .append(escapeHtml(tl.getText()))
                .append("</div>\n");
    }

    private void renderHeader(Header header, StringBuilder sb) {
        sb.append("<div class=\"header-block\">")
                .append(escapeHtml(header.getText()))
                .append("</div>\n");
    }

    private void renderParagraph(Paragraph paragraph, StringBuilder sb) {
        sb.append("<div class=\"paragraph-block\">")
                .append(escapeHtml(paragraph.getText()))
                .append("</div>\n");
    }

    private void renderOtherBlock(OtherBlock otherBlock, StringBuilder sb) {
        sb.append("<div class=\"other-block\" data-label=\"")
                .append(escapeHtml(otherBlock.getLabel()))
                .append("\">")
                .append(escapeHtml(otherBlock.getText()))
                .append("</div>\n");
    }

    private void renderTable(Table table, StringBuilder sb) {
        String embeddedHtml = table.getEmbeddedHtml();
        if (embeddedHtml == null || embeddedHtml.isBlank()) {
            embeddedHtml = findEmbeddedHtmlTable(table);
        }
        if (embeddedHtml != null && !embeddedHtml.isBlank()) {
            sb.append("<div class=\"table-wrapper\">");
            sb.append(styleEmbeddedHtmlTable(embeddedHtml));
            sb.append("</div>\n");
            return;
        }

        List<List<TableCell>> rowsToRender = table.isSkipHtmlCompaction()
                ? (table.getRows() != null ? table.getRows() : List.of())
                : compactTableRows(table.getRows());
        if (rowsToRender.isEmpty()) {
            return;
        }

        sb.append("<div class=\"table-wrapper\">");
        sb.append("<table style=\"border-collapse:collapse;\">");

        for (List<TableCell> row : rowsToRender) {
            sb.append("<tr>");
            if (row != null) {
                for (TableCell cell : row) {
                    if (cell == null) continue;
                    if (cell.getInvisible() == 1) continue;

                    int colspan = Math.max(1, cell.getColSpan());
                    int rowspan = Math.max(1, cell.getRowSpan());
                    String cellStyle = "border:1px solid #ccc;padding:4px;vertical-align:top;";
                    List<String> classes = new ArrayList<>();

                    if (cell.getContentBlocks() != null && !cell.getContentBlocks().isEmpty()) {
                        Object firstBlock = cell.getContentBlocks().getFirst();
                        if (firstBlock instanceof Word wordInCell && wordInCell.getFont() != null) {
                            if (wordInCell.getFont().isBold()) classes.add("bold");
                            if (wordInCell.getFont().isItalic()) classes.add("italic");
                        }
                    }

                    String content = escapeHtml(resolveCellContent(cell));
                    sb.append("<td");
                    if (colspan > 1) sb.append(" colspan=\"").append(colspan).append("\"");
                    if (rowspan > 1) sb.append(" rowspan=\"").append(rowspan).append("\"");
                    sb.append(" style=\"").append(cellStyle).append("\"");
                    if (!classes.isEmpty()) {
                        sb.append(" class=\"").append(String.join(" ", classes)).append("\"");
                    }
                    sb.append(">").append(content).append("</td>");
                }
            }
            sb.append("</tr>");
        }

        sb.append("</table>");
        sb.append("</div>\n");
    }

    private List<List<TableCell>> compactTableRows(List<List<TableCell>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<List<TableCell>> nonEmptyRows = rows.stream()
                .filter(row -> row != null && row.stream().anyMatch(this::hasCellContent))
                .toList();
        if (nonEmptyRows.isEmpty()) {
            return List.of();
        }

        int maxCols = nonEmptyRows.stream().mapToInt(List::size).max().orElse(0);
        boolean[] usedCols = new boolean[maxCols];
        for (List<TableCell> row : nonEmptyRows) {
            for (int i = 0; i < row.size(); i++) {
                if (hasCellContent(row.get(i))) {
                    usedCols[i] = true;
                }
            }
        }

        List<List<TableCell>> compact = new ArrayList<>();
        for (List<TableCell> row : nonEmptyRows) {
            List<TableCell> compactRow = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                if (i < usedCols.length && usedCols[i]) {
                    compactRow.add(row.get(i));
                }
            }
            if (compactRow.stream().anyMatch(this::hasCellContent)) {
                compact.add(compactRow);
            }
        }
        return compact;
    }

    private boolean hasCellContent(TableCell cell) {
        if (cell == null) return false;
        if (cell.getInvisible() == 1) return false;
        String content = resolveCellContent(cell);
        return content != null && !content.isBlank();
    }

    private String resolveCellContent(TableCell cell) {
        if (cell == null) return "";
        if (cell.getContentBlocks() == null || cell.getContentBlocks().isEmpty()) {
            return cell.getContent() != null ? cell.getContent() : "";
        }

        List<TextEntity> blocks = new ArrayList<>(cell.getContentBlocks());
        blocks.sort(Comparator
                .comparingDouble((TextEntity b) -> -b.getBoundingBox().getCenterY())
                .thenComparingDouble(b -> b.getBoundingBox().getX()));

        return blocks.stream()
                .map(TextEntity::getText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    private void renderImage(PdfImage image, StringBuilder sb) {
        if (image.getImageData() == null || image.getImageData().length == 0) return;

        String base64Data = Base64.getEncoder().encodeToString(image.getImageData());
        String mimeType = "image/" + (image.getImageFormat() != null ? image.getImageFormat().toLowerCase() : "png");
        var bbox = image.getBoundingBox();
        double bboxWidth = bbox != null ? bbox.getWidth() : 0.0;
        double bboxHeight = bbox != null ? bbox.getHeight() : 0.0;

        // Filter PDF artifacts (stencil-like 1x1 pseudo-images) that render as black placeholders.
        if (image.getWidth() <= 1 && image.getHeight() <= 1 && (bboxHeight <= 1 || bboxWidth > 100)) {
            return;
        }

        double width = bboxWidth;
        double height = bboxHeight;
        if (width <= 1 || height <= 1) {
            if (image.getWidth() > 0) width = image.getWidth();
            if (image.getHeight() > 0) height = image.getHeight();
        }
        if (width <= 1) width = 320;
        if (height <= 1) height = 180;
        double maxWidth = 800;
        double maxHeight = 600;

        if (width > maxWidth || height > maxHeight) {
            double scale = Math.min(maxWidth / width, maxHeight / height);
            width = width * scale;
            height = height * scale;
        }

        sb.append("<div style=\"display:inline-block;margin:8px 0;padding:3px;\">")
                .append("<img src=\"data:").append(mimeType).append(";base64,").append(base64Data)
                .append("\" style=\"width:").append(width).append("px;height:auto;max-width:100%;border:1px solid #ddd;\"")
                .append(" alt=\"PDF Image\">")
                .append("<div style=\"font-size:10px;color:#666;text-align:center;\">")
                .append("Image (").append(Math.round(width)).append("x").append(Math.round(height)).append(")")
                .append("</div>")
                .append("</div>\n");
    }

    private enum PageElementType {
        TEXT_LINE,
        HEADER,
        PARAGRAPH,
        OTHER_BLOCK,
        TABLE,
        IMAGE
    }

    private static class PageElement {
        final PageElementType type;
        final Object element;
        final int order;

        PageElement(PageElementType type, Object element, int order) {
            this.type = type;
            this.element = element;
            this.order = order;
        }
    }

    @Override
    public boolean supportsFormat(ExportFormat format) {
        return format == ExportFormat.HTML;
    }

    private String findEmbeddedHtmlTable(Table table) {
        if (table == null || table.getRows() == null) {
            return null;
        }
        for (List<TableCell> row : table.getRows()) {
            if (row == null) {
                continue;
            }
            for (TableCell cell : row) {
                String text = resolveCellContent(cell);
                if (text == null || text.isBlank()) {
                    continue;
                }
                Matcher matcher = EMBEDDED_HTML_TABLE.matcher(text.trim());
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    private String styleEmbeddedHtmlTable(String html) {
        String trimmed = html.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("<table") && !lower.contains("border-collapse")) {
            trimmed = trimmed.replaceFirst(
                    "(?i)<table\\b",
                    "<table style=\"border-collapse:collapse;width:100%;\"");
        }
        return trimmed
                .replaceAll("(?i)<td\\b", "<td style=\"border:1px solid #ccc;padding:4px;vertical-align:top;\"")
                .replaceAll("(?i)<th\\b", "<th style=\"border:1px solid #ccc;padding:4px;vertical-align:top;\"");
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}