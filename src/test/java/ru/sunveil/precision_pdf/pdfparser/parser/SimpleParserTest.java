package ru.sunveil.precision_pdf.pdfparser.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;
import ru.sunveil.precision_pdf.pdfparser.model.PdfDocument;
import ru.sunveil.precision_pdf.pdfparser.model.PdfPage;
import ru.sunveil.precision_pdf.pdfparser.model.Ruling;
import ru.sunveil.precision_pdf.pdfparser.model.TextLine;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleParserTest {
    private SimpleParser parser;
    private PdfDocument document;

    @BeforeEach
    public void setUp() throws Exception {
        parser = new SimpleParser();
        java.net.URL res = getClass().getClassLoader().getResource("table.pdf");
        assertNotNull(res, "table.pdf resource must be available in test classpath");
        File pdf = new File(res.toURI());
        assertTrue(pdf.exists(), "resolved resource file should exist on disk");
        document = parser.parse(pdf, (ExtractionConfig) null);
        assertNotNull(document, "parsed document should not be null");
    }

    @Test
    public void testPageCount() {
        assertEquals(1, document.getTotalPages(), "table.pdf is expected to contain 1 page");
    }

    @Test
    public void testSplitLinesForNumberCell() {
        PdfPage page = document.getPages().get(0);
        List<TextLine> lines = page.getTextLines();
        assertFalse(lines.isEmpty(), "there should be some text lines extracted");

        System.out.println("--- extracted lines ---");
        for (TextLine ln : lines) {
            System.out.printf("text='%s' bbox=%s words=%d\n", ln.getText(), ln.getBoundingBox(), ln.getWords().size());
        }

        TextLine numLine = lines.stream()
                .filter(l -> l.getText().trim().equals("№"))
                .findFirst()
                .orElse(null);
        assertNotNull(numLine, "a separate line with only the № symbol must exist");
        assertEquals(1, numLine.getWords().size(), "№ line should consist of a single word");
        float numWidth = numLine.getBoundingBox().getWidth();
        assertTrue(numWidth < 100,
                "№ line bounding box should be narrow (was " + numWidth + ")");
        assertEquals(13.38f, numWidth, 0.5f, "unexpected width for № cell");

        TextLine taskLine = lines.stream()
                .filter(l -> l.getText().toLowerCase().contains("задача"))
                .findFirst()
                .orElse(null);
        assertNotNull(taskLine, "a separate line containing the word 'задача' must exist");
        assertEquals(1, taskLine.getWords().size(), "задача line should consist of a single word");
        float taskWidth = taskLine.getBoundingBox().getWidth();
        assertEquals(41.82f, taskWidth, 1.0f, "unexpected width for Задача cell");

        boolean merged = lines.stream()
                .anyMatch(l -> l.getText().contains("№") && l.getText().contains("задача"));
        assertFalse(merged, "there should be no line containing both № и задача вместе");
    }

    @Test
    public void testRulingsAndGeneralContent() {
        PdfPage page = document.getPages().get(0);

        List<TextLine> lines = page.getTextLines();
        assertTrue(lines.size() > 30, "expected many lines on the page");
        assertTrue(lines.stream().anyMatch(l -> l.getText().contains("Задачи на 2026 год")),
                "header text should appear on the first page");

        List<Ruling> rulings = page.getVisibleRulings();
        assertNotNull(rulings, "visible rulings list must not be null");
        assertFalse(rulings.isEmpty(), "there should be some visible rulings on the page");

        long verticalCount = rulings.stream().filter(Ruling::isVertical).count();
        long horizontalCount = rulings.size() - verticalCount;
        assertTrue(verticalCount >= 3, "expect at least three vertical rulings");
        assertTrue(horizontalCount >= 5, "expect several horizontal rulings");

        System.out.println("--- visible rulings ---");
        for (Ruling r : rulings) {
            if (r.isVertical()) {
                System.out.printf("VERT x=%.2f y1=%.2f y2=%.2f\n", r.getLeft(), r.getTop(), r.getBottom());
            } else {
                System.out.printf("HORIZ y=%.2f x1=%.2f x2=%.2f\n", r.getTop(), r.getLeft(), r.getRight());
            }
        }

        TextLine numLine = lines.stream()
                .filter(l -> l.getText().trim().equals("№"))
                .findFirst().orElseThrow();
        TextLine taskLine = lines.stream()
                .filter(l -> l.getText().toLowerCase().contains("задача"))
                .findFirst().orElseThrow();
        float leftBoundary = numLine.getBoundingBox().getX();
        float rightBoundary = taskLine.getBoundingBox().getX();
        boolean foundBoundary = rulings.stream()
                .filter(Ruling::isVertical)
                .anyMatch(r -> Math.abs(r.getLeft() - numLine.getBoundingBox().getRight()) < 10);
        assertTrue(foundBoundary,
                "should have a vertical ruling near the right edge of № cell (within 10pt)");
    }

    @Test
    public void testHtmlExporterProducesHtml() {
        ru.sunveil.precision_pdf.pdfparser.export.HtmlExporter exporter = new ru.sunveil.precision_pdf.pdfparser.export.HtmlExporter();
        String html = exporter.export(document, ru.sunveil.precision_pdf.pdfparser.export.ExportFormat.HTML);
        assertNotNull(html, "HTML output should not be null");
        assertTrue(html.contains("<html"), "output should contain html tag");
        assertTrue(html.contains("Задачи на 2026 год") || html.contains("<span"), "should include some content from page");
    }
}
