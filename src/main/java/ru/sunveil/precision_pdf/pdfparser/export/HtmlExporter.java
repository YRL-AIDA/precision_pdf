package ru.sunveil.precision_pdf.pdfparser.export;

import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.TableCell;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.PdfImage;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Component
public class HtmlExporter implements Exporter {

    @Override
    public String export(PdfDocument document, ExportFormat format) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<style>\n");
        sb.append("body{margin:0;padding:0;}\n");
        sb.append(".page{position:relative;margin:1rem auto; border:1px solid #ccc; overflow-y: visible;}\n");
        sb.append(".word{position:absolute;white-space:pre;}\n");
        sb.append(".textline{white-space:pre-wrap;overflow:hidden;}\n");
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

            List<PageElement> elements = new ArrayList<>();

            for (TextLine tl : page.getTextLines()) {
                if (tl == null || tl.getBoundingBox() == null) continue;

                boolean insideTable = false;
                for (Table table : page.getTables()) {
                    if (table == null || table.getBoundingBox() == null) continue;
                    if (table.getBoundingBox().intersects(tl.getBoundingBox())) {
                        insideTable = true;
                        break;
                    }
                }

                if (!insideTable) {
                    elements.add(new PageElement(PageElementType.TEXT_LINE, tl, tl.getOrder()));
                }
            }

            for (Table table : page.getTables()) {
                if (table == null || table.getBoundingBox() == null) continue;
                elements.add(new PageElement(PageElementType.TABLE, table, table.getOrder()));
            }

            for (PdfImage image : page.getImages()) {
                if (image == null || image.getBoundingBox() == null) continue;
                elements.add(new PageElement(PageElementType.IMAGE, image, 0)); // Images don't have order, so use 0
            }

            elements.sort(Comparator.comparingInt(e -> e.order));

            for (PageElement element : elements) {
                switch (element.type) {
                    case TEXT_LINE:
                        renderTextLine((TextLine) element.element, sb);
                        break;
                    case TABLE:
                        renderTable((Table) element.element, sb);
                        break;
                    case IMAGE:
                        renderImage((PdfImage) element.element, sb);
                        break;
                }
            }

            sb.append("</div>\n");
        }

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private void renderTextLine(TextLine tl, StringBuilder sb) {
        List<String> classes = new ArrayList<>();
        classes.add("textline");

        if (tl.getFont() != null) {
            if (tl.getFont().isBold()) {
                classes.add("bold");
            }
            if (tl.getFont().isItalic()) {
                classes.add("italic");
            }
        }

        sb.append("<div class=\"").append(String.join(" ", classes)).append("\">")
                .append(escapeHtml(tl.getText()))
                .append("</div>\n");
    }

    private void renderTable(Table table, StringBuilder sb) {
        sb.append("<div class=\"table-wrapper\">");
        sb.append("<table style=\"border-collapse:collapse;\">");

        if (table.getRows() != null) {
            List<List<TableCell>> rows = table.getRows();
            for (List<TableCell> row : rows) {
                sb.append("<tr>");
                if (row != null) {
                    for (TableCell cell : row) {
                        if (cell == null) continue;

                        int colspan = Math.max(1, cell.getColSpan());
                        int rowspan = Math.max(1, cell.getRowSpan());

                        String cellStyle = "border:1px solid #ccc;padding:4px;vertical-align:top;";
                        List<String> classes = new ArrayList<>();

                        if (!cell.getContentBlocks().isEmpty()) {
                            Object firstBlock = cell.getContentBlocks().getFirst();
                            if (firstBlock instanceof Word wordInCell) {
                                if (wordInCell.getFont() != null) {
                                    if (wordInCell.getFont().isBold()) {
                                        classes.add("bold");
                                    }
                                    if (wordInCell.getFont().isItalic()) {
                                        classes.add("italic");
                                    }
                                }
                            }
                        }

                        String content = escapeHtml(cell.getContent());
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
        }

        sb.append("</table>");
        sb.append("</div>\n");
    }

    private void renderImage(PdfImage image, StringBuilder sb) {
        if (image.getImageData() == null || image.getImageData().length == 0) return;

        String base64Data = java.util.Base64.getEncoder().encodeToString(image.getImageData());
        String mimeType = "image/" + (image.getImageFormat() != null ? image.getImageFormat().toLowerCase() : "png");

        // Получаем bounding box
        var bbox = image.getBoundingBox();

        // Если изображение слишком большое, масштабируем его
        double maxWidth = 800; // максимальная ширина в пикселях
        double maxHeight = 600; // максимальная высота в пикселях

        double width = bbox.getWidth();
        double height = bbox.getHeight();

        if (width > maxWidth || height > maxHeight) {
            double scale = Math.min(maxWidth / width, maxHeight / height);
            width = width * scale;
            height = height * scale;
        }

        sb.append("<div style=\"position:relative;display:inline-block;margin:10px;\">")
                .append("<img src=\"data:")
                .append(mimeType)
                .append(";base64,")
                .append(base64Data)
                .append("\" style=\"width:")
                .append(width)
                .append("px;height:auto;max-width:100%;border:1px solid #ddd;\"")
                .append(" alt=\"PDF Image\">")
                .append("<div style=\"font-size:10px;color:#666;text-align:center;\">")
                .append("Image (").append(Math.round(bbox.getWidth())).append("x").append(Math.round(bbox.getHeight())).append(" pt)")
                .append("</div>")
                .append("</div>\n");
    }

    private enum PageElementType {
        TEXT_LINE,
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

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}
