package com.taobao.android.alinnkit.alinn;

import android.graphics.Bitmap;

public class AliNNNetNative {
    public static native int nativeBufferToBitmap(byte[] bArr, int i, int i2, int i3, Bitmap bitmap, int i4, int i5, float[] fArr);

    public static native boolean nativeConvertBitmapToTensor(Bitmap bitmap, long j, int i, int i2, int i3, float[] fArr, float[] fArr2, float[] fArr3);

    public static native boolean nativeConvertBufferToTensor(byte[] bArr, int i, int i2, long j, int i3, int i4, int i5, int i6, float[] fArr, float[] fArr2, float[] fArr3);

    public static native long nativeCreateImageTensor(int i, int i2, int i3);

    public static native long nativeCreateNetFromFile(String str);

    public static native long nativeCreateSession(long j, int i, int i2, int i3, int i4, int i5, String[] strArr);

    public static native long nativeCreateSessionWithFlag(long j, int i, int i2, int i3, int i4, int i5, String[] strArr, boolean[] zArr);

    public static native boolean nativeDeviceSupportNPU();

    public static native long nativeGetSessionInput(long j, long j2, String str);

    public static native long nativeGetSessionOutput(long j, long j2, String str);

    public static native String nativeNetBizCode(long j);

    public static native long nativeReleaseNet(long j);

    public static native void nativeReleaseSession(long j, long j2);

    public static native long nativeReleaseTensor(long j);

    public static native int nativeReshapeSession(long j, long j2);

    public static native void nativeReshapeTensor(long j, long j2, int[] iArr);

    public static native int nativeRunSession(long j, long j2);

    public static native int nativeRunSessionWithCallback(long j, long j2, String[] strArr, long[] jArr);

    public static native void nativeSetInputFloatData(long j, long j2, float[] fArr);

    public static native void nativeSetInputIntData(long j, long j2, int[] iArr);

    public static native int nativeTensorGetData(long j, float[] fArr);

    public static native int nativeTensorGetDimensionType(long j);

    public static native int[] nativeTensorGetDimensions(long j);

    public static native int nativeTensorGetIntData(long j, int[] iArr);

    public static native int nativeTensorGetUINT8Data(long j, byte[] bArr);
}
