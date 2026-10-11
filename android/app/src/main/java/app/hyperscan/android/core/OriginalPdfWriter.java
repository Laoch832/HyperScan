package app.hyperscan.android.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Migrates PicturesPDFExportAndroidImp's local drawing path:
 * per-image orientation, fractional padding, Matrix.ScaleToFit.CENTER, PdfDocument.
 * CMS, statistics, membership, service locator and remote preview are removed.
 * Adaptation: Laoch832. Original component attribution retained in CORE-MIGRATION.md.
 */
public final class OriginalPdfWriter {
    public static void write(List<File> images, OutputStream output, String paper,
                             String orientation, double margin) throws IOException {
        if (images.isEmpty()) throw new IOException("No pages");
        if (!Double.isFinite(margin) || margin < 0 || margin > 0.2)
            throw new IOException("Invalid margin");
        int[] size = switch (paper) {
            case "a4" -> new int[]{595, 842};
            case "a5" -> new int[]{420, 595};
            case "letter" -> new int[]{612, 792};
            default -> throw new IOException("Unsupported paper");
        };
        if (!orientation.equals("portrait") && !orientation.equals("landscape") && !orientation.equals("auto"))
            throw new IOException("Unsupported orientation");
        PdfDocument pdf = new PdfDocument();
        try {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            int number = 0;
            for (File image : images) {
                Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());
                if (bitmap == null) throw new IOException("Page " + (number + 1) + " cannot be decoded");
                try {
                    boolean landscape = orientation.equals("landscape") ||
                        (orientation.equals("auto") && bitmap.getWidth() > bitmap.getHeight());
                    int width = landscape ? size[1] : size[0], height = landscape ? size[0] : size[1];
                    RectF area = new RectF((int)(width * margin), (int)(height * margin),
                        width - (int)(width * margin), height - (int)(height * margin));
                    Matrix matrix = new Matrix();
                    matrix.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), area, Matrix.ScaleToFit.CENTER);
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(width, height, ++number).create());
                    try { page.getCanvas().drawBitmap(bitmap, matrix, paint); }
                    finally { pdf.finishPage(page); }
                } finally { bitmap.recycle(); }
            }
            // Fail the entire export on a bad page instead of silently dropping pages.
            pdf.writeTo(output);
        } finally { pdf.close(); }
    }
    private OriginalPdfWriter() {}
}
