package app.hyperscan.android;

import android.Manifest;
import android.content.Intent;
import android.graphics.*;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import androidx.exifinterface.media.ExifInterface;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import java.io.*;
import java.util.regex.Pattern;

@CapacitorPlugin(name="ScanCamera", permissions={@Permission(alias="camera",strings={Manifest.permission.CAMERA})})
public class ScanCameraPlugin extends Plugin {
    private File session;
    private boolean opening;
    @Override public void load() {
        File[] old=getContext().getCacheDir().listFiles();
        if(old!=null) for(File dir:old) if(Pattern.matches("scan-camera-[0-9a-f-]{36}",dir.getName()) && System.currentTimeMillis()-dir.lastModified()>86400000L) {
            File[] photos=dir.listFiles(); if(photos!=null) for(File photo:photos) photo.delete(); dir.delete();
        }
    }
    @PluginMethod public void open(PluginCall call) {
        if (opening || session != null) { call.reject("相机任务尚未结束"); return; }
        opening = true;
        if (getPermissionState("camera") != PermissionState.GRANTED) requestPermissionForAlias("camera",call,"permissionResult"); else launch(call);
    }
    @PermissionCallback private void permissionResult(PluginCall call) {
        if (getPermissionState("camera") != PermissionState.GRANTED) { opening=false; call.reject("请在系统设置中允许 HyperScan 使用相机，也可导入图片。"); return; } launch(call);
    }
    private void launch(PluginCall call) {
        try { startActivityForResult(call,new Intent(getContext(),ScanCameraActivity.class),"cameraResult"); }
        catch (Exception e) { opening=false; call.reject("无法打开应用内相机",e); }
    }
    @ActivityCallback private void cameraResult(PluginCall call,ActivityResult result) {
        opening=false; if (call==null) return;
        Intent data=result.getData();
        String name=data==null ? null : data.getStringExtra("session");
        if (name!=null && Pattern.matches("scan-camera-[0-9a-f-]{36}",name)) session=new File(getContext().getCacheDir(),name);
        String error=data==null ? null : data.getStringExtra("error");
        if(error!=null) { cleanup(); call.reject(error); return; }
        int count=data==null ? 0 : data.getIntExtra("count",0);
        JSObject out=new JSObject(); out.put("count",Math.max(0,Math.min(20,count))); call.resolve(out);
    }
    @PluginMethod public void read(PluginCall call) {
        Integer index=call.getInt("index"); File active=session;
        if(active==null || index==null || index<0 || index>=20) { call.reject("没有可读取的相机照片"); return; }
        new Thread(() -> {
            Bitmap image=null, rotated=null;
            try {
                File file=new File(active,"page-"+index+".jpg");
                BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true; BitmapFactory.decodeFile(file.getPath(),opts);
                if(opts.outWidth<=0 || opts.outHeight<=0) throw new IOException("Invalid photo");
                opts.inJustDecodeBounds=false; opts.inSampleSize=1; while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>4800) opts.inSampleSize*=2;
                image=BitmapFactory.decodeFile(file.getPath(),opts); if(image==null) throw new IOException("Cannot decode photo");
                ExifInterface exif=new ExifInterface(file); Matrix transform=new Matrix();
                if(exif.isFlipped()) transform.postScale(-1,1); transform.postRotate(exif.getRotationDegrees());
                float scale=Math.min(1f,2400f/Math.max(image.getWidth(),image.getHeight())); transform.postScale(scale,scale);
                rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),transform,true);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(); if(!rotated.compress(Bitmap.CompressFormat.JPEG,92,bytes)) throw new IOException("JPEG failed");
                JSObject out=new JSObject(); out.put("image","data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)); call.resolve(out);
            } catch(Exception e) { android.util.Log.e("HyperScanCamera","Cannot read captured photo",e); call.reject("照片处理失败，已添加的页面仍保留。",e); }
            finally { if(rotated!=null && rotated!=image) rotated.recycle(); if(image!=null) image.recycle(); }
        },"hyperscan-camera-photo").start();
    }
    @PluginMethod public void release(PluginCall call) { cleanup(); call.resolve(); }
    private void cleanup() { if(session!=null) { File[] files=session.listFiles(); if(files!=null) for(File f:files) f.delete(); session.delete(); session=null; } }
    @Override protected void handleOnDestroy() { cleanup(); super.handleOnDestroy(); }
}
