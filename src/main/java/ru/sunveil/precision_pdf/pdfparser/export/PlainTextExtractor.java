package ru.sunveil.precision_pdf.pdfparser.export;

import ru.sunveil.precision_pdf.pdfparser.model.Header;
import ru.sunveil.precision_pdf.pdfparser.model.OtherBlock;
import ru.sunveil.precision_pdf.pdfparser.model.Paragraph;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Table;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;
import ru.sunveil.precision_pdf.pdfparser.model.Word;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;

import java.util.List;

public final class PlainTextExtractor {

    private PlainTextExtractor() {
    }

    public static String extract(PdfDocument document) {
        StringBuilder text = new StringBuilder();
        if (document.getPages() == null) {
            return "";
        }
        for (PdfPage page : document.getPages()) {
            boolean wrotePageText = false;
            if (page.getTextLines() != null && !page.getTextLines().isEmpty()) {
                for (TextLine line : page.getTextLines()) {
                    wrotePageText |= appendTextEntity(text, line);
                    text.append('\n');
                }
            } else {
                wrotePageText |= appendBlocks(text, page.getHeaders());
                wrotePageText |= appendBlocks(text, page.getParagraphs());
                wrotePageText |= appendBlocks(text, page.getOtherBlocks());
                if (page.getTables() != null) {
                    for (Table table : page.getTables()) {
                        if (table.getEmbeddedHtml() != null && !table.getEmbeddedHtml().isBlank()) {
                            appendLineText(text, table.getEmbeddedHtml());
                            wrotePageText = true;
                        }
                    }
                }
                if (!wrotePageText && page.getWords() != null) {
                    for (Word word : page.getWords()) {
                        appendToken(text, word.getText());
                    }
                    wrotePageText = !page.getWords().isEmpty();
                }
            }
            if (wrotePageText) {
                text.append('\n');
            }
        }
        return text.toString().trim();
    }

    private static boolean appendBlocks(StringBuilder text, List<? extends TextEntity> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        boolean wrote = false;
        for (TextEntity block : blocks) {
            wrote |= appendTextEntity(text, block);
            text.append('\n');
        }
        return wrote;
    }

    private static boolean appendTextEntity(StringBuilder text, TextEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getText() != null && !entity.getText().isBlank()) {
            appendLineText(text, entity.getText());
            return true;
        }
        if (entity instanceof TextLine line && line.getWords() != null) {
            boolean wrote = false;
            for (Word word : line.getWords()) {
                if (word.getText() != null && !word.getText().isBlank()) {
                    appendToken(text, word.getText());
                    wrote = true;
                }
            }
            return wrote;
        }
        if (entity instanceof Paragraph paragraph && paragraph.getLines() != null) {
            boolean wrote = false;
            for (TextLine line : paragraph.getLines()) {
                wrote |= appendTextEntity(text, line);
            }
            return wrote;
        }
        if (entity instanceof Header header && header.getLines() != null) {
            boolean wrote = false;
            for (TextLine line : header.getLines()) {
                wrote |= appendTextEntity(text, line);
            }
            return wrote;
        }
        if (entity instanceof OtherBlock otherBlock && otherBlock.getLines() != null) {
            boolean wrote = false;
            for (TextLine line : otherBlock.getLines()) {
                wrote |= appendTextEntity(text, line);
            }
            return wrote;
        }
        return false;
    }

    private static void appendLineText(StringBuilder text, String lineText) {
        if (lineText == null || lineText.isBlank()) {
            return;
        }
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append(' ');
        }
        text.append(lineText.trim());
    }

    private static void appendToken(StringBuilder text, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n' && text.charAt(text.length() - 1) != ' ') {
            text.append(' ');
        }
        text.append(token.trim());
    }
}
