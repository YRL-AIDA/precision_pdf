package ru.sunveil.precision_pdf.pdfparser.export;

import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;

@Component
public class TextExporter implements Exporter {

    @Override
    public String export(PdfDocument document, ExportFormat format) {
        StringBuilder text = new StringBuilder();

        text.append("Title: ").append(document.getMetadata().getTitle()).append("\n")
                .append("Author: ").append(document.getMetadata().getAuthor()).append("\n")
                .append("Pages: ").append(document.getTotalPages()).append("\n")
                .append("=".repeat(50)).append("\n\n");

        for (PdfPage page : document.getPages()) {
            text.append("Page ").append(page.getPageNumber()).append("\n")
                    .append("-".repeat(30)).append("\n");

            for (TextLine line : page.getTextLines()) {
                for (Word word : line.getWords()) {
                    text.append(word.getText()).append(" ");
                }
                text.append("\n");
            }
            text.append("\n");
        }

        return text.toString();
    }

    @Override
    public boolean supportsFormat(ExportFormat format) {
        return format == ExportFormat.TEXT;
    }
}