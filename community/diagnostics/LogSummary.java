package org.hyperscan.diagnostics;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe event summaries by Laoch832; raw payloads are never returned. */
public final class LogSummary {
    private static final Pattern HOST = Pattern.compile("https?://([A-Za-z0-9.-]+\\.[A-Za-z]{2,63})(?::[0-9]+)?(?:/|\\s|$)");
    private static final Pattern STATUS = Pattern.compile("(?:http[/0-9. ]*|status(?:code)?\\s*[:= ]\\s*)([45][0-9]{2})", Pattern.CASE_INSENSITIVE);
    public static String summarize(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        String category = null;
        if (lower.contains("verifyerror")) category = "DEX_VERIFY — 字节码校验异常";
        else if (lower.contains("sslhandshake") || lower.contains("certpath") || lower.contains("certificate verify") || lower.contains("sslpeerunverified")) category = "TLS_VERIFY — TLS 握手或证书验证相关事件";
        else if (lower.contains("unknownhost") || lower.contains("dns fail") || lower.contains("name not resolved")) category = "DNS — 域名解析失败";
        else if (lower.contains("sockettimeout") || lower.contains("timed out") || lower.contains("timeout")) category = "TIMEOUT — 超时相关事件";
        else if (lower.contains("connectexception") || lower.contains("connection refused") || lower.contains("network is unreachable")) category = "CONNECT — 连接失败";
        else if (lower.contains("verify")) category = "VERIFY — 验证相关日志（提及不等于失败）";
        else if (lower.contains("unauthorized") || lower.contains("forbidden")) category = "AUTH — 授权拒绝相关事件";
        else if (lower.contains("network") && (lower.contains("fail") || lower.contains("error"))) category = "NETWORK — 网络错误相关事件";
        Matcher status = STATUS.matcher(raw);
        if (status.find()) category = "HTTP — 状态码 " + status.group(1) + (category == null ? "" : " / " + category);
        if (category == null) return null;
        Matcher host = HOST.matcher(raw);
        if (host.find()) category += " / host=" + host.group(1);
        return category;
    }

}
