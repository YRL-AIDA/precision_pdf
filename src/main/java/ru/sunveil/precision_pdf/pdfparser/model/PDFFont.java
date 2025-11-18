package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;

@Data
public class PDFFont {
    private String name;
    private float fontSize;
    private float height;
    private boolean bold;
    private boolean italic;

    public PDFFont() {
        this.name = "";
        this.fontSize = 0f;
        this.height = 0f;
        this.bold = false;
        this.italic = false;
    }

    public PDFFont(String name, float fontSize, float height, boolean bold, boolean italic) {
        this.name = name;
        this.fontSize = fontSize;
        this.height = height;
        this.bold = bold;
        this.italic = italic;
    }
}