package ru.sunveil.precision_pdf.pdfparser.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.exceptions.PdfParseException;
import ru.sunveil.precision_pdf.pdfparser.model.*;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;
import ru.sunveil.precision_pdf.pdfparser.model.core.TextEntity;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.AbstractPdfBoxParser;
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.ImageExtractionEngine;
import ru.sunveil.precision_pdf.pdfparser.table.TableType;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractRegionsJsonSegmentsParser extends AbstractPdfBoxParser {

    protected final ObjectMapper objectMapper = new ObjectMapper();
    private static final boolean DEBUG_DISABLE_TABLE_MERGES = true;
    private final ImageExtractionEngine imageExtractionEngine = new ImageExtractionEngine();

    @Override
    public PdfDocument parse(File pdfFile, ExtractionConfig config) throws IOException {
        validateFile(pdfFile);
        PDDocument document = null;
        try {
            document = Loader.loadPDF(pdfFile);

            PdfDocument pdfDocument = new PdfDocument(document);
            pdfDocument.setFilename(pdfFile.getName());
            pdfDocument.setTotalPages(document.getNumberOfPages());
            if (config != null && config.isExtractMetadata()) {
                pdfDocument.setMetadata(extractMetadata(document));
            }
            Path segmentationPath = produceSegmentationJson(pdfFile, config);
            pdfDocument.setPages(extractPages(document, segmentationPath, config));
            return pdfDocument;
        } catch (Exception e) {
            throw new PdfParseException(parseFailureMessage(), e);
        } finally {
            closeDocument(document);
        }
    }

    @Override
    public List<PdfTextChunk> extractTextChunks(PDDocument document) {
        return List.of();
    }

    @Override
    public List<TextLine> extractTextLines(PDDocument document) {
        return List.of();
    }

    @Override
    public List<Word> extractWords(PDDocument document) {
        return List.of();
    }

    @Override
    public TextExtractionResult extractTextAllTextEntities(PDDocument document) {
        return new TextExtractionResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    @Override
    public TableExtractionResult extractTables(PdfPage page) {
        return new TableExtractionResult(new ArrayList<>());
    }

    @Override
    public List<PdfImage> extractImages(PDDocument document) {
        try {
            return imageExtractionEngine.extractImages(document);
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public List<PdfImage> extractImagesFromPage(PDDocument document, int pageNumber) {
        try {
            return imageExtractionEngine.extractImagesFromPage(document, pageNumber);
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public boolean supportsImageExtraction() {
        return true;
    }

    /**
     * Path to JSON with root {@code pages} and PageR-compatible {@code regions} per page.
     */
    protected abstract Path produceSegmentationJson(File pdfFile, ExtractionConfig config)
            throws IOException, InterruptedException;

    protected abstract String parseFailureMessage();

    private List<PdfPage> extractPages(PDDocument document, Path segmentationJsonPath, ExtractionConfig config) throws IOException {
        List<PdfPage> pages = new ArrayList<>();
        JsonNode root = objectMapper.readTree(segmentationJsonPath.toFile());
        JsonNode pagesNode = root.path("pages");

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage pdPage = document.getPage(i);
            PdfPage page = createBasePage(pdPage, i + 1, i, document);
            if (pagesNode.isArray() && i < pagesNode.size()) {
                JsonNode jsonPage = pagesNode.get(i);
                fillSegments(page, jsonPage.path("regions"));
            }
            if (config != null && config.isExtractImages()) {
                List<PdfImage> pageImages = extractImagesFromPage(document, i + 1);
                page.setImages(pageImages);
            }
            pages.add(page);
        }

        return pages;
    }

    private PdfPage createBasePage(PDPage pdPage, int pageNumber, int index, PDDocument document) {
        PdfPage page = new PdfPage();
        page.setPageNumber(pageNumber);
        page.setIndex(index);
        page.setDocument(document);

        PDRectangle mediaBox = pdPage.getMediaBox();
        page.setWidth(mediaBox.getWidth());
        page.setHeight(mediaBox.getHeight());
        page.setBoundingBox(new BoundingBox(mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY(),
                mediaBox.getWidth(), mediaBox.getHeight()));
        return page;
    }

    private void fillSegments(PdfPage page, JsonNode regionsNode) {
        if (!regionsNode.isArray()) {
            return;
        }

        int globalOrder = 0;
        int globalWordOrder = 0;
        for (JsonNode region : regionsNode) {
            JsonNode segment = region.path("segment");
            BoundingBox bbox = toBoundingBox(segment, page.getHeight());
            if (bbox == null || !bbox.isValid()) {
                continue;
            }

            String label = region.path("label").asText("");
            label = label.isBlank() ? "unknown" : label.trim().toLowerCase();

            PdfSegment pdfSegment = new PdfSegment();
            pdfSegment.setPageNumber(page.getPageNumber());
            pdfSegment.setBoundingBox(bbox);
            pdfSegment.setLabel(label);
            page.getSegments().add(pdfSegment);

            int[] wordOrderRef = new int[]{globalWordOrder};
            List<TextLine> regionLines = buildTextLinesFromRegion(region, page, globalOrder, wordOrderRef);
            globalWordOrder = wordOrderRef[0];
            if (regionLines.isEmpty()) {
                continue;
            }
            globalOrder += regionLines.size();
            page.getTextLines().addAll(regionLines);
            if (isTableLabel(label)) {
                String htmlTable = region.path("html_table").asText("").trim();
                Table table = !htmlTable.isBlank()
                        ? buildTableFromEmbeddedHtml(page, bbox, regionLines, htmlTable)
                        : buildTableFromRegionWords(page, bbox, regionLines);
                if (table != null) {
                    page.addTable(table);
                }
            }
            addBlockEntity(page, label, bbox, regionLines);
        }

        page.getWords().sort(Comparator.comparingInt(Word::getOrder));
        page.getTextLines().sort(Comparator.comparingInt(TextLine::getOrder));
        page.getPdfTextChunks().sort(Comparator.comparingInt(PdfTextChunk::getOrder));
        page.getHeaders().sort(Comparator.comparingInt(Header::getOrder));
        page.getParagraphs().sort(Comparator.comparingInt(Paragraph::getOrder));
        page.getOtherBlocks().sort(Comparator.comparingInt(OtherBlock::getOrder));
    }

    private List<TextLine> buildTextLinesFromRegion(JsonNode regionNode, PdfPage page, int baseOrder, int[] wordOrderRef) {
        List<TextLine> textLines = new ArrayList<>();
        JsonNode rowsNode = regionNode.path("rows");
        if (!rowsNode.isArray()) {
            return textLines;
        }

        int lineIndex = 0;
        for (JsonNode rowNode : rowsNode) {
            BoundingBox rowBBox = toBoundingBox(rowNode.path("segment"), page.getHeight());
            if (rowBBox == null || !rowBBox.isValid()) {
                continue;
            }
            page.getJsonRows().add(rowBBox.copy());

            List<Word> lineWords = buildWordsFromRow(rowNode, page, wordOrderRef);
            String lineText = normalizeRowText(rowNode.path("text").asText(""), lineWords);
            if (lineText.isBlank()) {
                continue;
            }

            TextLine line = new TextLine(
                    page.getPageNumber(),
                    rowBBox,
                    lineText,
                    lineWords,
                    Math.max(rowBBox.getHeight(), 1f),
                    baseOrder + lineIndex,
                    lineWords.isEmpty() ? null : lineWords.get(0).getFont(),
                    Color.BLACK,
                    lineWords.isEmpty() ? 0f : lineWords.get(0).getSpaceWidth()
            );
            textLines.add(line);
            page.getWords().addAll(lineWords);
            lineIndex++;
        }

        if (!textLines.isEmpty()) {
            BoundingBox chunkBBox = textLines.get(0).getBoundingBox().copy();
            StringBuilder chunkText = new StringBuilder();
            for (TextLine line : textLines) {
                chunkBBox = chunkBBox.union(line.getBoundingBox());
                if (!chunkText.isEmpty()) {
                    chunkText.append("\n");
                }
                chunkText.append(line.getText());
            }
            PdfTextChunk chunk = new PdfTextChunk(page.getPageNumber(), chunkBBox, chunkText.toString(), textLines, null);
            chunk.setOrder(baseOrder);
            page.getPdfTextChunks().add(chunk);
        }

        return textLines;
    }

    private List<Word> buildWordsFromRow(JsonNode rowNode, PdfPage page, int[] wordOrderRef) {
        List<Word> words = new ArrayList<>();
        JsonNode wordsNode = rowNode.path("words");
        if (!wordsNode.isArray()) {
            return words;
        }
        String rowRawText = rowNode.path("text").asText("").trim();
        boolean preferRowTextForSingleWord = wordsNode.size() == 1
                && rowRawText.contains(" ")
                && !rowRawText.isBlank();
        for (JsonNode wordNode : wordsNode) {
            BoundingBox wordBBox = toBoundingBox(wordNode.path("segment"), page.getHeight());
            if (wordBBox == null || !wordBBox.isValid()) {
                continue;
            }

            String wordText = wordNode.path("text").asText("").trim();
            if (preferRowTextForSingleWord) {
                wordText = rowRawText;
            }
            if (wordText.isBlank()) {
                continue;
            }

            JsonNode fontNode = wordNode.path("font");
            String fontName = fontNode.path("name").asText(fontNode.path("fontname").asText("unknown"));
            float fontSize = (float) fontNode.path("size").asDouble(fontNode.path("fontsize").asDouble(1.0));
            boolean italic = fontNode.path("italic").asInt(0) == 1;
            PDFFont font = new PDFFont(fontName, fontSize, wordBBox.getHeight(), false, italic);

            Word word = new Word(
                    page.getPageNumber(),
                    wordBBox,
                    wordText,
                    1.0f,
                    fontName,
                    Math.max(fontSize, 0.01f),
                    wordOrderRef[0],
                    font,
                    Color.BLACK,
                    Math.max(wordBBox.getHeight() * 0.3f, 1f)
            );
            words.add(word);
            wordOrderRef[0]++;
        }
        return words;
    }

    private void addBlockEntity(PdfPage page, String label, BoundingBox bbox, List<TextLine> regionLines) {
        if (isTableLabel(label)) {
            return;
        }

        String text = String.join("\n", regionLines.stream().map(TextLine::getText).toList());
        int order = regionLines.get(0).getOrder();

        if ("header".equals(label)) {
            Header header = new Header(page.getPageNumber(), bbox, text, order, regionLines);
            page.getHeaders().add(header);
            page.addBlock(header);
            return;
        }

        if ("text".equals(label) || "paragraph".equals(label)) {
            Paragraph paragraph = new Paragraph(page.getPageNumber(), bbox, text, order, regionLines);
            page.getParagraphs().add(paragraph);
            page.addBlock(paragraph);
            return;
        }

        OtherBlock otherBlock = new OtherBlock(page.getPageNumber(), bbox, text, order, label, regionLines);
        page.getOtherBlocks().add(otherBlock);
        page.addBlock(otherBlock);
    }

    private boolean isTableLabel(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        String l = label.trim().toLowerCase(Locale.ROOT);
        return "table".equals(l) || "tabular".equals(l) || "table_region".equals(l);
    }

    private Table buildTableFromRegionWords(PdfPage page, BoundingBox tableBBox, List<TextLine> regionLines) {
        List<List<List<Word>>> rowCandidates = new ArrayList<>();
        List<Float> candidateCenters = new ArrayList<>();
        List<Float> candidateWidths = new ArrayList<>();
        List<Integer> candidateCountPerRow = new ArrayList<>();
        List<Float> sourceRowCenters = new ArrayList<>();

        for (TextLine line : regionLines) {
            List<Word> lineWords = new ArrayList<>();
            if (line.getWords() != null) {
                for (Word word : line.getWords()) {
                    if (word.getBoundingBox() != null && tableBBox.intersects(word.getBoundingBox())) {
                        lineWords.add(word);
                    }
                }
            }
            if (lineWords.isEmpty()) {
                continue;
            }

            List<List<Word>> candidates = splitWordsIntoCellCandidates(lineWords);
            rowCandidates.add(candidates);
            candidateCountPerRow.add(candidates.size());
            sourceRowCenters.add(line.getBoundingBox().getCenterY());
            for (List<Word> candidate : candidates) {
                BoundingBox bb = unionWords(candidate);
                candidateCenters.add(bb.getCenterX());
                candidateWidths.add(bb.getWidth());
            }
        }

        if (rowCandidates.isEmpty()) {
            return null;
        }
        float avgCandidateWidth = (float) candidateWidths.stream().mapToDouble(v -> v).average().orElse(20.0);
        float avgWordHeight = (float) regionLines.stream()
                .flatMap(line -> line.getWords() == null ? List.<Word>of().stream() : line.getWords().stream())
                .mapToDouble(w -> w.getBoundingBox().getHeight()).average().orElse(8.0);

        List<Float> columnCenters = clusterPositions(candidateCenters, Math.max(avgCandidateWidth * 0.7f, 18f), true);
        List<Float> rowCenters = new ArrayList<>();
        for (TextLine line : regionLines) {
            if (line.getBoundingBox() != null && tableBBox.intersects(line.getBoundingBox())) {
                rowCenters.add(line.getBoundingBox().getCenterY());
            }
        }
        rowCenters = clusterPositions(rowCenters, Math.max(avgWordHeight * 0.8f, 4f), false);
        if (columnCenters.isEmpty() || rowCenters.isEmpty()) {
            return null;
        }

        Map<String, List<Word>> cellWords = new HashMap<>();
        Map<Integer, BoundingBox> colBounds = new HashMap<>();
        Map<Integer, BoundingBox> rowBounds = new HashMap<>();

        for (int sourceRowIdx = 0; sourceRowIdx < rowCandidates.size(); sourceRowIdx++) {
            List<List<Word>> candidates = rowCandidates.get(sourceRowIdx);
            int rowIdx = nearestClusterIndex(rowCenters, sourceRowCenters.get(sourceRowIdx));
            for (List<Word> candidate : candidates) {
                BoundingBox cb = unionWords(candidate);
                int col = nearestClusterIndex(columnCenters, cb.getCenterX());
                String key = rowIdx + "_" + col;
                cellWords.computeIfAbsent(key, k -> new ArrayList<>()).addAll(candidate);
                colBounds.put(col, colBounds.containsKey(col) ? colBounds.get(col).union(cb) : cb.copy());
                rowBounds.put(rowIdx, rowBounds.containsKey(rowIdx) ? rowBounds.get(rowIdx).union(cb) : cb.copy());
            }
        }

        List<List<TableCell>> tableRows = new ArrayList<>();
        for (int row = 0; row < rowCenters.size(); row++) {
            List<TableCell> rowCells = new ArrayList<>();
            for (int col = 0; col < columnCenters.size(); col++) {
                String key = row + "_" + col;
                List<Word> wordsInCell = cellWords.getOrDefault(key, Collections.emptyList());
                BoundingBox cellBBox = estimateCellBoundingBox(tableBBox, rowBounds.get(row), colBounds.get(col));
                if (!wordsInCell.isEmpty()) {
                    wordsInCell.sort(Comparator.comparingDouble(w -> w.getBoundingBox().getX()));
                    cellBBox = wordsInCell.get(0).getBoundingBox().copy();
                    for (Word word : wordsInCell) {
                        cellBBox = cellBBox.union(word.getBoundingBox());
                    }
                }

                List<TextEntity> content = new ArrayList<>(wordsInCell);
                TableCell cell = new TableCell(cellBBox, 1, 1, content);
                cell.setPageNumber(page.getPageNumber());
                cell.setRow(row);
                cell.setColumn(col);
                rowCells.add(cell);
            }
            tableRows.add(rowCells);
        }
        redistributeCollapsedTopHeaderCells(tableRows);
        applyTopHeaderRowFusion(tableRows);
        int[] mips = findBestMips(tableRows);
        if (DEBUG_DISABLE_TABLE_MERGES) {
            inlineWrappedDescriptorCells(tableRows);
        } else {
            if (mips != null) {
                applyHeaderSpans(tableRows, mips[0], mips[1]);
                applyContinuationRowSpans(tableRows, mips[1]);
            } else {
                applyContinuationRowSpans(tableRows, Math.max(0, (tableRows.stream().mapToInt(List::size).max().orElse(1) / 2) - 1));
            }
            mergeSingleCellWrappedLines(tableRows);
            mergeWrappedDescriptorWithTrailingData(tableRows);
        }
        for (int r = 0; r < tableRows.size(); r++) {
            List<TableCell> row = tableRows.get(r);
            for (int c = 0; c < row.size(); c++) {
                TableCell cell = row.get(c);
                if (cell != null) {
                    cell.setRow(r);
                    cell.setColumn(c);
                }
            }
        }

        Table table = new Table(tableBBox.getX(), tableBBox.getTop(), tableBBox.getRight(), tableBBox.getY(), TableType.NOT_BORDERED);
        table.setPageNumber(page.getPageNumber());
        table.setRows(tableRows);
        table.setRowCount(tableRows.size());
        table.setColumnCount(columnCenters.size());
        table.setOrder(regionLines.get(0).getOrder());
        return table;
    }

    /**
     * DeepSeek-OCR (and similar) may supply a full {@code <table>} in {@code html_table};
     * skip grid clustering/MIPS when that HTML is present.
     */
    private Table buildTableFromEmbeddedHtml(
            PdfPage page, BoundingBox tableBBox, List<TextLine> regionLines, String htmlTable) {
        Table table = new Table(
                tableBBox.getX(), tableBBox.getTop(), tableBBox.getRight(), tableBBox.getY(), TableType.NOT_BORDERED);
        table.setPageNumber(page.getPageNumber());
        table.setEmbeddedHtml(htmlTable);
        table.setRows(new ArrayList<>());
        table.setRowCount(0);
        table.setColumnCount(0);
        table.setOrder(regionLines.get(0).getOrder());
        return table;
    }

    private int[] findBestMips(List<List<TableCell>> rows) {
        int rowCount = rows.size();
        if (rowCount < 2) return null;
        int colCount = rows.stream().mapToInt(List::size).max().orElse(0);
        if (colCount < 2) return null;

        int bestR2 = -1;
        int bestC2 = -1;
        int bestArea = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int r2 = 0; r2 < rowCount - 1; r2++) {
            for (int c2 = 0; c2 < colCount - 1; c2++) {
                if (!hasUniqueRowHeaders(rows, r2, c2)) continue;
                if (!hasUniqueColumnHeaders(rows, r2, c2, colCount)) continue;
                int area = (rowCount - (r2 + 1)) * (colCount - (c2 + 1));
                double rowCoverage = (double) (r2 + 1) / (double) rowCount;
                double colCoverage = (double) (c2 + 1) / (double) colCount;
                // Keep MIPS close to top-left quarter; large coverages overfit this table family.
                if (rowCoverage > 0.30 || colCoverage > 0.60) continue;
                double score = area - (rowCoverage * 100.0) - (colCoverage * 40.0);
                if (score > bestScore || (Math.abs(score - bestScore) < 0.0001 && area > bestArea)) {
                    bestScore = score;
                    bestArea = area;
                    bestR2 = r2;
                    bestC2 = c2;
                }
            }
        }
        return bestR2 >= 0 ? new int[]{bestR2, bestC2} : null;
    }

    private boolean hasUniqueRowHeaders(List<List<TableCell>> rows, int r2, int c2) {
        Map<String, Boolean> seen = new HashMap<>();
        for (int r = r2 + 1; r < rows.size(); r++) {
            String key = buildRowHeaderKey(rows.get(r), c2);
            if (key.isBlank()) return false;
            if (seen.containsKey(key)) return false;
            seen.put(key, true);
        }
        return !seen.isEmpty();
    }

    private boolean hasUniqueColumnHeaders(List<List<TableCell>> rows, int r2, int c2, int colCount) {
        Map<String, Boolean> seen = new HashMap<>();
        for (int c = c2 + 1; c < colCount; c++) {
            String key = buildColumnHeaderKey(rows, r2, c);
            if (key.isBlank()) return false;
            if (seen.containsKey(key)) return false;
            seen.put(key, true);
        }
        return !seen.isEmpty();
    }

    private String buildRowHeaderKey(List<TableCell> row, int c2) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c <= c2 && c < row.size(); c++) {
            TableCell cell = row.get(c);
            String text = normalizeText(cell == null ? "" : cell.getContent());
            if (!text.isBlank()) {
                if (!sb.isEmpty()) sb.append("|");
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private String buildColumnHeaderKey(List<List<TableCell>> rows, int r2, int c) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= r2 && r < rows.size(); r++) {
            List<TableCell> row = rows.get(r);
            if (c >= row.size()) continue;
            TableCell cell = row.get(c);
            String text = normalizeText(cell == null ? "" : cell.getContent());
            if (!text.isBlank()) {
                if (!sb.isEmpty()) sb.append("|");
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private void applyHeaderSpans(List<List<TableCell>> rows, int r2, int c2) {
        // Horizontal spans in column header region [0..r2, c2+1..end]
        for (int r = 0; r <= r2 && r < rows.size(); r++) {
            List<TableCell> row = rows.get(r);
            for (int c = c2 + 1; c < row.size(); c++) {
                TableCell cell = row.get(c);
                if (cell == null || cell.getInvisible() == 1) continue;
                if (cell.getContent() == null || cell.getContent().isBlank()) continue;

                int span = 1;
                int next = c + 1;
                while (next < row.size()) {
                    TableCell nextCell = row.get(next);
                    if (nextCell == null || nextCell.getInvisible() == 1) break;
                    if (nextCell.getContent() != null && !nextCell.getContent().isBlank()) break;
                    nextCell.setInvisible(1);
                    span++;
                    next++;
                }
                cell.setColSpan(Math.max(cell.getColSpan(), span));
                c = next - 1;
            }
        }

        // Vertical spans in row header region [r2+1..end, 0..c2]
        for (int c = 0; c <= c2; c++) {
            for (int r = r2 + 1; r < rows.size() - 1; r++) {
                List<TableCell> row = rows.get(r);
                if (c >= row.size()) continue;
                TableCell cell = row.get(c);
                if (cell == null || cell.getInvisible() == 1) continue;
                if (cell.getContent() == null || cell.getContent().isBlank()) continue;

                int span = 1;
                int next = r + 1;
                while (next < rows.size()) {
                    List<TableCell> nextRow = rows.get(next);
                    if (c >= nextRow.size()) break;
                    TableCell nextCell = nextRow.get(c);
                    if (nextCell == null || nextCell.getInvisible() == 1) break;
                    if (nextCell.getContent() != null && !nextCell.getContent().isBlank()) break;
                    nextCell.setInvisible(1);
                    span++;
                    next++;
                }
                cell.setRowSpan(Math.max(cell.getRowSpan(), span));
                r = next - 1;
            }
        }
    }

    private List<List<Word>> splitWordsIntoCellCandidates(List<Word> lineWords) {
        List<Word> sorted = new ArrayList<>(lineWords);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().getX()));
        if (sorted.isEmpty()) {
            return new ArrayList<>();
        }

        if (sorted.size() == 1) {
            List<String> markdownCells = parseMarkdownTableCells(sorted.get(0).getText());
            if (markdownCells != null && markdownCells.size() > 1) {
                return splitWordIntoCellCandidates(sorted.get(0), markdownCells);
            }
        }

        float avgWidth = (float) sorted.stream().mapToDouble(w -> w.getBoundingBox().getWidth()).average().orElse(10.0);
        float gapThreshold = Math.max(avgWidth * 0.9f, 12f);

        List<List<Word>> candidates = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        current.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            Word previous = sorted.get(i - 1);
            Word next = sorted.get(i);
            float gap = next.getBoundingBox().getX() - previous.getBoundingBox().getRight();
            if (gap > gapThreshold) {
                candidates.add(current);
                current = new ArrayList<>();
            }
            current.add(next);
        }
        candidates.add(current);
        return candidates;
    }

    private List<String> parseMarkdownTableCells(String lineText) {
        if (lineText == null) {
            return null;
        }
        String line = lineText.trim();
        if (!line.startsWith("|") || line.indexOf('|', 1) < 0) {
            return null;
        }
        String inner = line.replaceAll("^\\|+", "").replaceAll("\\|+$", "");
        String[] parts = inner.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        boolean separatorRow = true;
        for (String part : parts) {
            String cell = part.trim();
            cells.add(cell);
            if (!cell.isEmpty() && !cell.matches("[\\s\\-:]+")) {
                separatorRow = false;
            }
        }
        if (separatorRow || cells.size() < 2) {
            return null;
        }
        return cells;
    }

    private List<List<Word>> splitWordIntoCellCandidates(Word source, List<String> cellTexts) {
        BoundingBox row = source.getBoundingBox();
        if (row == null || cellTexts.isEmpty()) {
            return List.of();
        }
        float colWidth = row.getWidth() / cellTexts.size();
        List<List<Word>> candidates = new ArrayList<>();
        for (int i = 0; i < cellTexts.size(); i++) {
            float x0 = row.getX() + i * colWidth;
            BoundingBox cellBox = new BoundingBox(x0, row.getY(), colWidth, row.getHeight());
            Word cellWord = new Word(
                    source.getPageNumber(),
                    cellBox,
                    cellTexts.get(i),
                    source.getConfidence(),
                    source.getFontName(),
                    source.getFontSize(),
                    source.getOrder(),
                    source.getFont(),
                    source.getColor(),
                    source.getSpaceWidth()
            );
            candidates.add(List.of(cellWord));
        }
        return candidates;
    }

    private int redistributeCollapsedTopHeaderCells(List<List<TableCell>> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        List<TableCell> top = rows.get(0);
        if (top == null || top.size() < 3) return 0;

        int changed = 0;
        for (int c = 1; c < top.size() - 1; c++) {
            TableCell left = top.get(c - 1);
            TableCell center = top.get(c);
            TableCell right = top.get(c + 1);
            if (left == null || center == null || right == null) continue;

            String lt = normalizeCellText(left.getContent());
            String ct = normalizeCellText(center.getContent());
            String rt = normalizeCellText(right.getContent());
            if (ct.isBlank() || !lt.isBlank() || !rt.isBlank()) continue;

            String[] tokens = ct.split("\\s+");
            if (tokens.length < 3) continue;
            if (tokens[0].length() > 3 || tokens[1].length() > 3) continue;

            String tail = String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length));
            left.setContent(tokens[0]);
            left.setContentBlocks(Collections.emptyList());
            center.setContent(tokens[1]);
            center.setContentBlocks(Collections.emptyList());
            right.setContent(tail);
            right.setContentBlocks(Collections.emptyList());
            changed++;

        }
        return changed;
    }

    private String normalizeCellText(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    }

    private BoundingBox unionWords(List<Word> words) {
        BoundingBox box = words.get(0).getBoundingBox().copy();
        for (int i = 1; i < words.size(); i++) {
            box = box.union(words.get(i).getBoundingBox());
        }
        return box;
    }

    private BoundingBox estimateCellBoundingBox(BoundingBox tableBBox, BoundingBox rowBBox, BoundingBox colBBox) {
        float x = colBBox != null ? colBBox.getX() : tableBBox.getX();
        float y = rowBBox != null ? rowBBox.getY() : tableBBox.getY();
        float right = colBBox != null ? colBBox.getRight() : tableBBox.getRight();
        float top = rowBBox != null ? rowBBox.getTop() : tableBBox.getTop();
        return BoundingBox.fromCorners(x, y, right, top);
    }

    private List<Float> clusterPositions(List<Float> values, float tolerance, boolean ascending) {
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(Float::compare);

        List<Float> clusters = new ArrayList<>();
        float current = sorted.get(0);
        int count = 1;
        for (int i = 1; i < sorted.size(); i++) {
            float value = sorted.get(i);
            if (Math.abs(value - current) <= tolerance) {
                current = (current * count + value) / (count + 1);
                count++;
            } else {
                clusters.add(current);
                current = value;
                count = 1;
            }
        }
        clusters.add(current);

        if (!ascending) {
            clusters.sort(Comparator.reverseOrder());
        }
        return clusters;
    }

    private int nearestClusterIndex(List<Float> centers, float value) {
        int bestIdx = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < centers.size(); i++) {
            float dist = Math.abs(centers.get(i) - value);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private int countNonEmptyCells(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            for (TableCell cell : row) {
                if (cell != null && cell.getContent() != null && !cell.getContent().isBlank()) count++;
            }
        }
        return count;
    }

    private int countInvisibleCells(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            for (TableCell cell : row) {
                if (cell != null && cell.getInvisible() == 1) count++;
            }
        }
        return count;
    }

    private int countVisibleCells(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            for (TableCell cell : row) {
                if (cell != null && cell.getInvisible() != 1) count++;
            }
        }
        return count;
    }

    private int countNonEmptyVisibleCells(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            for (TableCell cell : row) {
                if (cell != null && cell.getInvisible() != 1 && cell.getContent() != null && !cell.getContent().isBlank()) count++;
            }
        }
        return count;
    }

    private int countRowsWithAnyLeadingContent(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            if (row == null || row.isEmpty()) continue;
            int leadingEnd = Math.max(1, row.size() / 2);
            boolean hasLeading = false;
            for (int c = 0; c < leadingEnd && c < row.size(); c++) {
                TableCell cell = row.get(c);
                if (cell != null && cell.getContent() != null && !cell.getContent().isBlank()) {
                    hasLeading = true;
                    break;
                }
            }
            if (hasLeading) count++;
        }
        return count;
    }

    private int countRowsWithEmptyLeadingAndFilledTrailing(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            if (row == null || row.isEmpty()) continue;
            int leadingEnd = Math.max(1, row.size() / 2);
            boolean hasLeading = false;
            boolean hasTrailing = false;
            for (int c = 0; c < row.size(); c++) {
                TableCell cell = row.get(c);
                boolean hasContent = cell != null && cell.getContent() != null && !cell.getContent().isBlank();
                if (c < leadingEnd) {
                    hasLeading = hasLeading || hasContent;
                } else {
                    hasTrailing = hasTrailing || hasContent;
                }
            }
            if (!hasLeading && hasTrailing) count++;
        }
        return count;
    }

    private void applyContinuationRowSpans(List<List<TableCell>> rows, int c2) {
        if (rows == null || rows.size() < 2) return;
        int colCount = rows.stream().mapToInt(List::size).max().orElse(0);
        if (colCount == 0) return;
        int leadingCols = Math.max(1, Math.min(c2 + 1, colCount / 2));
        int trailingStart = Math.min(leadingCols, colCount - 1);

        for (int r = 1; r < rows.size(); r++) {
            List<TableCell> current = rows.get(r);
            List<TableCell> previous = rows.get(r - 1);
            if (current == null || previous == null) continue;

            boolean hasTrailingContent = false;
            for (int c = trailingStart; c < current.size(); c++) {
                TableCell cell = current.get(c);
                if (cell != null && cell.getContent() != null && !cell.getContent().isBlank()) {
                    hasTrailingContent = true;
                    break;
                }
            }
            if (!hasTrailingContent) continue;

            for (int c = 0; c < leadingCols && c < current.size() && c < previous.size(); c++) {
                TableCell cur = current.get(c);
                TableCell prev = previous.get(c);
                if (cur == null || prev == null) continue;
                boolean curBlank = cur.getContent() == null || cur.getContent().isBlank();
                boolean prevFilled = prev.getContent() != null && !prev.getContent().isBlank();
                if (!curBlank || !prevFilled) continue;

                cur.setInvisible(1);
                prev.setRowSpan(Math.max(1, prev.getRowSpan()) + 1);
                if (prev.getBoundingBox() != null && cur.getBoundingBox() != null) {
                    prev.setBoundingBox(prev.getBoundingBox().union(cur.getBoundingBox()));
                }
            }
        }
    }

    private boolean applyTopHeaderRowFusion(List<List<TableCell>> rows) {
        if (rows == null || rows.size() < 2) return false;
        if (!shouldFuseTopHeaderRows(rows)) return false;
        List<TableCell> first = rows.get(0);
        List<TableCell> second = rows.get(1);
        int cols = Math.min(first.size(), second.size());
        for (int c = 0; c < cols; c++) {
            TableCell top = first.get(c);
            TableCell below = second.get(c);
            if (top == null || below == null) continue;
            String topText = top.getContent() == null ? "" : top.getContent().trim();
            String belowText = below.getContent() == null ? "" : below.getContent().trim();
            if (topText.isBlank() && belowText.isBlank()) continue;

            String merged;
            if (topText.isBlank()) {
                merged = belowText;
            } else if (belowText.isBlank()) {
                merged = topText;
            } else {
                merged = topText + " " + belowText;
            }
            top.setContent(merged.trim());
            List<TextEntity> mergedBlocks = new ArrayList<>();
            if (top.getContentBlocks() != null) mergedBlocks.addAll(top.getContentBlocks());
            if (below.getContentBlocks() != null) mergedBlocks.addAll(below.getContentBlocks());
            if (!mergedBlocks.isEmpty()) {
                mergedBlocks.sort(Comparator
                        .comparingDouble((TextEntity b) -> -b.getBoundingBox().getCenterY())
                        .thenComparingDouble(b -> b.getBoundingBox().getX()));
                top.setContentBlocks(mergedBlocks);
            }
            if (top.getBoundingBox() != null && below.getBoundingBox() != null) {
                top.setBoundingBox(top.getBoundingBox().union(below.getBoundingBox()));
            }
            // Avoid synthetic rowspan=2 here: second header row is hidden during compaction.
            top.setRowSpan(Math.max(1, top.getRowSpan()));
            below.setInvisible(1);
        }
        return true;
    }

    private boolean shouldFuseTopHeaderRows(List<List<TableCell>> rows) {
        List<TableCell> first = rows.get(0);
        List<TableCell> second = rows.get(1);
        int cols = Math.min(first.size(), second.size());
        if (cols == 0) return false;

        int secondNonEmpty = 0;
        int dataLike = 0;
        for (int c = 0; c < cols; c++) {
            TableCell cell = second.get(c);
            if (cell == null || cell.getContent() == null || cell.getContent().isBlank()) continue;
            secondNonEmpty++;
            String text = cell.getContent().trim();
            if (text.matches(".*\\d.*") || text.contains("[") || text.contains("+")) {
                dataLike++;
            }
        }
        // Header continuation is typically dense and mostly non-data.
        return secondNonEmpty >= Math.max(3, cols / 2) && dataLike <= 1;
    }

    private List<Map<String, Object>> previewTopHeaderCells(List<List<TableCell>> rows) {
        List<Map<String, Object>> preview = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return preview;
        List<TableCell> first = rows.get(0);
        int limit = Math.min(4, first.size());
        for (int c = 0; c < limit; c++) {
            TableCell cell = first.get(c);
            if (cell == null) continue;
            preview.add(Map.of(
                    "col", c,
                    "content", cell.getContent() == null ? "" : cell.getContent(),
                    "contentBlocks", cell.getContentBlocks() == null ? 0 : cell.getContentBlocks().size(),
                    "rowSpan", cell.getRowSpan()
            ));
        }
        return preview;
    }

    private int countCellsWithRowSpanGreaterThanOne(List<List<TableCell>> rows, int rowIndex) {
        if (rows == null || rowIndex < 0 || rowIndex >= rows.size()) return 0;
        int count = 0;
        List<TableCell> row = rows.get(rowIndex);
        for (TableCell cell : row) {
            if (cell != null && cell.getRowSpan() > 1) count++;
        }
        return count;
    }

    private int countCellsWithRowSpanGreaterThanOneInTable(List<List<TableCell>> rows) {
        int count = 0;
        for (List<TableCell> row : rows) {
            if (row == null) continue;
            for (TableCell cell : row) {
                if (cell != null && cell.getRowSpan() > 1) count++;
            }
        }
        return count;
    }

    private List<Double> sampleLineWidthRatios(List<TextLine> lines, BoundingBox tableBBox, int limit) {
        List<Double> ratios = new ArrayList<>();
        for (int i = 0; i < lines.size() && ratios.size() < limit; i++) {
            TextLine line = lines.get(i);
            if (line == null || line.getBoundingBox() == null) continue;
            ratios.add(tableBBox.getWidth() <= 0 ? 0.0 : (double) line.getBoundingBox().getWidth() / (double) tableBBox.getWidth());
        }
        return ratios;
    }

    private List<Double> sampleLineWidthRatiosFromEnd(List<TextLine> lines, BoundingBox tableBBox, int limit) {
        List<Double> ratios = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && ratios.size() < limit; i--) {
            TextLine line = lines.get(i);
            if (line == null || line.getBoundingBox() == null) continue;
            ratios.add(tableBBox.getWidth() <= 0 ? 0.0 : (double) line.getBoundingBox().getWidth() / (double) tableBBox.getWidth());
        }
        return ratios;
    }

    private List<String> sampleLineTextsFromEnd(List<TextLine> lines, int limit) {
        List<String> texts = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && texts.size() < limit; i--) {
            TextLine line = lines.get(i);
            if (line == null || line.getText() == null) continue;
            String t = line.getText().replaceAll("\\s+", " ").trim();
            if (t.length() > 120) t = t.substring(0, 120) + "...";
            texts.add(t);
        }
        return texts;
    }

    private String normalizeRowText(String rawRowText, List<Word> lineWords) {
        String raw = rawRowText == null ? "" : rawRowText.trim();
        if (lineWords == null || lineWords.isEmpty()) {
            return raw;
        }
        List<Word> sorted = new ArrayList<>(lineWords);
        sorted.sort(Comparator.comparingDouble(w -> w.getBoundingBox().getX()));
        String reconstructed = String.join(" ", sorted.stream().map(Word::getText).toList()).trim();
        if (raw.isBlank()) return reconstructed;
        if (lineWords.size() <= 1) return raw;

        int rawSpaces = (int) raw.chars().filter(ch -> ch == ' ').count();
        int expectedMinSpaces = Math.max(0, lineWords.size() - 1);
        String rawNoSpace = raw.replaceAll("\\s+", "");
        String recNoSpace = reconstructed.replaceAll("\\s+", "");
        if (rawSpaces < expectedMinSpaces && rawNoSpace.equals(recNoSpace)) {
            return reconstructed;
        }
        return raw;
    }

    private List<Integer> tailRowFillCounts(List<List<TableCell>> rows, int tailSize) {
        List<Integer> counts = new ArrayList<>();
        int start = Math.max(0, rows.size() - tailSize);
        for (int r = start; r < rows.size(); r++) {
            List<TableCell> row = rows.get(r);
            int count = 0;
            for (TableCell cell : row) {
                if (cell != null && cell.getContent() != null && !cell.getContent().isBlank()) count++;
            }
            counts.add(count);
        }
        return counts;
    }

    private int inlineWrappedDescriptorCells(List<List<TableCell>> rows) {
        if (rows == null || rows.size() < 2) return 0;
        int moved = 0;
        int descriptorLimit = Math.max(1, rows.stream().mapToInt(List::size).max().orElse(0) / 2);
        for (int r = 1; r < rows.size(); r++) {
            List<TableCell> current = rows.get(r);
            List<TableCell> previous = rows.get(r - 1);
            if (current == null || previous == null) continue;
            for (int c = 1; c < descriptorLimit && c < current.size() && c < previous.size(); c++) {
                TableCell cur = current.get(c);
                TableCell prev = previous.get(c);
                if (cur == null || prev == null) continue;
                String curText = cur.getContent() == null ? "" : cur.getContent().trim();
                String prevText = prev.getContent() == null ? "" : prev.getContent().trim();
                if (curText.isBlank() || prevText.isBlank()) continue;
                if (!looksLikeWrappedContinuation(curText)) continue;

                prev.setContent((prevText + " " + curText).replaceAll("\\s+", " ").trim());
                List<TextEntity> mergedBlocks = new ArrayList<>();
                if (prev.getContentBlocks() != null) mergedBlocks.addAll(prev.getContentBlocks());
                if (cur.getContentBlocks() != null) mergedBlocks.addAll(cur.getContentBlocks());
                if (!mergedBlocks.isEmpty()) {
                    mergedBlocks.sort(Comparator
                            .comparingDouble((TextEntity b) -> -b.getBoundingBox().getCenterY())
                            .thenComparingDouble(b -> b.getBoundingBox().getX()));
                    prev.setContentBlocks(mergedBlocks);
                }
                if (prev.getBoundingBox() != null && cur.getBoundingBox() != null) {
                    prev.setBoundingBox(prev.getBoundingBox().union(cur.getBoundingBox()));
                }
                cur.setContent("");
                cur.setContentBlocks(new ArrayList<>());
                moved++;
            }
        }
        return moved;
    }

    private boolean looksLikeWrappedContinuation(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isBlank()) return false;
        if (t.length() > 80) return false;
        char first = t.charAt(0);
        return t.startsWith("(")
                || Character.isLowerCase(first)
                || Character.isDigit(first)
                || t.startsWith("TEQ")
                || t.startsWith("in ");
    }

    private int mergeSingleCellWrappedLines(List<List<TableCell>> rows) {
        if (rows == null || rows.size() < 2) return 0;
        int merged = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<TableCell> current = rows.get(r);
            List<TableCell> previous = rows.get(r - 1);
            if (current == null || previous == null) continue;

            int nonEmptyIdx = -1;
            int nonEmptyCount = 0;
            for (int c = 0; c < current.size(); c++) {
                TableCell cell = current.get(c);
                if (cell == null || cell.getInvisible() == 1) continue;
                if (cell.getContent() != null && !cell.getContent().isBlank()) {
                    nonEmptyCount++;
                    nonEmptyIdx = c;
                }
            }
            if (nonEmptyCount != 1 || nonEmptyIdx < 0) continue;
            if (nonEmptyIdx >= previous.size()) continue;
            if (nonEmptyIdx > Math.max(0, current.size() / 2)) continue; // focus on descriptor columns

            TableCell cur = current.get(nonEmptyIdx);
            TableCell prev = previous.get(nonEmptyIdx);
            if (prev == null || prev.getInvisible() == 1) continue;
            if (prev.getContent() == null || prev.getContent().isBlank()) continue;

            // Avoid merging if current row has any content in value columns.
            boolean hasTrailingData = false;
            for (int c = Math.max(nonEmptyIdx + 1, current.size() / 2); c < current.size(); c++) {
                TableCell tail = current.get(c);
                if (tail != null && tail.getInvisible() != 1 && tail.getContent() != null && !tail.getContent().isBlank()) {
                    hasTrailingData = true;
                    break;
                }
            }
            if (hasTrailingData) continue;

            String mergedText = (prev.getContent() + " " + cur.getContent()).replaceAll("\\s+", " ").trim();
            prev.setContent(mergedText);
            List<TextEntity> mergedBlocks = new ArrayList<>();
            if (prev.getContentBlocks() != null) mergedBlocks.addAll(prev.getContentBlocks());
            if (cur.getContentBlocks() != null) mergedBlocks.addAll(cur.getContentBlocks());
            if (!mergedBlocks.isEmpty()) {
                mergedBlocks.sort(Comparator
                        .comparingDouble((TextEntity b) -> -b.getBoundingBox().getCenterY())
                        .thenComparingDouble(b -> b.getBoundingBox().getX()));
                prev.setContentBlocks(mergedBlocks);
            }
            if (prev.getBoundingBox() != null && cur.getBoundingBox() != null) {
                prev.setBoundingBox(prev.getBoundingBox().union(cur.getBoundingBox()));
            }
            cur.setInvisible(1);
            merged++;
        }
        return merged;
    }

    private int mergeWrappedDescriptorWithTrailingData(List<List<TableCell>> rows) {
        if (rows == null || rows.size() < 2) return 0;
        int merged = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<TableCell> current = rows.get(r);
            List<TableCell> previous = rows.get(r - 1);
            if (current == null || previous == null) continue;

            int descriptorLimit = Math.max(1, current.size() / 2);
            boolean hasTrailingData = false;
            for (int c = descriptorLimit; c < current.size(); c++) {
                TableCell tail = current.get(c);
                if (tail != null && tail.getInvisible() != 1 && tail.getContent() != null && !tail.getContent().isBlank()) {
                    hasTrailingData = true;
                    break;
                }
            }
            if (!hasTrailingData) continue;

            for (int descriptorIdx = 1; descriptorIdx < descriptorLimit && descriptorIdx < current.size() && descriptorIdx < previous.size(); descriptorIdx++) {
                TableCell cur = current.get(descriptorIdx);
                TableCell prev = previous.get(descriptorIdx);
                if (cur == null || prev == null || cur.getInvisible() == 1 || prev.getInvisible() == 1) continue;
                if (cur.getContent() == null || cur.getContent().isBlank()) continue;
                if (prev.getContent() == null || prev.getContent().isBlank()) continue;

                boolean leftOfDescriptorEmpty = true;
                for (int c = 0; c < descriptorIdx && c < current.size(); c++) {
                    TableCell cell = current.get(c);
                    if (cell != null && cell.getInvisible() != 1 && cell.getContent() != null && !cell.getContent().isBlank()) {
                        leftOfDescriptorEmpty = false;
                        break;
                    }
                }
                if (!leftOfDescriptorEmpty) continue;

                String curText = cur.getContent().trim();
                String prevText = prev.getContent().trim();
                if (curText.length() > 60) continue;
                if (!(curText.startsWith("(") || Character.isLowerCase(curText.charAt(0)) || Character.isDigit(curText.charAt(0)) || curText.startsWith("TEQ"))) {
                    continue;
                }

                prev.setContent((prevText + " " + curText).replaceAll("\\s+", " ").trim());
                List<TextEntity> mergedBlocks = new ArrayList<>();
                if (prev.getContentBlocks() != null) mergedBlocks.addAll(prev.getContentBlocks());
                if (cur.getContentBlocks() != null) mergedBlocks.addAll(cur.getContentBlocks());
                if (!mergedBlocks.isEmpty()) {
                    mergedBlocks.sort(Comparator
                            .comparingDouble((TextEntity b) -> -b.getBoundingBox().getCenterY())
                            .thenComparingDouble(b -> b.getBoundingBox().getX()));
                    prev.setContentBlocks(mergedBlocks);
                }
                if (prev.getBoundingBox() != null && cur.getBoundingBox() != null) {
                    prev.setBoundingBox(prev.getBoundingBox().union(cur.getBoundingBox()));
                }
                cur.setInvisible(1);
                merged++;
            }
        }
        return merged;
    }

    private BoundingBox toBoundingBox(JsonNode segmentNode, double pageHeight) {
        if (segmentNode.isMissingNode() || !segmentNode.isObject()) {
            return null;
        }

        float xTopLeft = (float) segmentNode.path("x_top_left").asDouble(Float.NaN);
        float yTopLeft = (float) segmentNode.path("y_top_left").asDouble(Float.NaN);
        if (Float.isNaN(xTopLeft) || Float.isNaN(yTopLeft)) {
            return null;
        }

        float xBottomRight = (float) segmentNode.path("x_bottom_right").asDouble(Float.NaN);
        float yBottomRight = (float) segmentNode.path("y_bottom_right").asDouble(Float.NaN);

        float width;
        float height;
        if (!Float.isNaN(xBottomRight) && !Float.isNaN(yBottomRight)) {
            width = xBottomRight - xTopLeft;
            height = yBottomRight - yTopLeft;
        } else {
            width = (float) segmentNode.path("width").asDouble(Float.NaN);
            height = (float) segmentNode.path("height").asDouble(Float.NaN);
            if (Float.isNaN(width) || Float.isNaN(height)) {
                return null;
            }
            yBottomRight = yTopLeft + height;
        }

        if (width <= 0 || height <= 0) {
            return null;
        }

        float pdfY = (float) pageHeight - yBottomRight;
        return new BoundingBox(xTopLeft, pdfY, width, height);
    }
}
