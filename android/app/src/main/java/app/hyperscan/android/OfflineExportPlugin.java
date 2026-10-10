package app.hyperscan.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;

/** Laoch832: save locally with Android's document picker; no server or broad storage access. */
@CapacitorPlugin(name = "OfflineExport")
public class OfflineExportPlugin extends Plugin {
    @PluginMethod
    public void save(PluginCall call) {
        String name = call.getString("filename", "HyperScan.pdf");
        String mime = call.getString("mime", "application/pdf");
        String encoded = call.getString("data");
        if (encoded == null || encoded.length() > 48 * 1024 * 1024) {
            call.reject("导出文件过大，请拆分文档后重试。"); return;
        }
        if (!mime.equals("application/pdf") && !mime.equals("text/plain")) {
            call.reject("不支持的导出格式。"); return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try { startActivityForResult(call, intent, "documentSelected"); }
        catch (Exception error) { call.reject("无法打开系统保存面板。", error); }
    }

    @ActivityCallback
    private void documentSelected(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            JSObject response = new JSObject(); response.put("cancelled", true); call.resolve(response); return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) { call.reject("系统没有返回保存位置。"); return; }
        new Thread(() -> {
            try (OutputStream output = getContext().getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("No output stream");
                byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
                output.write(bytes); output.flush();
                JSObject response = new JSObject(); response.put("cancelled", false); call.resolve(response);
            } catch (Exception error) { call.reject("保存失败，请检查目标位置是否可写。", error); }
        }, "hyperscan-local-export").start();
    }
}
