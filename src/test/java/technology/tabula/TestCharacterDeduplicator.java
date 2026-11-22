package technology.tabula;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TestCharacterDeduplicator {

    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static TextElement charAt(String text, float top, float left,
                                      float width, float height, float dir) {
        return new TextElement(top, left, width, height, FONT, 10f, text, 2.5f, dir);
    }

    private static TextElement charAt(String text, float top, float left) {
        return charAt(text, top, left, 10f, 10f, 0f);
    }

    @Test
    public void defaultThresholdIsHalf() {
        assertEquals(0.5f, new CharacterDeduplicator().getOverlapThreshold(), 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroThresholdRejected() {
        new CharacterDeduplicator(0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void thresholdAboveOneRejected() {
        new CharacterDeduplicator(1.01f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nanThresholdRejected() {
        new CharacterDeduplicator(Float.NaN);
    }

    @Test
    public void exactRedrawAtSamePositionIsDeduplicated() {
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f));
        chars.add(charAt("5", 100f, 10f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(1, result.getTextElements().size());
        assertSame(chars.get(0), result.getTextElements().get(0));
    }

    @Test
    public void slightlyOffsetRedrawIsDeduplicated() {
        // second glyph shifted by 2pt on each axis: 8x8 intersection of a 10x10
        // box => 64% of each area, above the 50% threshold
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("9", 100f, 10f));
        chars.add(charAt("9", 102f, 12f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(1, result.getTextElements().size());
        assertSame(chars.get(0), result.getTextElements().get(0));
    }

    @Test
    public void earliestDrawnCharacterIsKept() {
        List<TextElement> chars = new ArrayList<>();
        TextElement first = charAt("7", 100f, 10f);
        TextElement second = charAt("7", 101f, 11f);
        TextElement third = charAt("7", 100.5f, 10.5f);
        chars.add(first);
        chars.add(second);
        chars.add(third);

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(1, result.getTextElements().size());
        assertSame(first, result.getTextElements().get(0));
    }

    @Test
    public void genuinelyAdjacentRepeatedDigitsAreKept() {
        // side-by-side "11": boxes do not intersect, so both stay
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("1", 100f, 10f));
        chars.add(charAt("1", 100f, 20f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, result.getTextElements().size());
    }

    @Test
    public void differentTextAtSamePositionIsKept() {
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f));
        chars.add(charAt("6", 100f, 10f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, result.getTextElements().size());
    }

    @Test
    public void differentDirectionAtSamePositionIsKept() {
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f, 10f, 10f, 0f));
        chars.add(charAt("5", 100f, 10f, 10f, 10f, 90f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, result.getTextElements().size());
    }

    @Test
    public void overlapBelowThresholdIsKept() {
        // 6x6 intersection = 36% of each 10x10 area, below the 50% threshold
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f));
        chars.add(charAt("5", 104f, 14f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, result.getTextElements().size());
    }

    @Test
    public void asymmetricOverlapMustCoverBothAreas() {
        // a small box sits fully inside a big one: the small box is 100%
        // covered, but the intersection is only 25% of the big box, so with a
        // 50% threshold they are NOT duplicates in either direction
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f, 20f, 20f, 0f));
        chars.add(charAt("5", 105f, 15f, 10f, 10f, 0f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, result.getTextElements().size());

        // with a 20% threshold both containment ratios pass
        CharacterDeduplicator.DeduplicationResult permissive =
                new CharacterDeduplicator(0.2f).deduplicate(chars);
        assertEquals(1, permissive.getTextElements().size());
    }

    @Test
    public void thresholdIsInclusive() {
        // exactly 50% of each area: half-offset boxes sharing a 5x10 strip
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f));
        chars.add(charAt("5", 100f, 15f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator(0.5f).deduplicate(chars);

        assertEquals(1, result.getTextElements().size());
    }

    @Test
    public void droppedCharacterIsNotUsedToDeduplicateLaterOnes() {
        // first "5" at x=10 (kept), a duplicate at x=12 (dropped), then a
        // third "5" at x=16.4: it overlaps the dropped second 64% but the kept
        // first only 36%, so the second must not be used to reject it.
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f));
        chars.add(charAt("5", 100f, 12f));
        chars.add(charAt("5", 100f, 16.4f));

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, result.getTextElements().size());
        assertSame(chars.get(0), result.getTextElements().get(0));
        assertSame(chars.get(2), result.getTextElements().get(1));
    }

    @Test
    public void returnedIndexContainsExactlyTheRetainedCharacters() {
        List<TextElement> chars = new ArrayList<>();
        TextElement kept = charAt("5", 100f, 10f);
        TextElement dropped = charAt("5", 101f, 11f);
        TextElement other = charAt("8", 200f, 50f);
        chars.add(kept);
        chars.add(dropped);
        chars.add(other);

        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(chars);

        List<TextElement> inIndex = result.getSpatialIndex()
                .intersects(new Rectangle(0f, 0f, 500f, 500f));
        assertEquals(2, inIndex.size());
        assertTrue(inIndex.contains(kept));
        assertTrue(inIndex.contains(other));
        assertFalse(inIndex.contains(dropped));
    }

    @Test
    public void emptyInputIsHandled() {
        CharacterDeduplicator.DeduplicationResult result =
                new CharacterDeduplicator().deduplicate(new ArrayList<TextElement>());
        assertTrue(result.getTextElements().isEmpty());
    }

    @Test
    public void inputListIsNotModified() {
        List<TextElement> chars = new ArrayList<>();
        chars.add(charAt("5", 100f, 10f));
        chars.add(charAt("5", 100f, 10f));

        new CharacterDeduplicator().deduplicate(chars);

        assertEquals(2, chars.size());
    }
}
