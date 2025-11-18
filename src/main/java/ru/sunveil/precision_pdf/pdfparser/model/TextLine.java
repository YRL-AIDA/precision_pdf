package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TextLine extends TextEntity {
    private List<Word> words;
    private float lineHeight;
    private int order;
    private PDFFont font;
    private Color color;
    private float spaceWidth;

    public TextLine(){
        super();
        words = null;
        lineHeight = Float.MIN_VALUE;
        order = -1;
        font = null;
        color = null;
        spaceWidth = 0f;
    }

    public TextLine(int pageNumber, BoundingBox boundingBox, String text,
                    List<Word> words, float lineHeight, int order, PDFFont font, Color color, float spaceWidth) {
        super(pageNumber, boundingBox, text);
        this.words = words != null ? new ArrayList<>(words) : new ArrayList<>();
        this.lineHeight = lineHeight;
        this.order = order;
        this.font = font;
        this.color = color;
        this.spaceWidth = spaceWidth;
        if (lineHeight <= 0) {
            throw new IllegalArgumentException("Line height must be positive");
        }    }

    @Override
    public String getType() {
        return "TEXT_LINE";
    }
}