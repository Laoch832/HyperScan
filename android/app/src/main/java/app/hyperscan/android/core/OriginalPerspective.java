package app.hyperscan.android.core;

import android.graphics.Bitmap;
import android.graphics.Point;
import com.quark.quarkit.QuarKitUtil;

/** Calls the original JsApiImageEdgeDetector.j model-free QuarKit branch. */
public final class OriginalPerspective {
    private static boolean loaded;
    private static synchronized void load() {
        if (loaded) return;
        for (String name : new String[]{"c++_shared", "MNN", "tbb", "opencv_java3", "upipe_jni", "quarkit"}) {
            System.loadLibrary(name);
        }
        loaded = true;
    }
    public static synchronized Bitmap correct(Bitmap input, double[] corners) {
        if (corners.length != 8) throw new IllegalArgumentException("Four corners required");
        Point[] points = new Point[4];
        for (int i = 0; i < 4; i++) {
            double x = corners[2 * i], y = corners[2 * i + 1];
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
                throw new IllegalArgumentException("Invalid corner");
            points[i] = new Point((int) (x * input.getWidth()), (int) (y * input.getHeight()));
        }
        // Clockwise convex quadrilateral, matching TL/TR/BR/BL original point order.
        for (int i = 0; i < 4; i++) {
            Point a = points[i], b = points[(i + 1) % 4], c = points[(i + 2) % 4];
            long cross = (long)(b.x - a.x) * (c.y - b.y) - (long)(b.y - a.y) * (c.x - b.x);
            if (cross < input.getWidth() * (double)input.getHeight() * 0.001)
                throw new IllegalArgumentException("Corners must form a sufficiently large clockwise convex document");
        }
        load();
        Bitmap result = QuarKitUtil.imagePerspective(input, points);
        if (result == null || result.getWidth() <= 0 || result.getHeight() <= 0)
            throw new IllegalStateException("Original perspective returned no image");
        return result;
    }
    private OriginalPerspective() {}
}
