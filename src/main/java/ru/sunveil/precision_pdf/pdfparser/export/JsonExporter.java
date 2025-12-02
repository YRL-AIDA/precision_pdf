package ru.sunveil.precision_pdf.pdfparser.export;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.stereotype.Component;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;

import java.awt.*;
import java.io.IOException;

@Component
public class JsonExporter implements Exporter {

    private final ObjectMapper objectMapper;

    public JsonExporter() {
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        SimpleModule colorModule = new SimpleModule();
        colorModule.addSerializer(Color.class, new ColorSerializer());
        objectMapper.registerModule(colorModule);
    }

    @Override
    public String export(PdfDocument document, ExportFormat format) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
        } catch (Exception e) {
            throw new RuntimeException("Error exporting to JSON", e);
        }
    }

    @Override
    public boolean supportsFormat(ExportFormat format) {
        return format == ExportFormat.JSON;
    }

    /**
     * Кастомный сериализатор для java.awt.Color, который сериализует только RGB компоненты
     */
    private static class ColorSerializer extends StdSerializer<Color> {

        public ColorSerializer() {
            super(Color.class);
        }

        @Override
        public void serialize(Color color, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("red", color.getRed());
            gen.writeNumberField("green", color.getGreen());
            gen.writeNumberField("blue", color.getBlue());
            gen.writeNumberField("alpha", color.getAlpha());
            gen.writeEndObject();
        }
    }
}