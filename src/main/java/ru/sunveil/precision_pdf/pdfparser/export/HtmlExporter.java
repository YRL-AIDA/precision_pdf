package ru.sunveil.precision_pdf.pdfparser.export;

import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

@Component
public class HtmlExporter implements Exporter {

    @Override
    public String export(PdfDocument document, ExportFormat format) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n");
        sb.append("<style>\n");
        sb.append("body{margin:0;padding:0;}\n");
        sb.append(".page{position:relative;margin:1rem auto; border:1px solid #ccc;}\n");
        sb.append(".word{position:absolute;white-space:pre;}\n");
        sb.append(".ruling{position:absolute;background:#000;}\n");
        sb.append("</style>\n</head>\n<body>\n");

        for (PdfPage page : document.getPages()) {
            sb.append("<div class=\"page\" style=\"width:")
                    .append(page.getWidth())
                    .append("pt;height:")
                    .append(page.getHeight())
                    .append("pt;\">\n");

            for (Ruling r : page.getVisibleRulings()) {
                if (r.isVertical()) {
                    double top = page.getHeight() - r.getBottom();
                    double height = r.getBottom() - r.getTop();
                    sb.append("<div class=\"ruling\" style=\"left:")
                            .append(r.getLeft())
                            .append("pt;top:")
                            .append(top)
                            .append("pt;width:1pt;height:")
                            .append(height)
                            .append("pt;\"></div>\n");
                } else {
                    double top = page.getHeight() - r.getTop();
                    double width = r.getRight() - r.getLeft();
                    sb.append("<div class=\"ruling\" style=\"left:")
                            .append(r.getLeft())
                            .append("pt;top:")
                            .append(top)
                            .append("pt;width:")
                            .append(width)
                            .append("pt;height:1pt;\"></div>\n");
                }
            }

            for (Word w : page.getWords()) {
                BoundingBox b = w.getBoundingBox();
                if (b == null) continue;
                double top = page.getHeight() - b.getY() - b.getHeight();
                sb.append("<span class=\"word\" style=\"left:")
                        .append(b.getX())
                        .append("pt;top:")
                        .append(top)
                        .append("pt;\">")
                        .append(escapeHtml(w.getText()))
                        .append("</span>\n");
            }

            sb.append("</div>\n");
        }

        sb.append("</body>\n</html>");
        return sb.toString();
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
