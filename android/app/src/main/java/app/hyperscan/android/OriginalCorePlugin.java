package app.hyperscan.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import androidx.activity.result.ActivityResult;
import app.hyperscan.android.core.OriginalPdfWriter;
import app.hyperscan.android.core.OriginalPerspective;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;

/** Laoch832's local bridge. No token, account, STS, CMS, upload or remote preview. */
@CapacitorPlugin(name = "OriginalCore")
public final class OriginalCorePlugin extends Plugin {
    private final Map<String, File> exports = new ConcurrentHashMap<>();

    @PluginMethod public void capabilities(PluginCall call) {
        JSObject result = new JSObject();
        result.put("perspective", Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"));
        result.put("pdf", true);
        result.put("automaticEdgeDetection", false);
        result.put("originalOcr", false);
        call.resolve(result);
    }

    private static byte[] imageBytes(String data) throws Exception {
        if (data == null || data.length() > 12 * 1024 * 1024 || !data.startsWith("data:image/jpeg;base64,"))
            throw new IllegalArgumentException("Invalid image data");
        byte[] bytes = Base64.decode(data.substring(data.indexOf(',') + 1), Base64.DEFAULT);
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 2400 || bounds.outHeight > 2400)
            throw new IllegalArgumentException("Image dimensions must be within 2400 pixels");
        return bytes;
    }

    @PluginMethod public void perspective(PluginCall call) {
        new Thread(() -> {
            Bitmap input = null, result = null;
            try {
                if (!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"))
                    throw new UnsupportedOperationException("Original core requires arm64");
                JSArray array = call.getArray("corners");
                if (array == null || array.length() != 8) throw new IllegalArgumentException("Invalid corners");
                double[] corners = new double[8];
                for (int i = 0; i < 8; i++) corners[i] = array.getDouble(i);
                byte[] bytes = imageBytes(call.getString("image"));
                input = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                result = OriginalPerspective.correct(input, corners);
                try (ByteArrayOutputStream encoded = new ByteArrayOutputStream()) {
                    if (!result.compress(Bitmap.CompressFormat.JPEG, 92, encoded))
                        throw new IllegalStateException("Encoding failed");
                    JSObject response = new JSObject();
                    response.put("image", "data:image/jpeg;base64," + Base64.encodeToString(encoded.toByteArray(), Base64.NO_WRAP));
                    response.put("width", result.getWidth()); response.put("height", result.getHeight());
                    response.put("engine", "original-quarkit-imagePerspective");
                    Log.i("HyperScanCore", "PERSPECTIVE_OK width=" + result.getWidth() + " height=" + result.getHeight());
                    call.resolve(response);
                }
            } catch (Exception | LinkageError error) {
                Log.e("HyperScanCore", "PERSPECTIVE_FAILED", error);
                call.reject("原生校正失败（" + error.getClass().getSimpleName() + "）。请保留原图并反馈诊断日志。", error instanceof Exception ? (Exception) error : null);
            } finally {
                if (result != null && result != input) result.recycle();
                if (input != null) input.recycle();
            }
        }, "hyperscan-original-perspective").start();
    }

    @PluginMethod public void savePdf(PluginCall call) {
        new Thread(() -> {
            File directory = null;
            try {
                JSArray pages = call.getArray("pages");
                if (pages == null || pages.length() < 1 || pages.length() > 100)
                    throw new IllegalArgumentException("PDF requires 1–100 pages");
                directory = new File(getContext().getCacheDir(), "original-pdf-" + UUID.randomUUID());
                if (!directory.mkdir()) throw new java.io.IOException("Cannot create PDF workspace");
                ArrayList<File> images = new ArrayList<>();
                long total = 0;
                for (int i = 0; i < pages.length(); i++) {
                    byte[] bytes = imageBytes(pages.getString(i));
                    total += bytes.length;
                    if (total > 48L * 1024 * 1024) throw new IllegalArgumentException("Document too large; split it first");
                    File image = new File(directory, i + ".jpg");
                    try (FileOutputStream out = new FileOutputStream(image)) { out.write(bytes); }
                    images.add(image);
                }
                File pdf = new File(directory, "document.pdf");
                try (FileOutputStream output = new FileOutputStream(pdf)) {
                    OriginalPdfWriter.write(images, output, call.getString("paper", "a4"),
                        call.getString("orientation", "auto"), call.getDouble("margin", 0.03));
                }
                for (File image : images) image.delete();
                exports.put(call.getCallbackId(), pdf);
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/pdf");
                intent.putExtra(Intent.EXTRA_TITLE, call.getString("filename", "HyperScan.pdf").replaceAll("[\\\\/:*?\"<>|]", "_"));
                getActivity().runOnUiThread(() -> {
                    try { startActivityForResult(call, intent, "pdfDestination"); }
                    catch (Exception error) {
                        deleteExport(exports.remove(call.getCallbackId()));
                        call.reject("无法打开系统保存面板。", error);
                    }
                });
            } catch (Exception error) {
                if (directory != null) deleteDirectory(directory);
                Log.e("HyperScanCore", "PDF_BUILD_FAILED", error);
                call.reject("本地 PDF 生成失败（" + error.getClass().getSimpleName() + "）。", error);
            }
        }, "hyperscan-original-pdf").start();
    }

    @ActivityCallback private void pdfDestination(PluginCall call, ActivityResult result) {
        if (call == null) return;
        File pdf = exports.remove(call.getCallbackId());
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            deleteExport(pdf);
            JSObject response = new JSObject(); response.put("cancelled", true); call.resolve(response); return;
        }
        Uri uri = result.getData().getData();
        if (pdf == null || uri == null) { deleteExport(pdf); call.reject("导出任务已失效，请重新生成 PDF。"); return; }
        new Thread(() -> {
            try (OutputStream output = getContext().getContentResolver().openOutputStream(uri, "wt");
                 FileInputStream input = new FileInputStream(pdf)) {
                if (output == null) throw new java.io.IOException("No output stream");
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush();
                JSObject response = new JSObject(); response.put("cancelled", false);
                response.put("engine", "original-android-pdf-path"); call.resolve(response);
                Log.i("HyperScanCore", "PDF_EXPORT_OK");
            } catch (Exception error) {
                call.reject("保存 PDF 失败，请检查目标位置。", error);
            } finally { deleteExport(pdf); }
        }, "hyperscan-original-pdf-save").start();
    }
    private static void deleteExport(File pdf) { if (pdf != null) deleteDirectory(pdf.getParentFile()); }
    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
        dir.delete();
    }
    @Override protected void handleOnDestroy() {
        for (File pdf : exports.values()) deleteExport(pdf);
        exports.clear();
        super.handleOnDestroy();
    }
}
