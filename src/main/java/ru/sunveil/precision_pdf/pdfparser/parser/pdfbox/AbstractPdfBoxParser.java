package ru.sunveil.precision_pdf.pdfparser.parser.pdfbox;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import ru.sunveil.precision_pdf.pdfparser.exceptions.PdfParseException;
import ru.sunveil.precision_pdf.pdfparser.model.PdfMetadata;
import ru.sunveil.precision_pdf.pdfparser.parser.*;

import java.io.File;

public abstract class AbstractPdfBoxParser implements PdfParser, TextExtractor, TableExtractor, ImageExtractor {

    @Override
    public PdfMetadata extractMetadata(PDDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        PdfMetadata metadata = new PdfMetadata();

        try {
            PDDocumentInformation docInfo = document.getDocumentInformation();

            if (docInfo != null) {
                metadata.setTitle(docInfo.getTitle());
                metadata.setAuthor(docInfo.getAuthor());
                metadata.setSubject(docInfo.getSubject());
                metadata.setKeywords(docInfo.getKeywords());
                metadata.setCreator(docInfo.getCreator());
                metadata.setProducer(docInfo.getProducer());
                //metadata.setCreationDate(docInfo.getCreationDate().getTime());
                //metadata.setModificationDate(docInfo.getModificationDate().getTime());
            }
        } catch (Exception e) {
            throw new PdfParseException("Failed to extract metadata", e);
        }

        return metadata;
    }

    private boolean isStandardMetadataKey(String keyName) {
        return keyName.equals("Title") || keyName.equals("Author") ||
                keyName.equals("Subject") || keyName.equals("Keywords") ||
                keyName.equals("Creator") || keyName.equals("Producer") ||
                keyName.equals("CreationDate") || keyName.equals("ModDate");
    }

    protected void validateFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("Path is not a file: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("Cannot read file: " + file.getAbsolutePath());
        }
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File is not a PDF: " + file.getAbsolutePath());
        }
    }

    protected void closeDocument(PDDocument document) {
        if (document != null) {
            try {
                document.close();
            } catch (Exception e) {
                System.err.println("Warning: Failed to close PDF document: " + e.getMessage());
            }
        }
    }

}