package ru.sunveil.precision_pdf.pdfparser.extensions;

import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.core.PdfEntity;

import java.util.List;

public abstract class PdfParserExtension {
    public abstract List<PdfEntity> extractExtendedEntities(PdfDocument document);
    public abstract String getExtensionName();
}
