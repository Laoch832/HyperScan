package app.hyperscan.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import app.hyperscan.android.core.DocumentDetector;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.nio.ByteBuffer;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.UUID;

/** In-app CameraX live viewfinder. Composition guidance is not automatic edge detection. */
public class ScanCameraActivity extends AppCompatActivity {
    private PreviewView preview;
    private DocumentOverlay overlay;
    private final ExecutorService analysisWorker=Executors.newSingleThreadExecutor();
    private DocumentDetector detector;
    private boolean automatic, autoArmed=true;
    private double[] lastCorners;
    private int stableFrames;
    private long lastAnalysis,lastShot;
    private String edgeStatus="正在找边…";
    private Button autoButton;
    private TextView status;
    private Button shutter, done, light;
    private ImageCapture capture;
    private Camera camera;
    private ProcessCameraProvider provider;
    private File directory;
    private int count;
    private boolean capturing, torch, ready;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        directory = saved == null ? new File(getCacheDir(), "scan-camera-" + UUID.randomUUID()) : new File(getCacheDir(), saved.getString("session", "invalid"));
        if (!directory.exists() && !directory.mkdirs()) { fail("无法创建本地照片目录"); return; }
        if (saved != null) count = saved.getInt("count");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(20,17,24));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(12),dp(8),dp(12),dp(8));
        Button back = button("返回", false); back.setOnClickListener(v -> finishSession()); top.addView(back);
        TextView title = new TextView(this); title.setText("HyperScan · 扫描"); title.setTextColor(Color.WHITE); title.setTextSize(20); title.setTypeface(null,Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0,-2,1)); light = button("补光", false); light.setEnabled(false);
        light.setOnClickListener(v -> { if (camera != null) { boolean target = !torch; camera.getCameraControl().enableTorch(target).addListener(() -> {
            torch = camera.getCameraInfo().getTorchState().getValue() != null && camera.getCameraInfo().getTorchState().getValue() == TorchState.ON;
            light.setText(torch ? "关闭补光" : "补光");
        }, ContextCompat.getMainExecutor(this)); } }); top.addView(light); root.addView(top);
        preview = new PreviewView(this); preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE); preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        FrameLayout viewfinder=new FrameLayout(this);viewfinder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        overlay=new DocumentOverlay(this);viewfinder.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        root.addView(viewfinder,new LinearLayout.LayoutParams(-1,0,1));
        autoButton=button("自动拍摄：关",false);autoButton.setOnClickListener(v->{automatic=!automatic;autoArmed=true;stableFrames=0;autoButton.setText(automatic?"自动拍摄：开":"自动拍摄：关");});root.addView(autoButton);
        detector=new DocumentDetector(this);
        preview.setOnTouchListener((v,e) -> { if (e.getAction() == android.view.MotionEvent.ACTION_UP && camera != null) {
            camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(preview.getMeteringPointFactory().createPoint(e.getX(),e.getY())).build()); v.performClick();
        } return true; });
        status = new TextView(this); status.setTextColor(Color.rgb(230,221,248)); status.setTextSize(15); status.setGravity(Gravity.CENTER); status.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(status);
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER); controls.setPadding(dp(16),dp(8),dp(16),dp(24));
        shutter = button("● 拍摄", true); shutter.setTextSize(22); shutter.setMinHeight(dp(72)); shutter.setOnClickListener(v -> takePhoto()); controls.addView(shutter,new LinearLayout.LayoutParams(0,dp(80),1));
        done = button("完成", false); done.setOnClickListener(v -> finishSession()); controls.addView(done); root.addView(controls); setContentView(root); new androidx.core.view.WindowInsetsControllerCompat(getWindow(),root).setAppearanceLightStatusBars(false); new androidx.core.view.WindowInsetsControllerCompat(getWindow(),root).setAppearanceLightNavigationBars(false); update();
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { finishSession(); } });
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> { if (isFinishing() || isDestroyed()) return; try {
            provider = future.get(); if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) throw new IllegalStateException("没有可用的后置相机");
            Preview live = new Preview.Builder().build(); live.setSurfaceProvider(preview.getSurfaceProvider());
            capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
            ImageAnalysis analysis=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).setTargetResolution(new android.util.Size(640,480)).build();
            analysis.setAnalyzer(analysisWorker,this::analyze);
            UseCaseGroup.Builder group=new UseCaseGroup.Builder().addUseCase(live).addUseCase(capture).addUseCase(analysis);
            ViewPort viewport=preview.getViewPort();if(viewport!=null)group.setViewPort(viewport);
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group.build());
            preview.getPreviewStreamState().observe(this, state -> { ready = state == PreviewView.StreamState.STREAMING; update(); });
            light.setEnabled(camera.getCameraInfo().hasFlashUnit());
        } catch (Exception error) { android.util.Log.e("HyperScanCamera","Camera initialization failed",error); fail("无法打开相机，请检查相机权限或是否被其他应用占用"); }
        },ContextCompat.getMainExecutor(this));
    }
    private void takePhoto() {
        if (!ready || capturing || capture == null || count >= 20) return;
        capturing = true; autoArmed=false; lastShot=android.os.SystemClock.elapsedRealtime(); update(); capture.setTargetRotation(preview.getDisplay().getRotation());
        File photo = new File(directory, "page-" + count + ".jpg");
        capture.takePicture(new ImageCapture.OutputFileOptions.Builder(photo).build(), ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults result) { capturing = false; count++; update(); }
            @Override public void onError(ImageCaptureException error) { capturing = false; photo.delete(); update(); status.setText("拍摄失败，请重试；已拍页面仍保留"); android.util.Log.e("HyperScanCamera","Capture failed",error); }
        });
    }
    private void update() { shutter.setEnabled(ready && !capturing && count < 20); done.setEnabled(!capturing); done.setText("完成 · " + count + " 页"); status.setText(capturing ? "正在保存照片…" : !ready ? "正在启动实时取景…" : edgeStatus + " · 已拍 " + count + " 页" + (count == 20 ? "，请完成后继续添加" : " · 可连续拍摄")); }
    private void finishSession() { if (capturing) return; Intent data = new Intent(); data.putExtra("session",directory.getName()); data.putExtra("count",count); setResult(Activity.RESULT_OK,data); finish(); }
    private void fail(String message) { Intent data = new Intent(); data.putExtra("error",message); if (directory != null) data.putExtra("session",directory.getName()); setResult(Activity.RESULT_CANCELED,data); finish(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("session",directory.getName()); out.putInt("count",count); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { if (provider != null) provider.unbindAll(); analysisWorker.execute(()->{if(detector!=null)detector.close();});analysisWorker.shutdown(); super.onDestroy(); }
    private void analyze(ImageProxy image) {
        Bitmap bitmap=null,rotated=null;
        try {
            long now=android.os.SystemClock.elapsedRealtime();if(now-lastAnalysis<350)return;lastAnalysis=now;
            android.graphics.Rect crop=image.getCropRect();int w=crop.width(),h=crop.height();ImageProxy.PlaneProxy plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int[] pixels=new int[w*h];
            int row=plane.getRowStride(),pixel=plane.getPixelStride();for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(y+crop.top)*row+(x+crop.left)*pixel;int r=buffer.get(i)&255,g=buffer.get(i+1)&255,b=buffer.get(i+2)&255;pixels[y*w+x]=0xff000000|r<<16|g<<8|b;}
            bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);Matrix transform=new Matrix();transform.postRotate(image.getImageInfo().getRotationDegrees());rotated=Bitmap.createBitmap(bitmap,0,0,w,h,transform,true);
            DocumentDetector.Result result=detector.detect(rotated);double[] q=result.corners;int rw=rotated.getWidth(),rh=rotated.getHeight();
            runOnUiThread(()->{if(isFinishing() || isDestroyed())return;
                double movement=0;if(q!=null && lastCorners!=null)for(int i=0;i<8;i++)movement=Math.max(movement,Math.abs(q[i]-lastCorners[i]));
                stableFrames=q!=null && lastCorners!=null && movement<.018?stableFrames+1:0;
                if(q==null || movement>.08)autoArmed=true;
                lastCorners=q;boolean stable=stableFrames>=3;overlay.update(q,rw,rh,stable);
                edgeStatus=q==null?"未找到清晰纸张边缘 · 点击对焦":stable?"边缘稳定 · "+(automatic?autoArmed?"即将拍摄":"请更换下一页":"点击拍摄"):"已找到纸张 · 请保持稳定";
                if(q!=null)edgeStatus+=(result.engine.startsWith("original")?" · 原模型":" · 本地轮廓");
                update();if(automatic && autoArmed && stable && now-lastShot>2000 && ready && !capturing)takePhoto();
            });
        }catch(Exception|LinkageError error){android.util.Log.e("HyperScanEdges","LIVE_DETECTION_FAILED",error);runOnUiThread(()->{overlay.update(null,0,0,false);edgeStatus="找边失败，可手动拍摄";update();});}
        finally{if(rotated!=null && rotated!=bitmap)rotated.recycle();if(bitmap!=null)bitmap.recycle();image.close();}
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String label,boolean primary) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(primary ? Color.rgb(35,24,55) : Color.WHITE); GradientDrawable bg = new GradientDrawable(); bg.setColor(primary ? Color.rgb(213,190,255) : Color.rgb(48,42,57)); bg.setCornerRadius(dp(32)); b.setBackground(bg); b.setPadding(dp(18),dp(8),dp(18),dp(8)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2,-2); p.setMargins(dp(4),0,dp(4),0); b.setLayoutParams(p); return b; }
}
