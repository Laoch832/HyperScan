package com.quark.quarkit;

import android.graphics.Bitmap;
import android.graphics.Point;


/** Original JNI ABI retained for bundled QuarKit; no cloud task or downloader. */

public class QuarKitUtil {
    public static native boolean CheckPaitiMatchResultNative(Bitmap bitmap, Bitmap bitmap2, float f);

    public static native Bitmap GetCardDewarpResultNative(Bitmap bitmap, float[] fArr, float f);

    public static native long GetImageBinarizationWorkerNative(String str);

    public static native Bitmap GetImageBinarizationWorkerResultNative(Bitmap bitmap, long j, int i);

    public static native long GetImageBrighteningWorkerNative(String str);

    public static native Bitmap GetImageBrighteningWorkerResultNative(Bitmap bitmap, long j, int i);

    public static native long GetImageEnhancementWorkerNative(String str, String str2);

    public static native Bitmap GetImageEnhancementWorkerResultNative(Bitmap bitmap, long j, int i);

    public static native long GetImageFilterClassWorkerNative(String str);

    public static native int GetImageFilterClassWorkerResultNative(Bitmap bitmap, long j);

    public static native Bitmap GetImageGrayScalingResultNative(Bitmap bitmap, int i);

    public static native <T> Object GetMattingPostProcessImageResultNative(Bitmap bitmap, Class<T> cls);

    public static native Bitmap GetMattingPostProcessResultNative(Bitmap bitmap, boolean z);

    public static native QuarKitRetData GetNewCropInfoNative(int i, int i2, float[] fArr, float[] fArr2);

    public static native <T> Object GetPortraitEnhanceImageResultNative(byte[] bArr, Class<T> cls);

    public static native Bitmap GetScreenDewarpResultNative(Bitmap bitmap, float[] fArr);

    public static native int ReleaseBinarizationWorkerNative(long j);

    public static native int ReleaseBrighteningWorkerNative(long j);

    public static native int ReleaseEnhancementWorkerNative(long j);

    public static native int ReleaseImageFilterClassWorkerNative(long j);

    public static native double[] calculatePerspectiveTransformPoints(Point[] pointArr, Point[] pointArr2);

    public static native double[] calculatePerspectiveTransformWH(Point[] pointArr, int i, int i2, float f);

    public static native int[] calculateWHRatio(Point[] pointArr, int i, int i2, float f);

    public static native int[] calculateWHRatioNew(Point[] pointArr, int i, int i2);

    public static native void cropNative(Bitmap bitmap, Point[] pointArr, Bitmap bitmap2);

    private static native <T> Object dbScanNative(float[][] fArr, float f, int i, Class<T> cls);

    public static native double getImageBrightDarkScoreNative(byte[] bArr, int i, int i2, int i3);

    public static native double getImageSimilarityNative(byte[] bArr, int i, int i2, byte[] bArr2, int i3, int i4, int i5);

    public static native Bitmap imagePerspective(Bitmap bitmap, Point[] pointArr);

    public static native Bitmap pageAffineProcess(Bitmap bitmap, Point[] pointArr, float f);

    public static native Bitmap pageCorrect(Bitmap bitmap, Point[] pointArr, boolean z, float f, boolean z2);

    public static native Bitmap pageDewarpProcess(Bitmap bitmap);
}
