package ru.sunveil.precision_pdf.pdfparser.export;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.io.IOException;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Экспортер JSON в формате, совместимом со старым precisionPDF для PageR.
 */
@Component
public class PagerJsonExporter implements Exporter {

    private final ObjectMapper objectMapper;

    public PagerJsonExporter() {
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        SimpleModule module = new SimpleModule();
        module.addSerializer(Color.class, new ColorSerializer());
        objectMapper.registerModule(module);
    }

    @Override
    public String export(PdfDocument document, ExportFormat format) {
        try {
            PageRFormat pageRDoc = convertToPageRFormat(document);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pageRDoc);
        } catch (Exception e) {
            throw new RuntimeException("Error exporting to PageR JSON format", e);
        }
    }

    @Override
    public boolean supportsFormat(ExportFormat format) {
        return format == ExportFormat.JSON;
    }

    private PageRFormat convertToPageRFormat(PdfDocument document) {
        PageRFormat result = new PageRFormat();
        result.setDocument(document.getFilename());
        
        for (PdfPage page : document.getPages()) {
            PageRPage pageRPage = convertPage(page);
            result.getPages().add(pageRPage);
        }
        
        return result;
    }

    private PageRPage convertPage(PdfPage page) {
        PageRPage pageRPage = new PageRPage();
        pageRPage.setNumber(page.getPageNumber());
        pageRPage.setWidth(page.getWidth());
        pageRPage.setHeight(page.getHeight());
        pageRPage.setTables(page.getTables());
        pageRPage.setImages(page.getImages());
        
        for (TextLine textLine : page.getTextLines()) {
            PageRRow row = convertRow(textLine);
            pageRPage.getRows().add(row);
        }
        
        return pageRPage;
    }

    private PageRRow convertRow(TextLine textLine) {
        PageRRow row = new PageRRow();
        
        BoundingBox bbox = textLine.getBoundingBox();
        PageRSegment segment = new PageRSegment();
        segment.setX_top_left(bbox.getX());
        segment.setY_top_left(bbox.getY());
        segment.setWidth(bbox.getWidth());
        segment.setHeight(bbox.getHeight());
        row.setSegment(segment);
        
        row.setText(textLine.getText());
        
        for (Word word : textLine.getWords()) {
            PageRWord pageRWord = convertWord(word);
            row.getWords().add(pageRWord);
        }
        
        return row;
    }

    private PageRWord convertWord(Word word) {
        PageRWord pageRWord = new PageRWord();
        pageRWord.setMetadata("unknown");
        pageRWord.setText(word.getText());
        pageRWord.setUrl("");
        
        BoundingBox bbox = word.getBoundingBox();
        PageRSegment segment = new PageRSegment();
        segment.setX_top_left(bbox.getX());
        segment.setY_top_left(bbox.getY());
        segment.setWidth(bbox.getWidth());
        segment.setHeight(bbox.getHeight());
        pageRWord.setSegment(segment);
        
        PageRFont font = new PageRFont();
        font.setFont_name(word.getFontName() != null ? word.getFontName() : "unknown");
        font.setFont_size(word.getFontSize());
        font.setIs_bold(word.getFont() != null && word.getFont().isBold());
        font.setIs_italic(word.getFont() != null && word.getFont().isItalic());
        font.setIs_normal(!font.getIs_bold() && !font.getIs_italic());
        pageRWord.setFont(font);
        
        return pageRWord;
    }

    @Setter
    @Getter
    private static class PageRFormat {
        private List<PageRPage> pages = new ArrayList<>();
        private String document;

    }

    @Setter
    @Getter
    private static class PageRPage {
        private int number;
        private double width;
        private double height;
        private List<Table> tables = new ArrayList<>();
        private List<PdfImage> images = new ArrayList<>();
        private List<PageRRow> rows = new ArrayList<>();

    }

    @Setter
    @Getter
    private static class PageRRow {
        private PageRSegment segment;
        private List<PageRWord> words = new ArrayList<>();
        private String text;

    }

    @Setter
    @Getter
    private static class PageRWord {
        private String metadata;
        private PageRSegment segment;
        private String text;
        private String url;
        private PageRFont font;

    }

    @Setter
    @Getter
    private static class PageRSegment {
        private float x_top_left;
        private float y_top_left;
        private float width;
        private float height;

    }

    private static class PageRFont {
        private boolean is_italic;
        private boolean is_normal;
        @Getter
        @Setter
        private float font_size;
        private boolean is_bold;
        @Getter
        @Setter
        private String font_name;

        public boolean getIs_italic() { return is_italic; }
        public void setIs_italic(boolean is_italic) { this.is_italic = is_italic; }
        public boolean getIs_normal() { return is_normal; }
        public void setIs_normal(boolean is_normal) { this.is_normal = is_normal; }

        public boolean getIs_bold() { return is_bold; }
        public void setIs_bold(boolean is_bold) { this.is_bold = is_bold; }
    }

    private static class ColorSerializer extends JsonSerializer<Color> {
        @Override
        public void serialize(Color color, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("red", color.getRed());
            gen.writeNumberField("green", color.getGreen());
            gen.writeNumberField("blue", color.getBlue());
            gen.writeNumberField("alpha", color.getAlpha());
            gen.writeEndObject();
        }
    }
}
