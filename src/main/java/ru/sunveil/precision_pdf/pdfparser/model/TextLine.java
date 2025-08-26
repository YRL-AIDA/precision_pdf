package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.sunveil.precision_pdf.pdfparser.model.core.*;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TextLine extends TextEntity {
    private List<Word> words;
    private float lineHeight;

    public TextLine(){
        super();
        words = null;
        lineHeight = Float.MIN_VALUE;
    }

    public TextLine(int pageNumber, BoundingBox boundingBox, String text,
                    List<Word> words, float lineHeight) {
        super(pageNumber, boundingBox, text);
        this.words = words != null ? new ArrayList<>(words) : new ArrayList<>();
        this.lineHeight = lineHeight;
        if (lineHeight <= 0) {
            throw new IllegalArgumentException("Line height must be positive");
        }    }

    @Override
    public String getType() {
        return "TEXT_LINE";
    }
}