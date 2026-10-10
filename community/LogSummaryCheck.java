import org.hyperscan.diagnostics.LogSummary;
class LogSummaryCheck {
    static void contains(String value,String expected) { if(value==null || !value.contains(expected))throw new AssertionError(value+" lacks "+expected); }
    static void excludes(String value,String secret) { if(value.contains(secret))throw new AssertionError("Leaked "+secret+": "+value); }
    public static void main(String[] args) {
        String a=LogSummary.summarize("10-11 01:02:03.456 123 456 E PdfExport: java.net.SocketTimeoutException: read timed out https://api.quark.cn/pdf/convert?token=SECRET password=SECRET");
        for(String part:new String[]{"PdfExport","SocketTimeoutException","123 456","api.quark.cn/pdf/convert"})contains(a,part);
        excludes(a,"SECRET"); contains(a,"QUERY_REDACTED");
        String b=LogSummary.summarize("E/Net: verify failed access_token=SECRET https://SECRET:SECRET@api.quark.cn/pdf/convert");
        contains(b,"verify failed");excludes(b,"SECRET");
        String c=LogSummary.summarize("E/TLS: javax.net.ssl.SSLHandshakeException: Chain validation failed");contains(c,"Chain validation failed");
        contains(LogSummary.summarize("E/App: java.lang.VerifyError: bad register v2"),"bad register v2");
        contains(LogSummary.summarize("E/PDF: at com.ucpro.pdf.Loader.load(Loader.java:42)"),"Loader.java:42");
        if(LogSummary.summarize("D/Config: connectTimeout=3000 readTimeout=3000")!=null)throw new AssertionError("Config falsely captured as failure");
        String d=LogSummary.summarize("E/Net: response body={\"code\":401,\"token\":\"SECRET\",\"document\":\"PRIVATE\"}");contains(d,"401");excludes(d,"SECRET");excludes(d,"PRIVATE");
        String e=LogSummary.redact("SSL failed Authorization: Bearer SECRET Cookie: SESSION");excludes(e,"SECRET");excludes(e,"SESSION");
        String f=LogSummary.redact("failed file=/storage/emulated/0/private.pdf user@email.com phone=13800138000");excludes(f,"private.pdf");excludes(f,"user@email.com");excludes(f,"13800138000");
        excludes(LogSummary.redact("E/Net: response body=PRIVATE"),"PRIVATE");
        System.out.println("Actual error text, tag/thread/endpoint and stack retention; credential/body redaction; config false-positive checks passed.");
    }
}
