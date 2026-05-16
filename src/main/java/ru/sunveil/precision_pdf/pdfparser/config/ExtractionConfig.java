package ru.sunveil.precision_pdf.pdfparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "pdf.extraction")
public class ExtractionConfig {
    private String parser = "simple";
    private boolean extractText = true;
    private boolean extractImages = true;
    private boolean extractTables = true;
    private boolean extractMetadata = true;
    private boolean preserveLayout = true;
    private float imageDpi = 150;
    private int maxImageSize = 2048;
    private String outputFormat = "JSON";
    private boolean includeBoundingBoxes = true;
    private boolean includeFontInfo = true;
    private boolean includeConfidenceScores = false;

    /**
     * ODL only: when {@code true}, tables are built with placeholder inflation + occupancy grid.
     * When {@code false} (default), each JSON {@code rows[]} element becomes one HTML {@code tr} with cells in JSON order
     * and spans taken from ODL as-is.
     */
    private boolean odlHeuristicTableModel = false;

    /**
     * ODL / OpenDataloader only: how {@code opendataloader_pdf.convert} is run for tables (and whole JSON).
     * <ul>
     *   <li>{@code heuristic} — без {@code hybrid} (нативный/геометрический путь, быстрее).</li>
     *   <li>{@code docling-fast} — с {@code hybrid="docling-fast"} (Docling, лучше на сложных/безлинейных таблицах).</li>
     *   <li>{@code merge-tables} — два прогона: JSON строится от эвристики; узлы {@code table} заменяются на вариант из Docling,
     *       если нативная таблица «слабая» или документ похож на скан с малым текстом.</li>
     * </ul>
     * Алиасы: {@code merge}, {@code both} → merge-tables; {@code docling}, {@code hybrid} → docling-fast.
     */
    private String odlConversionMode = "heuristic";

    private String deepseekOcrMode = "auto";

    /** Render DPI for page images passed to DeepSeek-OCR. */
    private int deepseekOcrRenderDpi = 144;
}
