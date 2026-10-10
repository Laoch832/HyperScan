import org.hyperscan.diagnostics.LogSummary;
class LogSummaryCheck {
    public static void main(String[] args) {
        String a=LogSummary.summarize("HTTP/1.1 403 https://api.quark.cn/private?token=SECRET password=SECRET");
        if (!a.contains("403") || !a.contains("api.quark.cn") || a.contains("SECRET") || a.contains("private")) throw new AssertionError(a);
        String b=LogSummary.summarize("verify failed access_token=SECRET https://SECRET:SECRET@api.quark.cn/");
        if (!b.startsWith("VERIFY") || b.contains("SECRET")) throw new AssertionError(b);
        if (!LogSummary.summarize("javax.net.ssl.SSLHandshakeException password=SECRET").startsWith("TLS_VERIFY")) throw new AssertionError("TLS category");
        if (!LogSummary.summarize("java.lang.VerifyError").startsWith("DEX_VERIFY")) throw new AssertionError("DEX category");
        if (!LogSummary.summarize("java.net.UnknownHostException").startsWith("DNS")) throw new AssertionError("DNS category");
        if (LogSummary.summarize("unrelated application event SECRET") != null) throw new AssertionError("unrelated raw log");
        System.out.println("HTTP/TLS/DNS/verify classification and secret/payload exclusion passed.");
    }
}
