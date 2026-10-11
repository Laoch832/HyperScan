package com.quark.quarkit.mediascan;

import android.content.res.AssetManager;

/** Original startup registration ABI. No scanner graphs or file traversal are exposed. */
public final class MediaScanner {
    private static native synchronized int setupScanner_(byte[] config, AssetManager assets);
    private native void start_(int id);
    private native void addSearchPath_(int id, String path);
    private native void addScanFiles_(int id, byte[] files);
    private native void setCallback_(int id, Object callback);
    private native void stop_(int id);
    private native void releaseScanner_(int id);
    private MediaScanner() {}
}
