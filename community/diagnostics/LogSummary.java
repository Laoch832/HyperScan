package org.hyperscan.diagnostics;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Laoch832: preserve diagnostic evidence; redact credentials, bodies and private paths. */
public final class LogSummary {
    private static final Pattern URL = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER = Pattern.compile("(?i)\\b(?:authorization|proxy-authorization|cookie|set-cookie)\\s*[:=].*");
    private static final Pattern SECRET = Pattern.compile("(?i)(\\b(?:[a-z0-9_-]*token|password|passwd|secret|api[_-]?key|appkey|credential|signature|sign|session(?:id)?|sid|uid|user[_-]?id|username|email|phone|mobile)\\b[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}]+)");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?\\b");
    private static final Pattern LONG_VALUE = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9_+/=-]{64,}(?![A-Za-z0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PATH = Pattern.compile("(?:/storage/|/sdcard/|/data/user/|/data/data/)[^\\s\\\"']+");
    private static final Pattern PRIVATE_ID = Pattern.compile("\\b(?:1[3-9][0-9]{9}|[0-9]{17}[0-9Xx])\\b");
    private static final Pattern CODE = Pattern.compile("(?i)(?:error[_-]?code|errcode|status|code)[\\\"']?\\s*[:=]\\s*[\\\"']?(-?[0-9]{1,6})");
    private static final Pattern STACK = Pattern.compile("(?:^|\\s)(?:at [A-Za-z_$]|Caused by:|Suppressed:|\\.\\.\\. [0-9]+ more)");

    public static String redact(String raw) {
        String value = HEADER.matcher(raw).replaceAll("[AUTH_HEADER_REDACTED]");
        value = SECRET.matcher(value).replaceAll("$1[REDACTED]");
        value = JWT.matcher(value).replaceAll("[JWT_REDACTED]");
        Matcher urls = URL.matcher(value);
        StringBuffer safe = new StringBuffer();
        while (urls.find()) {
            String replacement = "[URL_REDACTED]";
            try {
                java.net.URI uri = new java.net.URI(urls.group());
                String path = uri.getRawPath();
                if (path == null) path = "";
                // Keep endpoint structure, replacing identifier-like path segments.
                path = path.replaceAll("/(?:(?:[0-9]{4,})|(?:[A-Fa-f0-9-]{20,})|(?:[A-Za-z0-9_=-]{40,}))(?=/|$)", "/[ID]");
                if (uri.getHost() != null) replacement = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + path + (uri.getRawQuery() == null ? "" : "?[QUERY_REDACTED]");
            } catch (Exception ignored) {}
            urls.appendReplacement(safe, Matcher.quoteReplacement(replacement));
        }
        urls.appendTail(safe); value = safe.toString();
        value = EMAIL.matcher(value).replaceAll("[EMAIL_REDACTED]");
        value = PRIVATE_ID.matcher(value).replaceAll("[PRIVATE_ID_REDACTED]");
        value = PATH.matcher(value).replaceAll("[LOCAL_PATH_REDACTED]");
        value = LONG_VALUE.matcher(value).replaceAll("[LONG_VALUE_REDACTED]");
        // Never show JSON / request or response body text. Preserve numeric business codes separately.
        String lower = value.toLowerCase(Locale.ROOT);
        boolean body = lower.contains("request body") || lower.contains("response body") || lower.contains("payload=") || lower.contains("payload:") || lower.contains("body=") || lower.contains("body:") || (value.contains("{") && value.contains(":"));
        if (body) {
            Matcher code = CODE.matcher(value);
            String codes = "";
            int found = 0;
            while (code.find() && found++ < 4) codes += " code=" + code.group(1);
            int start = value.indexOf('{');
            Matcher bodyMarker = Pattern.compile("(?i)(?:request\\s+body|response\\s+body|payload\\s*[:=]|\\bbody\\s*[:=])").matcher(value);
            if (bodyMarker.find()) start = start < 0 ? bodyMarker.start() : Math.min(start, bodyMarker.start());
            if (start < 0) start = 0;
            value = value.substring(0, Math.min(start, 180)) + " [BODY_REDACTED]" + codes;
        }
        return value.length() > 1600 ? value.substring(0, 1600) + " [TRUNCATED]" : value;
    }

    public static String summarize(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        // Preserve real W/E lines and stack traces. A config mentioning timeout is not a timeout failure.
        boolean priority = raw.matches(".*\\s[WEF]\\s+[^ :]+\\s*:.*") || raw.matches("^[WEF]/.*");
        boolean stack = STACK.matcher(raw).find();
        boolean evidence = lower.contains("exception") || lower.contains("verifyerror") || lower.contains("failed") || lower.contains("failure") || lower.contains("error") || lower.contains("timed out") || lower.contains("unauthorized") || lower.contains("forbidden") || lower.contains("网络异常") || lower.contains("加载失败");
        boolean context = lower.contains("pdf") || lower.contains("verify") || lower.contains("ssl") || lower.contains("request") || lower.contains("response") || lower.contains("http/") || lower.contains("openacs") || lower.contains("chromium") || lower.contains("flutter");
        if (!(priority || stack || evidence || context)) return null;
        return redact(raw);
    }
}
