package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Purpose: Fetch data-only live timing without rebuilding the launcher.
 * Invocation: Opt-in install at Application startup; existing launcher lifecycle hooks own polling.
 * Contract: One worker, weak Activity keys, foreground-only 1500 ms fixed-delay polling. HTTPS only,
 * platform TLS verification, no redirects, 4096-byte response cap, bounded connect/read waits.
 * Network and private cache I/O stay off main. Invalid/offline updates retain last-good/defaults.
 * Only a volatile immutable snapshot is published; active animations retain their captured values.
 * Verification: Host parsing/math/wiring checks and HTTPS endpoint probe; Android execution unverified.
 * Visual: No extra screen; the next Home return uses the latest complete configuration.
 */
public final class LiveTimingConfig {
    private static final long POLL_MS = 1500;
    private static final int TIMEOUT_MS = 2000;
    private static final int MAX_BYTES = 4096;
    private static final long RESPONSE_BUDGET_MS = 5000;
    private static final WeakHashMap<Activity, Boolean> visible = new WeakHashMap<>();
    private static final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "launcher-live-timing");
        thread.setDaemon(true); return thread;
    });
    private static volatile TimingSettings current = TimingSettings.DEFAULT;
    private static volatile Context app;
    private static volatile boolean active;
    private static String endpoint;
    private static ScheduledFuture<?> poll;
    // Worker-only state: no shared mutable config map and no repeated writes for unchanged text.
    private static String lastText;
    private static String lastStatus;

    private LiveTimingConfig() {}
    public static TimingSettings current() { return current; }

    /** Purpose: Enable live tuning for this host's trusted HTTPS endpoint.
     * Invocation: Application initialization, after CombinedTransitionController.install().
     * Contract: Idempotent for the same URL; a different URL requires a process restart. Cache is
     * endpoint-scoped. No credentials in URLs. Initial cache load is queued off main before polls.
     * Verification: Source lifecycle/transport checks; actual phone fetch is unverified.
     */
    public static synchronized void install(Application application, String configUrl) {
        try {
            URL url = new URL(configUrl);
            if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty()
                    || url.getUserInfo() != null) throw new IllegalArgumentException("HTTPS URL required");
        } catch (java.net.MalformedURLException invalid) {
            throw new IllegalArgumentException("Invalid config URL", invalid);
        }
        if (app != null) {
            if (!endpoint.equals(configUrl)) throw new IllegalStateException("Config already installed");
            return;
        }
        endpoint = configUrl;
        app = application.getApplicationContext();
        worker.execute(LiveTimingConfig::loadCache);
    }

    /** Purpose: Start/stop only config polling as Home resumes or leaves.
     * Invocation: Controller resume, pause, stop and destroy; weak keys support multiple hosts.
     * Contract: Never cancels an animation. Removing the final visible owner cancels the future
     * and purges its queued task; an in-flight bounded fetch may finish caching for the next return.
     * Verification: Source lifecycle checks; device lifecycle scheduling remains unverified.
     */
    static synchronized void setVisible(Activity activity, boolean foreground) {
        if (app == null) return;
        if (foreground) visible.put(activity, Boolean.TRUE); else visible.remove(activity);
        active = !visible.isEmpty();
        if (active && poll == null)
            poll = worker.scheduleWithFixedDelay(LiveTimingConfig::refresh, 0, POLL_MS, TimeUnit.MILLISECONDS);
        else if (!active && poll != null) {
            poll.cancel(false); poll = null; worker.purge();
        }
    }

    /** Purpose: Restore last-good timing without blocking launcher startup.
     * Invocation: Single worker before its first poll. Contract: Cache belongs to exact endpoint;
     * invalid/missing cache leaves defaults. No Activity/View retained or mutated.
     * Verification: Parser tests and source I/O-thread checks; device storage unverified.
     */
    private static void loadCache() {
        try {
            SharedPreferences cache = app.getSharedPreferences("launcher_live_timing", Context.MODE_PRIVATE);
            String text = cache.getString("text", null);
            if (endpoint.equals(cache.getString("endpoint", null)) && text != null) {
                current = TimingSettings.parse(text); lastText = text;
                report("live_timing_cached", current.revision);
            }
        } catch (IOException | RuntimeException invalid) {
            report("live_timing_cache_invalid", current.revision);
        }
    }

    /** Purpose: Fetch and publish a complete timing revision, retaining the last good one on error.
     * Invocation: Fixed-delay worker only. Contract: Validated INTERNET route, no redirects or
     * certificate bypass; numeric payload only. Commit cache off main before publishing snapshot.
     * Failures emit bounded codes through the existing uploader, never exception text or user data.
     * Verification: Host parser/range/transport wiring checks and served-file byte check; phone unverified.
     */
    private static void refresh() {
        if (!active) return;
        HttpURLConnection connection = null;
        try {
            ConnectivityManager manager = app.getSystemService(ConnectivityManager.class);
            Network route = null;
            if (manager != null) for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    route = network; break;
                }
            }
            if (route == null) { report("live_timing_offline", current.revision); return; }
            connection = (HttpURLConnection) route.openConnection(new URL(endpoint));
            connection.setConnectTimeout(TIMEOUT_MS); connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            long deadline = SystemClock.elapsedRealtime() + RESPONSE_BUDGET_MS;
            if (connection.getResponseCode() != 200) throw new IOException("Config HTTP status");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > MAX_BYTES || SystemClock.elapsedRealtime() > deadline)
                        throw new IOException("Config response limit");
                    bytes.write(buffer, 0, count);
                }
            }
            String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (text.equals(lastText)) { report("live_timing_ready", current.revision); return; }
            TimingSettings next = TimingSettings.parse(text);
            SharedPreferences cache = app.getSharedPreferences("launcher_live_timing", Context.MODE_PRIVATE);
            boolean saved = cache.edit().putString("endpoint", endpoint).putString("text", text).commit();
            current = next;
            if (saved) lastText = text;
            report(saved ? "live_timing_ready" : "live_timing_cache_write_failed", next.revision);
        } catch (IOException | RuntimeException unavailable) {
            report("live_timing_retained", current.revision);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // Purpose: Reuse the existing bounded automatic uploader for state/revision changes only.
    // Invocation: Worker cache/fetch result. Contract: No content, URL, app names or stack traces.
    // Verification: Source checks confirm existing uploader use; no phone logs are read.
    private static void report(String code, int revision) {
        String status = code + ":" + revision;
        if (!status.equals(lastStatus)) {
            lastStatus = status; TransitionDiagnostics.event(code, revision);
        }
    }
}
