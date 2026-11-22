package technology.tabula;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes duplicate {@link TextElement}s produced by PDFs whose text layer is
 * drawn more than once at (almost) the same position, e.g. bills that repaint
 * the same glyphs with a small offset.
 * <p>
 * A character is considered a duplicate of an already retained one when:
 * <ul>
 *     <li>their text is equal,</li>
 *     <li>their direction is equal, and</li>
 *     <li>the intersection of their bounding boxes accounts for at least
 *         {@code overlapThreshold} of <em>each</em> character's own area.</li>
 * </ul>
 * Characters are processed in drawing order: the first character of a group of
 * duplicates is retained and every later one is dropped. Candidates are found
 * through a {@link RectangleSpatialIndex}, so the page is never compared
 * pairwise. The same retained characters populate both the returned list and a
 * spatial index, keeping {@link Page#getText()} and {@link Page#getText(Rectangle)}
 * consistent. Both stream and lattice extraction modes therefore follow the
 * exact same rule, since deduplication happens before any word merging.
 */
public class CharacterDeduplicator {

    /**
     * Default minimum fraction of each character's area that the intersection
     * with another character must cover for the two to be considered duplicates.
     */
    public static final float DEFAULT_OVERLAP_THRESHOLD = 0.5f;

    private final float overlapThreshold;

    /**
     * Creates a deduplicator with the {@link #DEFAULT_OVERLAP_THRESHOLD}.
     */
    public CharacterDeduplicator() {
        this(DEFAULT_OVERLAP_THRESHOLD);
    }

    /**
     * Creates a deduplicator with an explicit overlap threshold.
     *
     * @param overlapThreshold fraction in the range (0, 1]; the intersection
     *                         area must reach this fraction of <em>both</em>
     *                         character areas for a duplicate to be detected
     */
    public CharacterDeduplicator(float overlapThreshold) {
        if (Float.isNaN(overlapThreshold) || overlapThreshold <= 0f || overlapThreshold > 1f) {
            throw new IllegalArgumentException(
                    "overlapThreshold must be in the range (0, 1], got " + overlapThreshold);
        }
        this.overlapThreshold = overlapThreshold;
    }

    public float getOverlapThreshold() {
        return overlapThreshold;
    }

    /**
     * Returns true when {@code candidate} is a duplicate of a character that
     * was kept earlier. Only characters previously accepted into
     * {@code retainedIndex} participate in the decision; a character that was
     * itself discarded can never make another one be discarded.
     */
    public boolean isDuplicate(TextElement candidate,
                               RectangleSpatialIndex<TextElement> retainedIndex) {
        // The spatial index only returns rectangles whose bounding boxes
        // intersect, so this is a local lookup rather than a full-page scan.
        for (TextElement retained : retainedIndex.intersects(candidate)) {
            if (retained.getText().equals(candidate.getText())
                    && Float.compare(retained.getDirection(), candidate.getDirection()) == 0
                    && containmentOverlapRatio(retained, candidate) >= overlapThreshold
                    && containmentOverlapRatio(candidate, retained) >= overlapThreshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fraction of {@code a}'s area covered by its intersection with {@code b}.
     * Unlike {@link Rectangle#overlapRatio(Rectangle)} (intersection over
     * union), each character is measured against its own area.
     */
    private static float containmentOverlapRatio(Rectangle a, Rectangle b) {
        float area = a.getArea();
        if (area <= 0f) {
            return 0f;
        }
        float intersectionWidth = Math.max(0f,
                Math.min(a.getRight(), b.getRight()) - Math.max(a.getLeft(), b.getLeft()));
        float intersectionHeight = Math.max(0f,
                Math.min(a.getBottom(), b.getBottom()) - Math.max(a.getTop(), b.getTop()));
        return (intersectionWidth * intersectionHeight) / area;
    }

    /**
     * Deduplicates a page's characters in drawing order. The input list is not
     * modified. The returned list and the returned index contain exactly the
     * same retained characters, so they can back both {@link Page#getText()}
     * and the region query index.
     *
     * @param textElements characters in the order in which they were drawn
     * @return the retained characters together with a spatial index over them
     */
    public DeduplicationResult deduplicate(List<TextElement> textElements) {
        List<TextElement> retained = new ArrayList<>(textElements.size());
        RectangleSpatialIndex<TextElement> retainedIndex = new RectangleSpatialIndex<>();

        for (TextElement candidate : textElements) {
            if (isDuplicate(candidate, retainedIndex)) {
                continue;
            }
            retained.add(candidate);
            retainedIndex.add(candidate);
        }

        return new DeduplicationResult(retained, retainedIndex);
    }

    /**
     * The retained characters and a spatial index built over exactly those
     * same characters.
     */
    public static class DeduplicationResult {
        private final List<TextElement> textElements;
        private final RectangleSpatialIndex<TextElement> spatialIndex;

        DeduplicationResult(List<TextElement> textElements,
                            RectangleSpatialIndex<TextElement> spatialIndex) {
            this.textElements = textElements;
            this.spatialIndex = spatialIndex;
        }

        public List<TextElement> getTextElements() {
            return textElements;
        }

        public RectangleSpatialIndex<TextElement> getSpatialIndex() {
            return spatialIndex;
        }
    }
}
