package technology.tabula;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

public class RectangleSpatialIndex<T extends Rectangle> {


    // A Quadtree is used instead of an STRtree because items must be inserted
    // and queried interleaved (see CharacterDeduplicator): STRtree packs and
    // freezes itself on the first query, while Quadtree stays dynamic.
    private final Quadtree si = new Quadtree();
    private final List<T> rectangles = new ArrayList<>();

    public void add(T te) {
        rectangles.add(te);
        si.insert(new Envelope(te.getLeft(), te.getRight(), te.getBottom(), te.getTop()), te);
    }
    
    public List<T> contains(Rectangle r) {
        List<T> intersection = si.query(new Envelope(r.getLeft(), r.getRight(), r.getTop(), r.getBottom()));
        List<T> rv = new ArrayList<T>();

        for (T ir: intersection) {
            if (r.contains(ir)) {
                rv.add(ir);
            }
        }

        Utils.sort(rv, Rectangle.ILL_DEFINED_ORDER);
        return rv;
    }
    
    public List<T> intersects(Rectangle r) {
      return si.query(new Envelope(r.getLeft(), r.getRight(), r.getTop(), r.getBottom()));
    }
    
    /**
     * Minimum bounding box of all the Rectangles contained on this RectangleSpatialIndex
     * 
     * @return a Rectangle
     */
    public Rectangle getBounds() {
        return Rectangle.boundingBoxOf(rectangles);
    }

}
