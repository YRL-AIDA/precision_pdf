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
import ru.sunveil.precision_pdf.pdfparser.parser.pdfbox.AbstractPdfBoxParser;
import ru.sunveil.precision_pdf.pdfparser.table.TableType;
import ru.sunveil.precision_pdf.pdfparser.util.BlockTextLayoutBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

public class OdlParser extends AbstractPdfBoxParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

            List<PdfPage> pages = createBasePages(document);
            Path odlJsonPath = runOdlPythonAndGetJson(pdfFile, config);
            JsonNode root = OBJECT_MAPPER.readTree(odlJsonPath.toFile());
            JsonNode kids = root.path("kids");

            if (kids.isArray()) {
                for (JsonNode node : kids) {
                    collectSegments(node, pages);
                }
                boolean odlHeuristicTableModel = config != null && config.isOdlHeuristicTableModel();
                fillSemanticBlocksFromOdl(root, pages, odlHeuristicTableModel);
            }

            pdfDocument.setPages(pages);
            return pdfDocument;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfParseException("ODL parser process was interrupted", e);
        } catch (Exception e) {
            throw new PdfParseException("Failed to parse PDF with ODL parser: " + e.getMessage(), e);
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
        return List.of();
    }

    @Override
    public List<PdfImage> extractImagesFromPage(PDDocument document, int pageNumber) {
        return List.of();
    }

    @Override
    public boolean supportsImageExtraction() {
        return false;
    }

    private List<PdfPage> createBasePages(PDDocument document) {
        List<PdfPage> pages = new ArrayList<>();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            PDPage pdPage = document.getPage(i);
            PDRectangle mediaBox = pdPage.getMediaBox();

            PdfPage page = new PdfPage();
            page.setPageNumber(i + 1);
            page.setIndex(i);
            page.setDocument(document);
            page.setWidth(mediaBox.getWidth());
            page.setHeight(mediaBox.getHeight());
            page.setBoundingBox(
                    new BoundingBox(
                            mediaBox.getLowerLeftX(),
                            mediaBox.getLowerLeftY(),
                            mediaBox.getWidth(),
                            mediaBox.getHeight()
                    )
            );
            pages.add(page);
        }
        return pages;
    }

    private Path runOdlPythonAndGetJson(File pdfFile, ExtractionConfig config) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("odl_parser_output_");
        Path scriptPath = Path.of("odl_parser", "main.py").toAbsolutePath();
        Path dotVenvUnixPython = Path.of("odl_parser", ".venv", "bin", "python").toAbsolutePath();
        Path dotVenvWindowsPython = Path.of("odl_parser", ".venv", "Scripts", "python.exe").toAbsolutePath();
        Path venvUnixPython = Path.of("odl_parser", "venv", "bin", "python").toAbsolutePath();
        Path venvWindowsPython = Path.of("odl_parser", "venv", "Scripts", "python.exe").toAbsolutePath();
        String pythonExecutable;
        if (Files.exists(dotVenvUnixPython)) {
            pythonExecutable = dotVenvUnixPython.toString();
        } else if (Files.exists(dotVenvWindowsPython)) {
            pythonExecutable = dotVenvWindowsPython.toString();
        } else if (Files.exists(venvUnixPython)) {
            pythonExecutable = venvUnixPython.toString();
        } else if (Files.exists(venvWindowsPython)) {
            pythonExecutable = venvWindowsPython.toString();
        } else {
            pythonExecutable = "python";
        }

        String odlMode = normalizeOdlConversionMode(config);

        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable,
                scriptPath.toString(),
                "--pdf",
                pdfFile.getAbsolutePath(),
                "--output-dir",
                outputDir.toAbsolutePath().toString(),
                "--odl-mode",
                odlMode
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String outputText = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ODL python parser failed with code " + exitCode + ". Output: " + outputText);
        }

        String fileName = pdfFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        Path jsonPath = outputDir.resolve(stem + ".json");
        if (!Files.exists(jsonPath)) {
            throw new IOException("ODL JSON output not found: " + jsonPath + ". Parser output: " + outputText);
        }
        return jsonPath;
    }

    /**
     * Maps {@link ExtractionConfig#getOdlConversionMode()} to CLI values for {@code odl_parser/main.py}.
     */
    private static String normalizeOdlConversionMode(ExtractionConfig config) {
        if (config == null || config.getOdlConversionMode() == null || config.getOdlConversionMode().isBlank()) {
            return "heuristic";
        }
        String m = config.getOdlConversionMode().trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("merge".equals(m) || "both".equals(m) || "merge-tables".equals(m) || "mergetables".equals(m)) {
            return "merge-tables";
        }
        if ("docling".equals(m) || "docling-fast".equals(m) || "hybrid".equals(m)) {
            return "docling-fast";
        }
        return "heuristic";
    }

    private void collectSegments(JsonNode node, List<PdfPage> pages) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        JsonNode bboxNode = node.path("bounding box");
        int pageNumber = node.path("page number").asInt(0);
        BoundingBox bbox = parseOdlBoundingBox(bboxNode);
        if (bbox != null && bbox.isValid() && pageNumber > 0 && pageNumber <= pages.size()) {
            PdfSegment segment = new PdfSegment();
            segment.setPageNumber(pageNumber);
            segment.setBoundingBox(bbox);
            segment.setLabel(node.path("type").asText("segment"));
            pages.get(pageNumber - 1).getSegments().add(segment);
        }

        JsonNode kids = node.path("kids");
        if (kids.isArray()) {
            for (JsonNode kid : kids) {
                collectSegments(kid, pages);
            }
        }

        JsonNode listItems = node.path("list items");
        if (listItems.isArray()) {
            for (JsonNode item : listItems) {
                collectSegments(item, pages);
            }
        }

        JsonNode rowsNode = node.path("rows");
        if (rowsNode.isArray()) {
            for (JsonNode rowNode : rowsNode) {
                collectSegments(rowNode, pages);
                JsonNode cellsNode = rowNode.path("cells");
                if (cellsNode.isArray()) {
                    for (JsonNode cellNode : cellsNode) {
                        collectSegments(cellNode, pages);
                    }
                }
            }
        }
    }

    /**
     * Maps ODL JSON blocks into Header / Paragraph / OtherBlock so {@link ru.sunveil.precision_pdf.pdfparser.export.HtmlExporter}
     * (and JSON exporters that read these lists) behave like Precision / PageR pipelines.
     */
    private void fillSemanticBlocksFromOdl(JsonNode root, List<PdfPage> pages, boolean odlHeuristicTableModel) {
        JsonNode kids = root.path("kids");
        if (!kids.isArray()) {
            return;
        }
        int maxPage = pages.size();

        for (PdfPage page : pages) {
            int pageNum = page.getPageNumber();
            List<LayoutBlock> layoutBlocks = new ArrayList<>();

            for (JsonNode kid : kids) {
                if (kid.path("page number").asInt(0) != pageNum) {
                    continue;
                }
                String topType = kid.path("type").asText("").trim().toLowerCase(Locale.ROOT);
                if ("table".equals(topType)) {
                    Table table = buildTableFromOdl(kid, page, odlHeuristicTableModel);
                    if (table != null && table.getBoundingBox() != null) {
                        BoundingBox bb = table.getBoundingBox();
                        layoutBlocks.add(new LayoutBlock(bb.getCenterY(), bb.getCenterX(), table, null));
                    }
                } else {
                    List<OdlSemanticDraft> subDrafts = new ArrayList<>();
                    collectOdlSemanticDrafts(kid, subDrafts, maxPage);
                    for (OdlSemanticDraft draft : subDrafts) {
                        if (draft.pageNumber == pageNum) {
                            layoutBlocks.add(new LayoutBlock(draft.centerY(), draft.centerX(), null, draft));
                        }
                    }
                }
            }

            layoutBlocks.sort(Comparator
                    .comparingDouble((LayoutBlock b) -> -b.centerY)
                    .thenComparingDouble(b -> b.centerX));

            int order = 0;
            int[] wordOrderRef = new int[]{0};
            for (LayoutBlock block : layoutBlocks) {
                if (block.table != null) {
                    block.table.setOrder(order);
                    page.addTable(block.table);
                    order++;
                    continue;
                }
                OdlSemanticDraft draft = block.draft;
                if (draft == null) {
                    continue;
                }
                List<Word> blockWords = new ArrayList<>();
                List<TextLine> blockLines = BlockTextLayoutBuilder.buildTextLines(
                        draft.pageNumber,
                        draft.boundingBox,
                        draft.text,
                        order,
                        wordOrderRef,
                        blockWords);
                page.getWords().addAll(blockWords);
                page.getTextLines().addAll(blockLines);
                if (!blockLines.isEmpty()) {
                    BlockTextLayoutBuilder.appendTextChunk(page, order, blockLines);
                }

                switch (draft.blockKind) {
                    case HEADER -> {
                        Header header = new Header(draft.pageNumber, draft.boundingBox, draft.text, order, blockLines);
                        page.getHeaders().add(header);
                        page.addBlock(header);
                    }
                    case PARAGRAPH -> {
                        Paragraph paragraph = new Paragraph(
                                draft.pageNumber, draft.boundingBox, draft.text, order, blockLines);
                        page.getParagraphs().add(paragraph);
                        page.addBlock(paragraph);
                    }
                    case OTHER -> {
                        OtherBlock other = new OtherBlock(
                                draft.pageNumber, draft.boundingBox, draft.text, order, draft.odlType, blockLines);
                        page.getOtherBlocks().add(other);
                        page.addBlock(other);
                    }
                }
                order += Math.max(blockLines.size(), 1);
            }

            page.getWords().sort(Comparator.comparingInt(Word::getOrder));
            page.getTextLines().sort(Comparator.comparingInt(TextLine::getOrder));
            page.getPdfTextChunks().sort(Comparator.comparingInt(PdfTextChunk::getOrder));
            page.getHeaders().sort(Comparator.comparingInt(Header::getOrder));
            page.getParagraphs().sort(Comparator.comparingInt(Paragraph::getOrder));
            page.getOtherBlocks().sort(Comparator.comparingInt(OtherBlock::getOrder));
        }
    }

    private Table buildTableFromOdl(JsonNode tableNode, PdfPage page, boolean odlHeuristicTableModel) {
        if (!odlHeuristicTableModel) {
            return buildTableFromOdlJsonRowOrder(tableNode, page);
        }
        return buildTableFromOdlHeuristic(tableNode, page);
    }

    /**
     * One ODL JSON {@code rows[]} entry → one HTML {@code tr}; cells in array order; {@code row span} / {@code column span}
     * from JSON without inflation or occupancy deduplication.
     */
    private Table buildTableFromOdlJsonRowOrder(JsonNode tableNode, PdfPage page) {
        JsonNode rowsNode = tableNode.path("rows");
        if (!rowsNode.isArray() || rowsNode.size() == 0) {
            return null;
        }

        BoundingBox tableBbox = parseOdlBoundingBox(tableNode.path("bounding box"));
        if (tableBbox == null || !tableBbox.isValid()) {
            return null;
        }

        Table table = new Table(
                tableBbox.getX(),
                tableBbox.getTop(),
                tableBbox.getRight(),
                tableBbox.getY(),
                TableType.NOT_BORDERED
        );
        table.setPageNumber(page.getPageNumber());
        table.setSkipHtmlCompaction(true);

        List<List<TableCell>> grid = new ArrayList<>();
        int maxLogicalCols = 0;

        for (JsonNode rowNode : rowsNode) {
            JsonNode cellsNode = rowNode.path("cells");
            if (!cellsNode.isArray()) {
                continue;
            }
            List<TableCell> tr = new ArrayList<>();
            for (JsonNode cellNode : cellsNode) {
                ParsedOdlTableCell p = parseOdlTableCellNode(cellNode, page.getPageNumber());
                if (p == null) {
                    continue;
                }
                TableCell cell = new TableCell(p.boundingBox, p.rowSpan, p.colSpan, List.of());
                cell.setContent(p.text);
                cell.setPageNumber(page.getPageNumber());
                cell.setRow(p.row0);
                cell.setColumn(p.col0);
                tr.add(cell);
            }
            if (!tr.isEmpty()) {
                grid.add(tr);
                int rowWidth = 0;
                for (TableCell c : tr) {
                    rowWidth += Math.max(1, c.getColSpan());
                }
                maxLogicalCols = Math.max(maxLogicalCols, rowWidth);
            }
        }

        if (grid.isEmpty()) {
            return null;
        }

        int declaredRows = tableNode.path("number of rows").asInt(0);
        int declaredCols = tableNode.path("number of columns").asInt(0);
        table.setRows(grid);
        table.setRowCount(declaredRows > 0 ? declaredRows : grid.size());
        table.setColumnCount(declaredCols > 0 ? declaredCols : maxLogicalCols);
        return table;
    }

    /**
     * Legacy: inflate vertical spans from placeholder cells, sort by area, occupancy grid to drop overlaps.
     */
    private Table buildTableFromOdlHeuristic(JsonNode tableNode, PdfPage page) {
        JsonNode rowsNode = tableNode.path("rows");
        if (!rowsNode.isArray() || rowsNode.size() == 0) {
            return null;
        }

        BoundingBox tableBbox = parseOdlBoundingBox(tableNode.path("bounding box"));
        if (tableBbox == null || !tableBbox.isValid()) {
            return null;
        }

        Table table = new Table(
                tableBbox.getX(),
                tableBbox.getTop(),
                tableBbox.getRight(),
                tableBbox.getY(),
                TableType.NOT_BORDERED
        );
        table.setPageNumber(page.getPageNumber());

        List<ParsedOdlTableCell> parsedCells = new ArrayList<>();
        for (JsonNode rowNode : rowsNode) {
            JsonNode cellsNode = rowNode.path("cells");
            if (!cellsNode.isArray()) {
                continue;
            }
            for (JsonNode cellNode : cellsNode) {
                ParsedOdlTableCell parsed = parseOdlTableCellNode(cellNode, page.getPageNumber());
                if (parsed != null) {
                    parsedCells.add(parsed);
                }
            }
        }

        if (parsedCells.isEmpty()) {
            return null;
        }

        int declaredRows = tableNode.path("number of rows").asInt(0);
        int declaredCols = tableNode.path("number of columns").asInt(0);
        int maxR = declaredRows > 0 ? declaredRows : 0;
        int maxC = declaredCols > 0 ? declaredCols : 0;
        for (ParsedOdlTableCell c : parsedCells) {
            maxR = Math.max(maxR, c.row0 + c.rowSpan);
            maxC = Math.max(maxC, c.col0 + c.colSpan);
        }
        if (maxR <= 0 || maxC <= 0) {
            return null;
        }

        inflateVerticalRowSpansFromPlaceholderGrid(parsedCells, maxR, maxC);

        parsedCells.sort(Comparator
                .comparingInt((ParsedOdlTableCell c) -> -(c.rowSpan * c.colSpan))
                .thenComparingInt(c -> c.row0)
                .thenComparingInt(c -> c.col0));

        boolean[][] occupied = new boolean[maxR][maxC];
        NavigableMap<Integer, List<TableCell>> rowMap = new TreeMap<>();

        for (ParsedOdlTableCell p : parsedCells) {
            if (p.row0 < 0 || p.col0 < 0 || p.row0 >= maxR || p.col0 >= maxC) {
                continue;
            }
            if (p.rowSpan < 1 || p.colSpan < 1) {
                continue;
            }

            boolean overlaps = false;
            outer:
            for (int dr = 0; dr < p.rowSpan; dr++) {
                for (int dc = 0; dc < p.colSpan; dc++) {
                    int rr = p.row0 + dr;
                    int cc = p.col0 + dc;
                    if (rr >= maxR || cc >= maxC) {
                        overlaps = true;
                        break outer;
                    }
                    if (occupied[rr][cc]) {
                        overlaps = true;
                        break outer;
                    }
                }
            }
            if (overlaps) {
                continue;
            }

            TableCell cell = new TableCell(p.boundingBox, p.rowSpan, p.colSpan, List.of());
            cell.setContent(p.text);
            cell.setPageNumber(page.getPageNumber());
            cell.setRow(p.row0);
            cell.setColumn(p.col0);

            rowMap.computeIfAbsent(p.row0, k -> new ArrayList<>()).add(cell);

            for (int dr = 0; dr < p.rowSpan; dr++) {
                for (int dc = 0; dc < p.colSpan; dc++) {
                    occupied[p.row0 + dr][p.col0 + dc] = true;
                }
            }
        }

        List<List<TableCell>> grid = new ArrayList<>();
        for (List<TableCell> rowCells : rowMap.values()) {
            rowCells.sort(Comparator.comparingInt(TableCell::getColumn));
            grid.add(rowCells);
        }

        if (grid.isEmpty()) {
            return null;
        }

        table.setRows(grid);
        table.setRowCount(maxR);
        table.setColumnCount(maxC);
        return table;
    }

    /**
     * ODL often encodes a vertical merge as repeated 1×1 empty cells under an anchor cell instead of
     * {@code row span &gt; 1}. Merge those into the anchor so HTML {@code rowspan} matches the PDF.
     */
    private static void inflateVerticalRowSpansFromPlaceholderGrid(
            List<ParsedOdlTableCell> cells, int maxR, int maxC) {
        Map<String, ParsedOdlTableCell> cellAtStart = new HashMap<>();
        for (ParsedOdlTableCell p : cells) {
            cellAtStart.put(slotKey(p.row0, p.col0), p);
        }

        List<ParsedOdlTableCell> anchors = new ArrayList<>();
        for (ParsedOdlTableCell p : cells) {
            if (!odlTableCellIsPlaceholder(p)) {
                anchors.add(p);
            }
        }
        anchors.sort(Comparator.comparingInt((ParsedOdlTableCell a) -> a.row0).thenComparingInt(a -> a.col0));

        Set<ParsedOdlTableCell> removed = new HashSet<>();
        for (ParsedOdlTableCell anchor : anchors) {
            if (removed.contains(anchor)) {
                continue;
            }
            int colSpan = anchor.colSpan;
            int rowSpan = anchor.rowSpan;
            int newRowSpan = rowSpan;
            while (anchor.row0 + newRowSpan < maxR) {
                int rBelow = anchor.row0 + newRowSpan;
                List<ParsedOdlTableCell> band = new ArrayList<>();
                boolean bandOk = true;
                for (int cc = anchor.col0; cc < anchor.col0 + colSpan && cc < maxC; cc++) {
                    ParsedOdlTableCell below = cellAtStart.get(slotKey(rBelow, cc));
                    if (below == null
                            || removed.contains(below)
                            || !odlTableCellIsPlaceholder(below)
                            || below.row0 != rBelow
                            || below.col0 != cc
                            || below.rowSpan != 1
                            || below.colSpan != 1) {
                        bandOk = false;
                        break;
                    }
                    band.add(below);
                }
                if (!bandOk || band.size() != colSpan) {
                    break;
                }
                for (ParsedOdlTableCell b : band) {
                    removed.add(b);
                    cellAtStart.remove(slotKey(b.row0, b.col0));
                }
                newRowSpan++;
            }
            if (newRowSpan > rowSpan) {
                int idx = cells.indexOf(anchor);
                if (idx >= 0) {
                    cells.set(idx, new ParsedOdlTableCell(
                            anchor.row0, anchor.col0, newRowSpan, colSpan, anchor.text, anchor.boundingBox));
                }
            }
        }
        cells.removeIf(removed::contains);
    }

    private static String slotKey(int row, int col) {
        return row + ":" + col;
    }

    private static boolean odlTableCellIsPlaceholder(ParsedOdlTableCell p) {
        return p.rowSpan == 1
                && p.colSpan == 1
                && (p.text == null || p.text.isBlank());
    }

    private static ParsedOdlTableCell parseOdlTableCellNode(JsonNode cellNode, int expectedPage) {
        BoundingBox cellBbox = parseOdlBoundingBox(cellNode.path("bounding box"));
        if (cellBbox == null || !cellBbox.isValid()) {
            return null;
        }
        int pageNumber = cellNode.path("page number").asInt(expectedPage);
        if (pageNumber != 0 && pageNumber != expectedPage) {
            return null;
        }
        int row0 = cellNode.path("row number").asInt(1) - 1;
        int col0 = cellNode.path("column number").asInt(1) - 1;
        int rowSpan = Math.max(1, cellNode.path("row span").asInt(1));
        int colSpan = Math.max(1, cellNode.path("column span").asInt(1));
        String cellText = extractOdlCellText(cellNode);
        return new ParsedOdlTableCell(row0, col0, rowSpan, colSpan, cellText, cellBbox);
    }

    private record ParsedOdlTableCell(
            int row0,
            int col0,
            int rowSpan,
            int colSpan,
            String text,
            BoundingBox boundingBox
    ) {
    }

    private static BoundingBox parseOdlBoundingBox(JsonNode bboxNode) {
        if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() != 4) {
            return null;
        }
        float x1 = (float) bboxNode.get(0).asDouble(Float.NaN);
        float y1 = (float) bboxNode.get(1).asDouble(Float.NaN);
        float x2 = (float) bboxNode.get(2).asDouble(Float.NaN);
        float y2 = (float) bboxNode.get(3).asDouble(Float.NaN);
        float w = x2 - x1;
        float h = y2 - y1;
        if (Float.isNaN(x1) || Float.isNaN(y1) || w <= 0 || h <= 0) {
            return null;
        }
        return new BoundingBox(x1, y1, w, h);
    }

    private static String extractOdlCellText(JsonNode cellNode) {
        StringBuilder sb = new StringBuilder();
        appendOdlTextFromKids(cellNode.path("kids"), sb);
        return sb.toString().trim();
    }

    private static void appendOdlTextFromKids(JsonNode node, StringBuilder sb) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                appendOdlTextFromKids(child, sb);
            }
            return;
        }
        String type = node.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        if ("paragraph".equals(type)) {
            String content = node.path("content").asText("").trim();
            if (!content.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(content);
            }
        }
        appendOdlTextFromKids(node.path("kids"), sb);
        JsonNode listItems = node.path("list items");
        if (listItems.isArray()) {
            for (JsonNode item : listItems) {
                appendOdlTextFromKids(item, sb);
            }
        }
    }

    private static final class LayoutBlock {
        final double centerY;
        final double centerX;
        final Table table;
        final OdlSemanticDraft draft;

        LayoutBlock(double centerY, double centerX, Table table, OdlSemanticDraft draft) {
            this.centerY = centerY;
            this.centerX = centerX;
            this.table = table;
            this.draft = draft;
        }
    }

    private void collectOdlSemanticDrafts(JsonNode node, List<OdlSemanticDraft> drafts, int maxPage) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        String nodeType = node.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        if ("table".equals(nodeType)) {
            return;
        }

        JsonNode nestedKids = node.path("kids");
        if (nestedKids.isArray()) {
            for (JsonNode child : nestedKids) {
                collectOdlSemanticDrafts(child, drafts, maxPage);
            }
        }
        JsonNode listItems = node.path("list items");
        if (listItems.isArray()) {
            for (JsonNode item : listItems) {
                collectOdlSemanticDrafts(item, drafts, maxPage);
            }
        }

        String type = node.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        if ("list".equals(type)) {
            return;
        }

        String text = node.path("content").asText("").trim();
        if (text.isEmpty()) {
            return;
        }

        JsonNode bboxNode = node.path("bounding box");
        int pageNumber = node.path("page number").asInt(0);
        if (!bboxNode.isArray() || bboxNode.size() != 4 || pageNumber < 1 || pageNumber > maxPage) {
            return;
        }

        BoundingBox bbox = parseOdlBoundingBox(bboxNode);
        if (bbox == null || !bbox.isValid()) {
            return;
        }
        String displayText = text;
        if ("list item".equals(type)) {
            displayText = "• " + text;
        }

        SemanticBlockKind kind = classifyOdlBlockKind(type);
        drafts.add(new OdlSemanticDraft(pageNumber, bbox, displayText, type, kind));
    }

    private static SemanticBlockKind classifyOdlBlockKind(String typeLower) {
        if (typeLower.contains("heading")
                || "title".equals(typeLower)
                || "header".equals(typeLower)) {
            return SemanticBlockKind.HEADER;
        }
        if ("paragraph".equals(typeLower) || "list item".equals(typeLower)) {
            return SemanticBlockKind.PARAGRAPH;
        }
        return SemanticBlockKind.OTHER;
    }

    private enum SemanticBlockKind {
        HEADER,
        PARAGRAPH,
        OTHER
    }

    private static final class OdlSemanticDraft {
        final int pageNumber;
        final BoundingBox boundingBox;
        final String text;
        final String odlType;
        final SemanticBlockKind blockKind;

        OdlSemanticDraft(int pageNumber, BoundingBox boundingBox, String text, String odlType, SemanticBlockKind blockKind) {
            this.pageNumber = pageNumber;
            this.boundingBox = boundingBox;
            this.text = text;
            this.odlType = odlType;
            this.blockKind = blockKind;
        }

        double centerY() {
            return boundingBox.getCenterY();
        }

        double centerX() {
            return boundingBox.getCenterX();
        }
    }
}
