package ru.sunveil.precision_pdf.pdfparser.extensions;

import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.core.PdfEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtensionManager {
    private final Map<String, PdfParserExtension> extensions = new HashMap<>();

    public void registerExtension(PdfParserExtension extension) {
        extensions.put(extension.getExtensionName(), extension);
    }

    public void unregisterExtension(String extensionName) {
        extensions.remove(extensionName);
    }

    public List<PdfEntity> processExtensions(PdfDocument document) {
        List<PdfEntity> extendedEntities = new ArrayList<>();
        for (PdfParserExtension extension : extensions.values()) {
            extendedEntities.addAll(extension.extractExtendedEntities(document));
        }
        return extendedEntities;
    }
}