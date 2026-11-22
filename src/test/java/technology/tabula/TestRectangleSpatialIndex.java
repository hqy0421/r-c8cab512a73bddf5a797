package technology.tabula;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestRectangleSpatialIndex {

	@Test
	public void testIntersects() {

		Rectangle r = new Rectangle(0, 0, 0, 0);

		RectangleSpatialIndex<Rectangle> rSpatialIndex = new RectangleSpatialIndex<>();
		rSpatialIndex.add(r);

		assertTrue(rSpatialIndex.intersects(r).size() > 0);

	}

	@Test
	public void testInsertAndQueryCanBeInterleaved() {
		// Character deduplication queries the index before every insert; the
		// underlying index must therefore accept inserts after a query.
		RectangleSpatialIndex<Rectangle> rSpatialIndex = new RectangleSpatialIndex<>();

		Rectangle first = new Rectangle(0, 0, 10, 10);
		rSpatialIndex.add(first);
		assertEquals(1, rSpatialIndex.intersects(first).size());

		Rectangle second = new Rectangle(5, 5, 10, 10);
		rSpatialIndex.add(second);

		assertEquals(2, rSpatialIndex.intersects(new Rectangle(0, 0, 20, 20)).size());
	}

}
