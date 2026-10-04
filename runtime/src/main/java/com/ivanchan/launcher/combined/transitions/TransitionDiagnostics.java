package com.ivanchan.launcher.combined.transitions;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Purpose: Deliver bounded transition result codes to the existing box-readable collector.
 * Invocation: Main-thread state changes enqueue only; one daemon worker sends and retries.
 * Contract: No package names, intents, pixels, tokens, or exception text are uploaded. At most
 * 64 events of 160 characters; drop oldest under pressure. Validated INTERNET network only,
 * 3-second connect/read deadlines, retry every 15 seconds. No main-thread network or logcat capture.
 * Verification: Collector POST probe passed; actual app delivery still requires device execution.
 */
final class TransitionDiagnostics {
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "combined-transition-events"); thread.setDaemon(true); return thread;
    });
    private static Context app;
    static synchronized void install(Context context) {
        if (app != null) return;
        app = context.getApplicationContext();
        worker.scheduleWithFixedDelay(TransitionDiagnostics::flush, 0, 15, TimeUnit.SECONDS);
    }
    static synchronized void event(String code, long generation) {
        if (queue.size() == 64) queue.removeFirst();
        String event = "transition=" + code + " generation=" + generation + " sdk=" + Build.VERSION.SDK_INT;
        queue.addLast(event.substring(0, Math.min(160, event.length())));
        if (queue.size() == 1) worker.execute(TransitionDiagnostics::flush);
    }
    private static void flush() {
        String value;
        synchronized (TransitionDiagnostics.class) { value = queue.peekFirst(); }
        if (value == null || app == null) return;
        HttpURLConnection connection = null;
        try {
            ConnectivityManager manager = app.getSystemService(ConnectivityManager.class);
            if (manager == null) return;
            Network route = null;
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    route = network; break;
                }
            }
            if (route == null) return;
            connection = (HttpURLConnection) route.openConnection(new URL(
                    "https://204-168-163-118.sslip.io/imelog/launcher-combined"));
            connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            connection.setRequestProperty("X-Device", "launcher-combined");
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                synchronized (TransitionDiagnostics.class) {
                    if (value.equals(queue.peekFirst())) queue.removeFirst();
                }
            }
        } catch (java.io.IOException | RuntimeException unavailable) {
            // Bounded retained queue retries automatically; never ask the user to relay logs.
        } finally { if (connection != null) connection.disconnect(); }
    }
}
