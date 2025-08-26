package ru.sunveil.precision_pdf.pdfparser.model;

import lombok.Data;
import java.util.Date;
import java.util.Map;

@Data
public class PdfMetadata {
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private String creator;
    private String producer;
    private Date creationDate;
    private Date modificationDate;
    private Map<String, String> customMetadata;
}