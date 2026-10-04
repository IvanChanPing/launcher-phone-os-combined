package com.ivanchan.launcher.combined.transitions;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;

/**
 * Purpose: Join USER_PRESENT arriving either before or after launcher resume.
 * Invocation: Application install; receiver notifies currently resumed weak controller immediately.
 * Contract: No Activity is retained. Tokens expire after five seconds and are consumed only by
 * eligible Home entry, not by a loading or locked launcher. SCREEN_OFF cancels transient rendering.
 * Verification: Lifecycle source tests; OEM broadcast ordering requires authorized device testing.
 * Visual: Unlock uses grid-only entry; never selected-icon expansion.
 */
final class UnlockSignalTracker {
    private static boolean installed;
    private static long token;
    private static long consumed;
    private static long receivedAt;
    static void install(Application application) {
        if (installed) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    token++; receivedAt = SystemClock.uptimeMillis();
                    CombinedTransitionController.onUnlocked();
                } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    consumed = token;
                    CombinedTransitionController.onScreenOff();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33)
            application.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else application.registerReceiver(receiver, filter);
        installed = true;
    }
    static boolean pending() {
        return token != consumed && SystemClock.uptimeMillis() - receivedAt <= 5000;
    }
    static void consume() { consumed = token; }
}
