package ru.sunveil.precision_pdf.pdfparser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Data;

import java.util.List;

@Data
public class TableExtractionResult {
    private final List<Table> tables;
}
