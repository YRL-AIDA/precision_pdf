package ru.sunveil.precision_pdf.pdfparser.export;

import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.TableCell;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.util.List;

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
        sb.append(".textline{position:absolute;white-space:pre-wrap;overflow:hidden;}\n");
        sb.append(".table-wrapper{position:absolute;overflow:auto;}\n");
        sb.append(".ruling{position:absolute;background:#000;}\n");
        sb.append("</style>\n</head>\n<body>\n");

        for (PdfPage page : document.getPages()) {
            sb.append("<div class=\"page\" style=\"width:")
                    .append(page.getWidth())
                    .append("pt;height:")
                    .append(page.getHeight())
                    .append("pt;\">\n");

            for (Table table : page.getTables()) {
                if (table == null || table.getBoundingBox() == null) continue;
                BoundingBox tb = table.getBoundingBox();
                double top = page.getHeight() - tb.getY() - tb.getHeight();
                sb.append("<div class=\"table-wrapper\" style=\"left:")
                        .append(tb.getX())
                        .append("pt;top:")
                        .append(top)
                        .append("pt;width:")
                        .append(tb.getWidth())
                        .append("pt;height:")
                        .append(tb.getHeight())
                        .append("pt;\">");
                sb.append("<table style=\"border-collapse:collapse;width:100%;height:100%;\">");
                if (table.getRows() != null) {
                    List<List<TableCell>> rows = table.getRows();
                    for (int ri = 0; ri < rows.size(); ri++) {
                        List<TableCell> row = rows.get(ri);
                        sb.append("<tr>");
                        if (row != null) {
                            for (TableCell cell : row) {
                                if (cell == null) continue;
                                int colspan = Math.max(1, cell.getColSpan());
                                int rowspan = Math.max(1, cell.getRowSpan());
                                String content = escapeHtml(cell.getContent());
                                sb.append("<td");
                                if (colspan > 1) sb.append(" colspan=\"").append(colspan).append("\"");
                                if (rowspan > 1) sb.append(" rowspan=\"").append(rowspan).append("\"");
                                sb.append(" style=\"border:1px solid #ccc;padding:4px;vertical-align:top;\">")
                                        .append(content)
                                        .append("</td>");
                            }
                        }
                        sb.append("</tr>");
                    }
                }
                sb.append("</table>");
                sb.append("</div>\n");
            }

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
                if (insideTable) continue;
                BoundingBox b = tl.getBoundingBox();
                double top = page.getHeight() - b.getY() - b.getHeight();
                sb.append("<div class=\"textline\" style=\"left:")
                        .append(b.getX())
                        .append("pt;top:")
                        .append(top)
                        .append("pt;width:")
                        .append(b.getWidth())
                        .append("pt;\">")
                        .append(escapeHtml(tl.getText()))
                        .append("</div>\n");
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
