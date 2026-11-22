package technology.tabula;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Test;

import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * End-to-end behavior of optional character deduplication on PDFs whose text
 * layer repaints every glyph twice with a small offset.
 */
public class TestCharacterDeduplicationEndToEnd {

    private static final String TEXT = "12345";
    private static final float FONT_SIZE = 12f;
    private static final float TEXT_X = 100f;
    private static final float TEXT_Y = 700f;
    // small diagonal repaint: PDFBox's own suppression hides the second copy,
    // our mode must still remove it once suppression is disabled
    private static final float SMALL_OFFSET = 0.5f;
    // horizontal 2.5pt repaint: large enough to bypass PDFBox's suppression so
    // the duplicates reach tabula by default, while the boxes still overlap by
    // ~62% horizontally (and 100% vertically), above the 50% area threshold
    private static final float VISIBLE_REPAINT_OFFSET_X = 2.5f;
    private static final float VISIBLE_REPAINT_OFFSET_Y = 0f;

    private byte[] buildRepaintedPdf(float offsetX, float offsetY) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, FONT_SIZE);
                cs.newLineAtOffset(TEXT_X, TEXT_Y);
                cs.showText(TEXT);
                // repaint the same string offset from the original, as a
                // redrawn text layer would
                cs.newLineAtOffset(offsetX, offsetY);
                cs.showText(TEXT);
                cs.endText();
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    private Page extractFirstPage(byte[] pdf, CharacterDeduplicator deduplicator) throws IOException {
        PDDocument doc = Loader.loadPDF(pdf);
        try (ObjectExtractor oe = new ObjectExtractor(doc, deduplicator)) {
            return oe.extract(1);
        }
    }

    private long countChar(List<TextElement> elements, String ch) {
        return elements.stream().filter(te -> te.getText().equals(ch)).count();
    }

    @Test
    public void defaultBehaviorIsUnchangedForOffsetThatBypassesPdfboxSuppression() throws IOException {
        // a 2.5pt horizontal repaint is large enough to bypass PDFBox's own
        // duplicate suppression, so without our mode both copies reach the page
        byte[] pdf = buildRepaintedPdf(VISIBLE_REPAINT_OFFSET_X, VISIBLE_REPAINT_OFFSET_Y);
        Page page = extractFirstPage(pdf, null);

        assertEquals(2 * TEXT.length(), page.getText().size());
        assertEquals(2, countChar(page.getText(), "3"));
    }

    @Test
    public void smallOffsetRepaintIsDeduplicated() throws IOException {
        // 0.5pt offset: PDFBox would normally suppress the second copy; with
        // our mode enabled it still reaches the stripper and is removed by the
        // configured area-ratio rule instead
        byte[] pdf = buildRepaintedPdf(SMALL_OFFSET, SMALL_OFFSET);
        Page page = extractFirstPage(pdf, new CharacterDeduplicator());

        assertEquals(TEXT.length(), page.getText().size());
        for (String ch : TEXT.split("")) {
            assertEquals(1, countChar(page.getText(), ch));
        }
    }

    @Test
    public void largerOffsetRepaintIsDeduplicated() throws IOException {
        // 2.5pt horizontal offset: the boxes still overlap ~62% horizontally
        // and 100% vertically, so each area ratio stays above the 50% threshold
        byte[] pdf = buildRepaintedPdf(VISIBLE_REPAINT_OFFSET_X, VISIBLE_REPAINT_OFFSET_Y);
        Page page = extractFirstPage(pdf, new CharacterDeduplicator());

        assertEquals(TEXT.length(), page.getText().size());
        for (String ch : TEXT.split("")) {
            assertEquals(1, countChar(page.getText(), ch));
        }
    }

    @Test
    public void textListAndSpatialIndexShareTheSameRetainedSet() throws IOException {
        byte[] pdf = buildRepaintedPdf(VISIBLE_REPAINT_OFFSET_X, VISIBLE_REPAINT_OFFSET_Y);
        Page page = extractFirstPage(pdf, new CharacterDeduplicator());

        List<TextElement> viaList = page.getText();
        List<TextElement> viaIntersects = page.getSpatialIndex()
                .intersects(new Rectangle(0f, 0f, (float) page.getWidth(), (float) page.getHeight()));

        assertEquals(viaList.size(), viaIntersects.size());
        assertTrue(viaIntersects.containsAll(viaList));
        assertTrue(viaList.containsAll(viaIntersects));
    }

    @Test
    public void streamModeObeysDeduplication() throws IOException {
        byte[] pdf = buildRepaintedPdf(VISIBLE_REPAINT_OFFSET_X, VISIBLE_REPAINT_OFFSET_Y);

        Table raw = new BasicExtractionAlgorithm()
                .extract(extractFirstPage(pdf, null)).get(0);
        Table deduped = new BasicExtractionAlgorithm()
                .extract(extractFirstPage(pdf, new CharacterDeduplicator())).get(0);

        String rawText = raw.getCell(0, 0).getText();
        String dedupedText = deduped.getCell(0, 0).getText();

        assertNotEquals(rawText, dedupedText);
        assertTrue("expected single amount, got: " + dedupedText,
                dedupedText.replace(" ", "").contains(TEXT));
        assertTrue(rawText.length() > dedupedText.length());
    }

    @Test
    public void latticeModeObeysDeduplication() throws IOException {
        byte[] pdf = buildRepaintedPdf(VISIBLE_REPAINT_OFFSET_X, VISIBLE_REPAINT_OFFSET_Y);

        Page rawPage = extractFirstPage(pdf, null);
        Page dedupedPage = extractFirstPage(pdf, new CharacterDeduplicator());

        // lattice reads characters through page.getText(area), i.e. the spatial
        // index, so both access paths must already be deduplicated. The region
        // is the text bounding box enlarged slightly so every glyph is fully
        // contained (getText(area) uses strict containment).
        Rectangle textBounds = Utils.bounds(rawPage.getText());
        Rectangle textRegion = new Rectangle(textBounds.getTop() - 1f,
                textBounds.getLeft() - 1f,
                (float) textBounds.getWidth() + 2f,
                (float) textBounds.getHeight() + 2f);

        assertEquals(2 * TEXT.length(), rawPage.getText(textRegion).size());
        assertEquals(TEXT.length(), dedupedPage.getText(textRegion).size());

        // the lattice algorithm runs on the deduped page without error
        List<Table> tables = new SpreadsheetExtractionAlgorithm().extract(dedupedPage);
        assertTrue(tables.isEmpty() || tables.get(0).getCell(0, 0) != null);
    }

    @Test
    public void genuinelyAdjacentRepeatedDigitsSurvive() throws IOException {
        // "11" painted once each: adjacent, non-overlapping boxes must be kept
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, FONT_SIZE);
                cs.newLineAtOffset(TEXT_X, TEXT_Y);
                cs.showText("11");
                cs.endText();
            }
            doc.save(out);

            Page deduped = extractFirstPage(out.toByteArray(), new CharacterDeduplicator());
            assertEquals(2, countChar(deduped.getText(), "1"));
        }
    }
}
