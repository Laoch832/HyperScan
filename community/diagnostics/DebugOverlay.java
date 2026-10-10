package org.hyperscan.diagnostics;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Locale;

/** Internal diagnostics by Laoch832. No requests, bodies, account data or raw logs are exported. */
public final class DebugOverlay {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
    private static boolean started;
    private static String last = "";
    private static long lastAt;
    private static int count;
    private DebugOverlay() {}

    public static void install(Activity activity) {
        try {
            synchronized (DebugOverlay.class) {
                if (!started) {
                    started = true;
                    record("START — HyperScan 屏幕诊断 / Laoch832 / Android API " + android.os.Build.VERSION.SDK_INT);
                    record("R5：增加 STS 解密结果及安全组件异常码；保留本进程错误原文、标签、时间、线程及调用栈（脱敏）；不把 timeout 配置或 verify 提及直接判定为失败。");
                    activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                        public void onActivityCreated(Activity a, Bundle b) {}
                        public void onActivityStarted(Activity a) {}
                        public void onActivityResumed(Activity a) { record("ACTIVITY — " + a.getClass().getName()); schedule(a); }
                        public void onActivityPaused(Activity a) {}
                        public void onActivityStopped(Activity a) {}
                        public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                        public void onActivityDestroyed(Activity a) {}
                    });
                    final Context context = activity.getApplicationContext();
                    try {
                        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        cm.registerNetworkCallback(new android.net.NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
                            public void onAvailable(Network network) { record("NETWORK — 网络连接已可用；不代表业务接口可访问。"); }
                            public void onLost(Network network) { record("NETWORK — 网络连接已断开。"); }
                            public void onCapabilitiesChanged(Network network, NetworkCapabilities c) {
                                record("NETWORK — " + (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "移动网络" : "其他网络") + " / 系统互联网验证=" + c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                            }
                        });
                    } catch (Throwable error) { record("NETWORK_MONITOR — " + error.getClass().getSimpleName()); }
                    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
                    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                        record("UNCAUGHT — " + LogSummary.redact(error.toString()));
                        Throwable cause = error.getCause();
                        for (int depth = 0; cause != null && depth < 4; depth++, cause = cause.getCause()) record("CAUSED_BY — " + LogSummary.redact(cause.toString()));
                        for (int i = 0; i < Math.min(error.getStackTrace().length, 8); i++) {
                            StackTraceElement frame = error.getStackTrace()[i];
                            record("  " + frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
                        }
                        if (previous != null) previous.uncaughtException(thread, error);
                        else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
                    });
                    Thread reader = new Thread(() -> {
                        try {
                            Process process = new ProcessBuilder("/system/bin/logcat", "-v", "threadtime", "--pid=" + android.os.Process.myPid(), "-T", "1").redirectErrorStream(true).start();
                            try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                                record("LOGCAT — 本进程 threadtime 日志已启动：时间 / PID / TID / 级别 / 标签 / 脱敏原文。未输出的请求无法捕获。");
                                String line;
                                while ((line = input.readLine()) != null) {
                                    String summary = LogSummary.summarize(line);
                                    if (summary != null) record(summary);
                                }
                            }
                            record("LOGCAT — 读取已结束，退出码=" + process.waitFor());
                        } catch (Exception error) { record("LOGCAT_UNAVAILABLE — " + error.getClass().getSimpleName()); }
                    }, "hyperscan-diagnostics");
                    reader.setDaemon(true);
                    reader.start();
                }
            }
            schedule(activity);
        } catch (Throwable ignored) { /* Diagnostics must not stop the host application. */ }
    }


    private static synchronized void record(String message) {
        long now = System.currentTimeMillis();
        // Retain repeated failures and stack frames; do not collapse distinct events.
        last = message; lastAt = now; count++;
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new java.util.Date(now));
        EVENTS.addLast(timestamp + "  " + message);
        while (EVENTS.size() > 600) EVENTS.removeFirst();
    }

    private static synchronized String snapshot() {
        StringBuilder output = new StringBuilder("HyperScan · Laoch832\nR5 STS 结果与脱敏原文日志。先点“开始重现”，关闭面板并触发失败，再打开复制。\n\n");
        for (String event : EVENTS) output.append(event).append('\n');
        return output.toString();
    }

    private static int dp(Activity a, int value) { return (int) (value * a.getResources().getDisplayMetrics().density + .5f); }
    private static void schedule(Activity a) {
        UI.postDelayed(() -> {
            try {
                if (a.isFinishing() || a.isDestroyed()) return;
                ViewGroup decor = (ViewGroup) a.getWindow().getDecorView();
                if (decor.findViewWithTag("hyperscan_diagnostics") != null) return;
                Button button = new Button(a);
                button.setTag("hyperscan_diagnostics");
                button.setText("日志"); button.setTextSize(11);
                button.setTextColor(Color.WHITE); button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff373d37));
                button.setContentDescription("打开 HyperScan 诊断日志");
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(a, 58), dp(a, 42), Gravity.TOP | Gravity.END);
                params.topMargin = dp(a, 78); params.rightMargin = dp(a, 10);
                decor.addView(button, params); button.setElevation(dp(a, 30));
                button.setOnClickListener(view -> show(a));
            } catch (Throwable error) { record("OVERLAY — " + error.getClass().getSimpleName()); }
        }, 900);
    }

    private static void show(Activity a) {
        TextView text = new TextView(a);
        text.setText(snapshot()); text.setTextSize(11); text.setTextIsSelectable(true);
        text.setTypeface(android.graphics.Typeface.MONOSPACE); text.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        ScrollView scroll = new ScrollView(a); scroll.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(a).setTitle("HyperScan 内部诊断 · Laoch832")
            .setView(scroll).setPositiveButton("关闭", (d, which) -> {})
            .setNegativeButton("复制日志", (d, which) -> {
                ClipboardManager clipboard = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("HyperScan diagnostics", snapshot()));
            }).setNeutralButton("开始重现", (d, which) -> { synchronized (DebugOverlay.class) { EVENTS.clear(); last = ""; record("REPRO_START — 用户开始重现问题"); } }).create();
        dialog.show();
        scroll.getLayoutParams().height = dp(a, 380); scroll.requestLayout();
        Runnable refresh = new Runnable() {
            public void run() { if (dialog.isShowing()) { text.setText(snapshot()); UI.postDelayed(this, 1000); } }
        };
        UI.postDelayed(refresh, 1000);
    }
}
