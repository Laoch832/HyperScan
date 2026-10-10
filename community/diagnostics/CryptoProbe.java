package org.hyperscan.diagnostics;

/** Laoch832: observe outcomes only; never log decrypted credentials or exception messages. */
public final class CryptoProbe {
    public static String stsSummary(String id, String secret, String credential) {
        return "STS_RESULT response_code=0 accessId_present=" + present(id)
            + " accessSecret_present=" + present(secret) + " credential_present=" + present(credential);
    }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static void emit(String value) {
        try { android.util.Log.w("HyperScanCrypto", value); } catch (Throwable ignored) {}
    }
    public static void sts(String id, String secret, String credential) {
        try { emit(stsSummary(id, secret, credential)); } catch (Throwable ignored) {}
    }
    public static void decryptError(Throwable error, int code) {
        try { emit("CRYPTO_DECRYPT_EXCEPTION sdk_code=" + code + " exception_type=" + (error == null ? "null" : error.getClass().getName())); } catch (Throwable ignored) {}
    }
    public static void caught(Throwable error) { decryptError(error, -1); }
    public static void initError(Throwable error, int code) {
        try { emit("CRYPTO_INIT_EXCEPTION sdk_code=" + code + " exception_type=" + (error == null ? "null" : error.getClass().getName())); } catch (Throwable ignored) {}
    }
}
