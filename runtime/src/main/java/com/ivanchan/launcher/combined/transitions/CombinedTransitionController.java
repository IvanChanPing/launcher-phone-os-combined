package com.ivanchan.launcher.combined.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Purpose: Coordinate grid-only unlock, Nova open, and Nova's system gesture contract plus grid return.
 * Invocation: Exact target launch/options hooks and post-body lifecycle/model-ready hooks.
 * Contract: Main-thread only. Weak Activity ownership outside an active rendering interval;
 * generation invalidates callbacks. Vendor j0 remains the only actual launch authority.
 * Snapshot failure returns to that native path before originals are hidden. Issued replay is
 * never repeated on a timeout or exception. Surface selection is from current target state.
 * Verification: Host source/patch checks only; compilation and actual UI remain separately gated.
 * Visual: One root coordinate space and one clock; no live child scale/translation mutation.
 */
public final class CombinedTransitionController {
    private static final WeakHashMap<Activity, CombinedTransitionController> owners = new WeakHashMap<>();
    private static final String IGNORE = "com.android.launcher3.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";
    // Geometry must remain ready across two observations at least 48 ms apart.
    private static final long SETTLE_MS = 48;
    // This bounds optional visuals, never declares the vendor launch failed.
    private static final long VISUAL_WAIT_MS = 1500;
    private final WeakReference<Activity> activity;
    private ViewGroup root;
    private View source;
    private float sourceAlpha;
    private SnapshotGridView grid;
    private IconOverlayView icon;
    private ValueAnimator animator;
    private ViewTreeObserver.OnPreDrawListener preDraw;
    private Runnable timeout;
    private Intent pendingIntent;
    private Object pendingItem;
    private ActivityOptions options;
    private long generation;
    private boolean resumed;
    private boolean cold = true;
    private boolean departed;
    private boolean issued;
    private boolean replaying;
    private boolean opening;
    private boolean homeIntent;
    private boolean destroyed;
    private NovaGestureContract pendingGesture;
    private NovaGestureSurface gestureSurface;

    private CombinedTransitionController(Activity activity) {
        this.activity = new WeakReference<>(activity);
        homeIntent = isHome(activity.getIntent());
    }

    private static CombinedTransitionController owner(Activity activity) {
        CombinedTransitionController value = owners.get(activity);
        if (value == null) { value = new CombinedTransitionController(activity); owners.put(activity, value); }
        return value;
    }

    public static void install(Application application) {
        UnlockSignalTracker.install(application);
        TransitionDiagnostics.install(application);
    }

    public static boolean interceptLaunch(Activity activity, View source, Intent intent, Object item) {
        if (Looper.myLooper() != Looper.getMainLooper() || !LauncherAccess.type(activity, LauncherAccess.LAUNCHER))
            return false;
        return owner(activity).intercept(source, intent, item);
    }

    public static ActivityOptions consumeLaunchOptions(Activity activity, View source) {
        CombinedTransitionController value = owners.get(activity);
        if (value == null || !value.replaying || value.source != source) return null;
        ActivityOptions result = value.options; value.options = null; return result;
    }

    public static void onLauncherCreated(Activity activity) {
        owner(activity).captureHomeIntent(activity.getIntent());
    }
    public static void onLauncherStarted(Activity activity) { owner(activity); }
    public static void onLauncherResumed(Activity activity) {
        CombinedTransitionController value = owner(activity);
        value.resumed = true;
        if (value.departed && value.opening) value.clearVisuals();
        value.requestEntry();
    }
    public static void onLauncherFocused(Activity activity) {
        CombinedTransitionController value = owner(activity);
        if (activity.hasWindowFocus()) {
            if (value.gestureSurface != null) value.gestureSurface.onHostFocused();
            value.requestEntry();
        }
    }
    public static void onLauncherModelReady(Activity activity) { owner(activity).requestEntry(); }
    /** Purpose: Consume Nova's gesture parcel before the target reuses its p1 register.
     * Invocation: onNewIntent entry; capture only, post-body/model-ready owns surface creation.
     * Contract: Clear an older surface, preserve the incoming system Message identity, remove the
     * consumed extra once. An actual contract supplies return identity even for externally opened apps.
     * Verification: Nova o9/p1 raw parser and target p1-clobber fixtures; Binder UI remains unverified.
     */
    public static void recordHomeIntent(Activity activity, Intent intent) {
        owner(activity).captureHomeIntent(intent);
    }
    private void captureHomeIntent(Intent intent) {
        clearVisuals();
        homeIntent = isHome(intent);
        pendingGesture = NovaGestureContract.consume(intent);
        if (pendingGesture != null) { homeIntent = true; departed = true; }
        else if (homeIntent) TransitionDiagnostics.event("gesture_contract_absent", generation);
    }
    public static void onLauncherNewIntent(Activity activity) {
        CombinedTransitionController value = owner(activity);
        // A real HOME action supersedes any not-yet-issued tap after the vendor settles its UI.
        if (value.homeIntent && value.opening && !value.issued) value.clearVisuals();
        value.requestEntry();
    }
    public static void onLauncherPaused(Activity activity) {
        CombinedTransitionController value = owner(activity);
        value.resumed = false;
        value.pendingGesture = null;
        value.closeGestureSurface();
        value.departed = value.issued;
        // Purpose: Preserve the already-issued 450 ms open while Home is still visible.
        // Invocation: onPause after vendor launch; stop/destroy/completion still centrally clean up.
        // Contract: Non-launch interruptions cancel normally; pause is not window invisibility.
        // Verification: Nova animator-end ownership and Android lifecycle contract; UI unverified.
        if (!(value.opening && value.issued && value.animator != null)) value.clearVisuals();
    }
    public static void onLauncherStopped(Activity activity) {
        CombinedTransitionController value = owner(activity);
        // Internal settings and permission UI do not acquire an external-app return token.
        value.pendingGesture = null;
        value.departed = value.issued;
        value.clearVisuals();
    }
    public static void onLauncherConfigurationChanged(Activity activity) {
        CombinedTransitionController value = owner(activity);
        value.pendingGesture = null;
        value.clearVisuals();
        value.requestEntry();
    }
    public static void onLauncherDestroyed(Activity activity) {
        CombinedTransitionController value = owners.remove(activity);
        if (value != null) { value.destroyed = true; value.clearVisuals(); }
    }
    static void onUnlocked() {
        for (CombinedTransitionController value : new ArrayList<>(owners.values())) value.requestEntry();
    }
    static void onScreenOff() {
        for (CombinedTransitionController value : new ArrayList<>(owners.values())) {
            value.departed = false; value.issued = false; value.pendingGesture = null; value.clearVisuals();
        }
    }

    private static boolean isHome(Intent intent) {
        return intent != null && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME);
    }

    private boolean eligible(Activity a) {
        return resumed && a.hasWindowFocus() && gestureEligible(a);
    }

    // Nova handles the contract from onNewIntent without waiting for Home to gain focus:
    // the system needs the destination while it is still animating the outgoing app.
    private boolean gestureEligible(Activity a) {
        KeyguardManager keyguard = a.getSystemService(KeyguardManager.class);
        PowerManager power = a.getSystemService(PowerManager.class);
        return !destroyed && !a.isFinishing()
                && (keyguard == null || !keyguard.isKeyguardLocked())
                && (power == null || power.isInteractive());
    }

    private static boolean animations(Activity a) {
        return Settings.Global.getFloat(a.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    private boolean intercept(View tapped, Intent intent, Object item) {
        Activity a = activity.get();
        if (replaying) return false;
        if (opening) return true;
        clearVisuals();
        pendingGesture = null;
        if (a == null || tapped == null || intent == null || !eligible(a)
                || !LauncherAccess.type(tapped, LauncherAccess.BUBBLE)
                || intent.hasExtra(IGNORE) || !animations(a)) return false;
        if (intent.getComponent() != null && a.getPackageName().equals(intent.getComponent().getPackageName()))
            return false;
        if (a.getPackageName().equals(intent.getPackage())) return false;
        if (("android.intent.action.CALL".equals(intent.getAction())
                || "android.intent.action.CALL_PRIVILEGED".equals(intent.getAction()))
                && a.checkSelfPermission("android.permission.CALL_PHONE") != PackageManager.PERMISSION_GRANTED)
            return false;
        try {
            if (LauncherAccess.binding(a)) return false;
            LauncherAccess.Scene scene = LauncherAccess.scene(a, tapped);
            if (scene == null || !LauncherAccess.visible(tapped)) return false;
            icon = new IconOverlayView(a, scene.root, tapped, scene.cellHeight);
            grid = new SnapshotGridView(scene, tapped);
            root = scene.root; source = tapped; sourceAlpha = tapped.getAlpha();
            pendingIntent = new Intent(intent); pendingItem = item;
            opening = true; issued = false;
            grid.attach(false); icon.attach(); source.setAlpha(0f);
            long token = ++generation;
            // First renderer draw owns time zero; layout was captured synchronously from this tap.
            preDraw = () -> {
                removePreDraw();
                removeTimeout();
                if (token == generation) runClock(MotionMath.OPEN_MS, false, token);
                return true;
            };
            root.getViewTreeObserver().addOnPreDrawListener(preDraw);
            // Optional rendering has a bounded chance to draw. Re-read actual eligibility before
            // one native replay at the visual deadline; never retry a previously issued launch.
            timeout = () -> {
                Activity current = activity.get();
                if (token == generation) {
                    if (current != null && eligible(current) && !issued) replay(token);
                    clearVisuals();
                }
            };
            root.postDelayed(timeout, VISUAL_WAIT_MS);
            root.invalidate();
            TransitionDiagnostics.event("open_prepared", token);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            TransitionDiagnostics.event("capture_native_fallback", generation);
            clearVisuals(); return false;
        }
    }

    /** Purpose: Wait for actual binding completion and stable layout before entry snapshots.
     * Invocation: Resume, focus, model-ready and unlock notification converge here.
     * Contract: No cold/unlock token consumed while loading. Deadline only skips optional motion;
     * disappearance during handover is observed again, never treated as an app-launch failure.
     * Verification: Hook-order and gate source checks; timing on OEMs remains device-unverified.
     */
    private void requestEntry() {
        Activity a = activity.get();
        if (a == null || !(pendingGesture == null ? eligible(a) : gestureEligible(a))
                || opening || grid != null || preDraw != null) return;
        boolean unlock = pendingGesture == null && (UnlockSignalTracker.pending() || (cold && homeIntent));
        if (!unlock && !departed && pendingGesture == null) return;
        try {
            if (LauncherAccess.binding(a)) return; // F() notifies after actual model completion.
            root = LauncherAccess.root(a);
            if (root == null || !root.isAttachedToWindow()) { root = null; return; }
            long token = ++generation, began = SystemClock.uptimeMillis();
            long[] stable = {0};
            int[] dimensions = {-1, -1};
            int delay = unlock ? MotionMath.UNLOCK_DELAY_MS : MotionMath.RETURN_DELAY_MS;
            preDraw = () -> {
                Activity current = activity.get();
                if (token != generation || current == null) { clearVisuals(); return true; }
                long now = SystemClock.uptimeMillis();
                try {
                    boolean ready = (pendingGesture == null ? eligible(current) : gestureEligible(current))
                            && !LauncherAccess.binding(current)
                            && root.isLaidOut() && !root.isLayoutRequested();
                    if (!ready || dimensions[0] != root.getWidth() || dimensions[1] != root.getHeight()) {
                        stable[0] = now; dimensions[0] = root.getWidth(); dimensions[1] = root.getHeight();
                    } else if (now - stable[0] >= SETTLE_MS && now - began >= delay) {
                        LauncherAccess.Scene scene = LauncherAccess.scene(current, null);
                        if (scene != null && (!unlock || (scene.home && homeIntent))) {
                            removePreDraw(); removeTimeout();
                            if (animations(current)) {
                                prepareReturnVisuals(current, scene, unlock, true);
                                runClock(grid.duration, true, token);
                            } else if (pendingGesture != null) {
                                prepareReturnVisuals(current, scene, false, false);
                                clearVisuals(true);
                            } else clearVisuals();
                            cold = false; departed = false; issued = false; pendingGesture = null;
                            if (unlock) UnlockSignalTracker.consume();
                            TransitionDiagnostics.event(unlock ? "unlock_entry" : "return_entry", token);
                            return true;
                        }
                    }
                    if (now - began >= VISUAL_WAIT_MS) {
                        TransitionDiagnostics.event("entry_visual_timeout", token);
                        pendingGesture = null;
                        clearVisuals();
                        return true;
                    }
                    root.postInvalidateOnAnimation();
                    return false; // Do not expose a static Home frame before the fly-in snapshot.
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    TransitionDiagnostics.event("entry_native_fallback", token); clearVisuals();
                }
                return true;
            };
            root.getViewTreeObserver().addOnPreDrawListener(preDraw);
            timeout = () -> {
                if (token == generation) {
                    TransitionDiagnostics.event("entry_no_draw_timeout", token); pendingGesture = null; clearVisuals();
                }
            };
            root.postDelayed(timeout, VISUAL_WAIT_MS + SETTLE_MS);
            root.postInvalidateOnAnimation();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            TransitionDiagnostics.event("entry_owner_unavailable", generation); clearVisuals();
        }
    }

    /**
     * Purpose: Use Nova's system-owned return surface while the iLauncher siblings animate.
     * Invocation: Actual HOME gesture contract, after vendor model/layout preparation.
     * Contract: The system provides component/user; there is no hand-authored reverse trajectory.
     * Supply its real Picture surface and exclude the same target from grid/tray snapshots.
     * Missing contract/target/surface leaves the native system fallback, never a fake drawn shrink.
     * Verification: Nova protocol/source contract tests; real gesture IPC and frames unverified.
     * Visual: System moves the real app and icon surface; only other icons use our grid clock.
     */
    private void prepareReturnVisuals(Activity current, LauncherAccess.Scene scene, boolean unlock,
            boolean animateGrid) throws ReflectiveOperationException {
        NovaGestureContract contract = unlock ? null : pendingGesture;
        View landing = contract == null ? null
                : LauncherAccess.gestureTarget(scene, contract.component, contract.user);
        if (landing != null) {
            try {
                gestureSurface = NovaGestureSurface.show(current, scene.root, landing, contract,
                        animateGrid && scene.sourceInStrip, this::clearVisuals);
            } catch (ReflectiveOperationException | RuntimeException unavailable) {
                closeGestureSurface();
                landing = null; scene.sourceInStrip = false;
                TransitionDiagnostics.event("gesture_surface_unavailable", generation);
            }
        } else if (contract != null) {
            TransitionDiagnostics.event("gesture_target_unavailable", generation);
        }
        if (animateGrid) {
            grid = new SnapshotGridView(scene, landing);
            grid.attach(true);
        }
    }

    private void closeGestureSurface() {
        NovaGestureSurface previous = gestureSurface;
        gestureSurface = null;
        if (previous != null) previous.abort();
    }

    private void runClock(int duration, boolean inward, long token) {
        ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
        animator = clock; clock.setDuration(duration); clock.setInterpolator(value -> value);
        clock.addUpdateListener(value -> {
            if (token != generation) return;
            float elapsed = clock.getAnimatedFraction() * duration;
            if (grid != null) grid.progress(elapsed);
            if (!inward && icon != null && icon.progress(elapsed) < .13f && !issued) replay(token);
        });
        clock.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (token == generation) clearVisuals(inward);
            }
        });
        clock.start();
    }

    /** Purpose: Hand off the opening transition to the existing vendor launch once.
     * Invocation: Opening clock or its bounded pre-draw timeout.
     * Contract: Exceptions clear the current pending contract and visuals, not removed artwork
     * state; issued remains claimed so an uncertain vendor launch cannot be repeated.
     * Verification: Retired-field regression check and Android compilation; device UI separate.
     */
    private void replay(long token) {
        Activity a = activity.get();
        if (token != generation || a == null || source == null || pendingIntent == null) {
            clearVisuals(); return;
        }
        issued = true; // Claim before invoking vendor code; no exception can cause a second launch.
        try {
            Rect rect = icon.launchBounds();
            options = ActivityOptions.makeScaleUpAnimation(root, rect.left, rect.top,
                    Math.max(1, rect.width()), Math.max(1, rect.height()));
            if (Build.VERSION.SDK_INT >= 33) options.setSplashScreenStyle(1);
            replaying = true;
            boolean accepted = LauncherAccess.replay(a, source, pendingIntent, pendingItem);
            TransitionDiagnostics.event(accepted ? "vendor_launch_accepted" : "vendor_launch_declined", token);
            if (!accepted) { issued = false; pendingGesture = null; clearVisuals(); }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            TransitionDiagnostics.event("vendor_launch_exception", token);
            pendingGesture = null;
            clearVisuals();
        } finally {
            replaying = false; options = null; pendingIntent = null; pendingItem = null;
        }
    }

    private void removePreDraw() {
        if (root != null && preDraw != null && root.getViewTreeObserver().isAlive())
            root.getViewTreeObserver().removeOnPreDrawListener(preDraw);
        preDraw = null;
    }
    private void removeTimeout() {
        if (root != null && timeout != null) root.removeCallbacks(timeout);
        timeout = null;
    }

    /** Purpose: Central reversible visual teardown, not a semantic launch-failure decision.
     * Invocation: Pause/stop/destroy/configuration, capture failure, completion and new gestures.
     * Contract: Increment generation before cancel; restore grid/open alpha and release their
     * snapshots, callbacks and strong references. Normal grid completion only releases the tray
     * hold: the separate system surface remains until its native finish callback. Interruptions
     * abort that surface too. No surface graph is retained through pause/stop/destroy.
     * Verification: Ownership/source audit; interruption behavior remains a UI test requirement.
     */
    private void clearVisuals() { clearVisuals(false); }

    private void clearVisuals(boolean gridCompletedNormally) {
        generation++;
        removePreDraw(); removeTimeout();
        if (animator != null) {
            ValueAnimator old = animator; animator = null;
            old.removeAllUpdateListeners(); old.removeAllListeners(); old.cancel();
        }
        if (grid != null) { grid.release(); grid = null; }
        if (gridCompletedNormally) {
            if (gestureSurface != null) gestureSurface.releaseGridHold();
        } else {
            closeGestureSurface();
        }
        if (icon != null) { icon.release(); icon = null; }
        if (source != null) source.setAlpha(sourceAlpha);
        root = null; source = null; pendingIntent = null; pendingItem = null; options = null;
        opening = false;
    }
}
