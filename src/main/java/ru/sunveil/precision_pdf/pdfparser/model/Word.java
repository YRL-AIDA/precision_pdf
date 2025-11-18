package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.awt.*;

@Data
@EqualsAndHashCode(callSuper = true)
public class Word extends TextEntity {
    private float confidence;
    private String fontName;
    private float fontSize;
    private int order;
    private PDFFont font;
    private Color color;
    private float spaceWidth;

    public Word(){
        super();
        confidence = Float.MIN_VALUE;
        fontName = null;
        fontSize = Float.MIN_VALUE;
        order = -1;
        font = null;
        color = null;
        spaceWidth = 0f;
    }

    public Word(int pageNumber, BoundingBox boundingBox, String text,
                float confidence, String fontName, float fontSize, int order, PDFFont font, Color color, float spaceWidth) {
        super(pageNumber, boundingBox, text);
        this.confidence = confidence;
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.order = order;
        this.font = font;
        this.color = color;
        this.spaceWidth = spaceWidth;


        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Confidence must be between 0 and 1");
        }
        if (fontSize <= 0) {
            throw new IllegalArgumentException("Font size must be positive");
        }

    }

    @Override
    public String getType() {
        return "WORD";
    }
}