package ru.sunveil.precision_pdf.pdfparser.util;

import ru.sunveil.precision_pdf.pdfparser.model.TableCell;
import ru.sunveil.precision_pdf.pdfparser.model.core.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class HtmlEmbeddedTableParser {

    private static final Pattern TABLE_FRAGMENT = Pattern.compile(
            "(?is)<table\\b[^>]*>(.*?)</table>");
    private static final Pattern ROW_FRAGMENT = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL_FRAGMENT = Pattern.compile("(?is)<t([dh])\\b([^>]*)>(.*?)</t\\1>");
    private static final Pattern TAG_STRIP = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern ATTR_INT = Pattern.compile(
            "(?i)\\b(rowspan|colspan)\\s*=\\s*\"?(\\d+)\"?");

    private HtmlEmbeddedTableParser() {
    }

    public static final class ParseResult {
        private final List<List<TableCell>> rows;
        private final int columnCount;

        public ParseResult(List<List<TableCell>> rows, int columnCount) {
            this.rows = rows;
            this.columnCount = columnCount;
        }

        public List<List<TableCell>> getRows() {
            return rows;
        }

        public int getColumnCount() {
            return columnCount;
        }

        public boolean isEmpty() {
            return rows == null || rows.isEmpty();
        }
    }

    public static ParseResult parse(String html, BoundingBox tableBBox, int pageNumber) {
        if (html == null || html.isBlank() || tableBBox == null || !tableBBox.isValid()) {
            return new ParseResult(List.of(), 0);
        }
        Matcher tableMatcher = TABLE_FRAGMENT.matcher(html.trim());
        if (!tableMatcher.find()) {
            return new ParseResult(List.of(), 0);
        }
        String tableBody = tableMatcher.group(1);

        List<List<RawCell>> rawRows = new ArrayList<>();
        Matcher rowMatcher = ROW_FRAGMENT.matcher(tableBody);
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            List<RawCell> cells = new ArrayList<>();
            Matcher cellMatcher = CELL_FRAGMENT.matcher(rowHtml);
            while (cellMatcher.find()) {
                String attrs = cellMatcher.group(2) == null ? "" : cellMatcher.group(2);
                String inner = cellMatcher.group(3);
                String text = normalizeCellText(inner);
                int rowSpan = attrInt(attrs, "rowspan", 1);
                int colSpan = attrInt(attrs, "colspan", 1);
                cells.add(new RawCell(text, rowSpan, colSpan));
            }
            if (!cells.isEmpty()) {
                rawRows.add(cells);
            }
        }
        if (rawRows.isEmpty()) {
            return new ParseResult(List.of(), 0);
        }

        int rowCount = rawRows.size();
        int colCount = logicalColumnCount(rawRows);
        if (colCount < 1) {
            return new ParseResult(List.of(), 0);
        }

        boolean[][] occupied = new boolean[rowCount][colCount];
        List<List<TableCell>> outRows = new ArrayList<>();

        for (int r = 0; r < rowCount; r++) {
            List<TableCell> outRow = new ArrayList<>();
            int col = 0;
            for (RawCell raw : rawRows.get(r)) {
                col = nextFreeColumn(occupied, r, col);
                if (col < 0 || col >= colCount) {
                    break;
                }
                int rowSpan = Math.max(1, raw.rowSpan);
                int colSpan = Math.max(1, raw.colSpan);
                if (col + colSpan > colCount) {
                    colSpan = colCount - col;
                }
                if (rowSpan > rowCount - r) {
                    rowSpan = rowCount - r;
                }
                markOccupied(occupied, r, col, rowSpan, colSpan);

                BoundingBox cellBBox = cellBBox(tableBBox, r, col, rowCount, colCount, rowSpan, colSpan);
                TableCell cell = new TableCell(cellBBox, rowSpan, colSpan, List.of());
                cell.setPageNumber(pageNumber);
                cell.setContent(raw.text);
                cell.setRow(r);
                cell.setColumn(col);
                outRow.add(cell);
                col += colSpan;
            }
            if (!outRow.isEmpty()) {
                outRows.add(outRow);
            }
        }

        return new ParseResult(outRows, colCount);
    }

    private static int logicalColumnCount(List<List<RawCell>> rawRows) {
        int maxCols = 0;
        for (List<RawCell> row : rawRows) {
            int width = 0;
            for (RawCell c : row) {
                width += Math.max(1, c.colSpan);
            }
            maxCols = Math.max(maxCols, width);
        }
        boolean[][] occupied = new boolean[rawRows.size()][Math.max(maxCols, 1)];
        int resolved = 0;
        for (int r = 0; r < rawRows.size(); r++) {
            int col = 0;
            for (RawCell raw : rawRows.get(r)) {
                col = nextFreeColumn(occupied, r, col);
                int colSpan = Math.max(1, raw.colSpan);
                int rowSpan = Math.max(1, raw.rowSpan);
                markOccupied(occupied, r, col, rowSpan, colSpan);
                resolved = Math.max(resolved, col + colSpan);
                col += colSpan;
            }
        }
        return Math.max(resolved, maxCols);
    }

    private static int nextFreeColumn(boolean[][] occupied, int row, int startCol) {
        int col = Math.max(0, startCol);
        while (row < occupied.length && col < occupied[row].length && occupied[row][col]) {
            col++;
        }
        if (row >= occupied.length) {
            return -1;
        }
        if (col >= occupied[row].length) {
            return col;
        }
        return col;
    }

    private static void markOccupied(boolean[][] occupied, int row, int col, int rowSpan, int colSpan) {
        for (int dr = 0; dr < rowSpan; dr++) {
            int rr = row + dr;
            if (rr >= occupied.length) {
                break;
            }
            if (occupied[rr].length < col + colSpan) {
                boolean[] next = new boolean[col + colSpan];
                System.arraycopy(occupied[rr], 0, next, 0, occupied[rr].length);
                occupied[rr] = next;
            }
            for (int dc = 0; dc < colSpan; dc++) {
                occupied[rr][col + dc] = true;
            }
        }
    }

    private static BoundingBox cellBBox(
            BoundingBox table, int rowFromTop, int col, int rowCount, int colCount, int rowSpan, int colSpan) {
        float cellW = table.getWidth() / colCount;
        float cellH = table.getHeight() / rowCount;
        float x = table.getX() + col * cellW;
        float top = table.getTop() - rowFromTop * cellH;
        float height = cellH * rowSpan;
        float y = top - height;
        if (y < table.getY()) {
            y = table.getY();
            height = top - y;
        }
        float width = cellW * colSpan;
        if (x + width > table.getRight()) {
            width = table.getRight() - x;
        }
        return new BoundingBox(x, y, Math.max(width, 1f), Math.max(height, 1f));
    }

    private static String normalizeCellText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = TAG_STRIP.matcher(html).replaceAll(" ");
        text = text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return text;
    }

    private static int attrInt(String attrs, String name, int defaultValue) {
        if (attrs == null || attrs.isBlank()) {
            return defaultValue;
        }
        Matcher m = ATTR_INT.matcher(attrs);
        while (m.find()) {
            if (name.equalsIgnoreCase(m.group(1))) {
                try {
                    return Math.max(1, Integer.parseInt(m.group(2)));
                } catch (NumberFormatException ignored) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private record RawCell(String text, int rowSpan, int colSpan) {
    }
}
