/*
 * FloatingSurfaceView mechanism adapted from Android Open Source Project (2020),
 * Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
 * Nova 8.9.2 source owners: FloatingSurfaceView U..Z, o9/p1.onNewIntent and ea/a.
 */
package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Purpose: Port Nova's real FloatingSurfaceView provider for system-owned return-to-icon motion.
 * Invocation: A valid gesture contract and a settled visible app/widget target, never plain unlock.
 * Contract: Picture records the actual target artwork. SurfaceControl is sent to the system;
 * this view has NO animation interpolator, reverse clock, scale or translation track.
 * API30 uses Nova's application-panel route; API31+ uses target-native DragLayer layout params.
 * The one integration extension holds completed-surface cleanup until sibling/tray snapshots
 * restore their parents, preventing a hidden dock parent from swallowing the landed icon.
 * Verification: Source/API/protocol tests only; surface composition and OEM support unverified.
 * Visual: Android shrinks the real foreground task into this icon while iLauncher siblings fly in.
 */
public final class NovaGestureSurface extends FrameLayout
        implements ViewTreeObserver.OnGlobalLayoutListener, SurfaceHolder.Callback2 {
    private static int nextSession;
    // Nova onWindowFocusChanged uses 400 ms only before stable API33.
    private static final long LEGACY_FOCUS_CLEANUP_MS = 400;
    final int sessionId = ++nextSession;
    private final SurfaceView surface;
    private final Picture picture = new Picture();
    private final RectF position = new RectF();
    private final RectF temporary = new RectF();
    private final Rect localBounds = new Rect();
    private ViewGroup host;
    private View target;
    private float originalAlpha;
    private NovaGestureContract contract;
    private WindowManager panelManager;
    private boolean panelAdded;
    private boolean pictureReady;
    private boolean gridHeld;
    private boolean finishReceived;
    private boolean closing;
    private boolean disposed;
    private boolean sent;
    private boolean surfaceFailed;
    private Runnable cancelAll;
    private final Runnable remove = this::removeNow;
    private final Runnable legacyFinish = this::systemFinished;

    public NovaGestureSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        surface = new SurfaceView(context);
        surface.setZOrderOnTop(true);
        surface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        surface.getHolder().addCallback(this);
        addView(surface, new FrameLayout.LayoutParams(1, 1));
    }

    /** Purpose: Attach the same surface provider branches Nova uses, with target-owned parameters.
     * Invocation: Controller after target resolution, before hiding grid/tray snapshots.
     * Contract: XML layout_ignoreInsets generates the real target LP subclass; no guessed LP cast
     * or global window permission is used. Any setup failure restores source state before fallback.
     * Verification: Native target layout contracts read; actual window creation awaits compilation/UI.
     */
    static NovaGestureSurface show(Activity activity, ViewGroup host, View target,
            NovaGestureContract contract, boolean holdForTray, Runnable cancelAll) throws ReflectiveOperationException {
        if (Build.VERSION.SDK_INT < 30) throw new IllegalStateException("Gesture contract requires API30");
        int resource = activity.getResources().getIdentifier("combined_gesture_surface", "layout",
                activity.getPackageName());
        if (resource == 0) throw new IllegalStateException("Gesture surface layout missing");
        NovaGestureSurface view = (NovaGestureSurface) LayoutInflater.from(activity)
                .inflate(resource, host, false);
        view.host = host;
        view.target = target;
        view.originalAlpha = target.getAlpha();
        view.contract = contract;
        view.cancelAll = cancelAll;
        view.gridHeld = holdForTray;
        try {
            view.recordTarget();
            if (Build.VERSION.SDK_INT <= 30) {
                if (activity.getWindow().getAttributes().token == null)
                    throw new IllegalStateException("Window token not ready");
                view.panelManager = activity.getSystemService(WindowManager.class);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.copyFrom(activity.getWindow().getAttributes());
                params.token = null;
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                view.panelManager.addView(view, params);
                view.panelAdded = true;
            } else {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = host.getWidth();
                params.height = host.getHeight();
                view.setLayoutParams(params);
                host.addView(view);
            }
            target.setAlpha(0f);
            if (activity.hasWindowFocus()) view.onHostFocused();
            TransitionDiagnostics.event("gesture_surface_attached", view.sessionId);
            return view;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            view.abort();
            throw failure;
        }
    }

    /** Purpose: Record actual icon contents as Nova does, not a recreated Drawable approximation.
     * Invocation: Before source hiding; local icon bounds for BubbleTextView, full bounds for widgets.
     * Contract: Recording and alpha restoration are paired even if the target cannot draw.
     * Verification: Picture/crop source contract; hardware drawing requires the real app UI.
     */
    private void recordTarget() throws ReflectiveOperationException {
        RectF local = LauncherAccess.gestureArtwork(target);
        local.roundOut(localBounds);
        if (localBounds.isEmpty()) throw new IllegalArgumentException("Empty gesture target");
        if ((long) localBounds.width() * localBounds.height() > 6L * 1024L * 1024L)
            throw new IllegalArgumentException("Gesture target exceeds capture budget");
        target.setAlpha(originalAlpha);
        Canvas recording = picture.beginRecording(localBounds.width(), localBounds.height());
        try {
            recording.translate(-localBounds.left, -localBounds.top);
            target.draw(recording);
        } finally {
            picture.endRecording();
            target.setAlpha(originalAlpha);
        }
        pictureReady = true;
        updatePosition();
    }

    /** Purpose: Keep the system's destination current without locally moving its surface.
     * Invocation: Global layout and first surface creation.
     * Contract: Coordinates use the same drag-layer mapping as the launcher snapshots; the actual
     * target view must still exist. Detachment cancels owned visuals, not an external app.
     * Verification: Native bounds and callback source checks; display insets remain UI-tested later.
     */
    private void updatePosition() {
        if (closing || disposed || target == null || host == null) return;
        if (!target.isAttachedToWindow()) {
            if (cancelAll != null) cancelAll.run();
            return;
        }
        temporary.set(localBounds);
        LauncherAccess.toRoot(target, host).mapRect(temporary);
        if (!temporary.equals(position)) {
            position.set(temporary);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
            params.width = Math.max(1, Math.round(position.width()));
            params.height = Math.max(1, Math.round(position.height()));
            params.leftMargin = Math.round(position.left);
            params.topMargin = Math.round(position.top);
            surface.setLayoutParams(params);
            sendPosition();
        }
    }

    private void drawSurface() {
        if (!pictureReady || disposed || !surface.getHolder().getSurface().isValid()) return;
        SurfaceHolder holder = surface.getHolder();
        Canvas canvas = null;
        try {
            canvas = holder.lockHardwareCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                picture.draw(canvas);
            }
        } catch (RuntimeException unavailable) {
            surfaceFailed = true;
            TransitionDiagnostics.event("gesture_surface_draw_unavailable", sessionId);
        } finally {
            if (canvas != null) {
                try { holder.unlockCanvasAndPost(canvas); }
                catch (RuntimeException unavailable) {
                    surfaceFailed = true;
                    TransitionDiagnostics.event("gesture_surface_post_unavailable", sessionId);
                }
            }
        }
        if (surfaceFailed) {
            contract = null;
            finishIfReady();
        }
    }

    private void sendPosition() {
        if (contract == null || position.isEmpty() || closing || disposed) return;
        SurfaceControl control = surface.getSurfaceControl();
        if (control == null || !control.isValid()) return;
        if (contract.send(position, control, this) && !sent) {
            sent = true;
            TransitionDiagnostics.event("gesture_surface_reply", sessionId);
        }
    }

    void onHostFocused() {
        if (closing || disposed) return;
        removeCallbacks(legacyFinish);
        if (Build.VERSION.SDK_INT < 32
                || (Build.VERSION.SDK_INT == 32 && Build.VERSION.PREVIEW_SDK_INT < 1))
            postDelayed(legacyFinish, LEGACY_FOCUS_CLEANUP_MS);
    }

    /** Purpose: Finish only when both real system motion and sibling snapshot ownership permit it.
     * Invocation: what=0 callback (or Nova's old-API focus fallback), plus grid completion.
     * Contract: No zero-duration failure inference. Native completion requests restoration;
     * an observed drawing exception requests a separately logged fallback, not a fake completion.
     * A hidden parent tray delays both. Removal waits Nova's two display-frame intervals.
     * Verification: Finish-message and cleanup-order source tests; real callback timing unverified.
     */
    void systemFinished() {
        if (closing || disposed) return;
        finishReceived = true;
        removeCallbacks(legacyFinish);
        TransitionDiagnostics.event("gesture_system_finished", sessionId);
        finishIfReady();
    }
    void releaseGridHold() {
        gridHeld = false;
        finishIfReady();
    }
    private void finishIfReady() {
        if ((!finishReceived && !surfaceFailed) || gridHeld || closing || disposed) return;
        closing = true;
        if (target != null) target.setAlpha(originalAlpha);
        contract = null;
        NovaGestureContract.release(this);
        float hz = getDisplay() == null ? 60f : getDisplay().getRefreshRate();
        long twoFrames = 2L * Math.max(1, (int) (1000f / Math.max(1f, hz)));
        postDelayed(remove, twoFrames);
    }

    /** Purpose: Nova-style cancellation on next gesture, pause, destroy, rotation or touch.
     * Invocation: Controller's centralized reversible teardown; never a guessed app failure.
     * Contract: Idempotent; source alpha/listeners/messages/surface parent restored or removed once.
     * Verification: Source ownership audit; device interruption testing remains required.
     */
    void abort() {
        if (disposed) return;
        closing = true;
        removeNow();
    }
    private void removeNow() {
        if (disposed) return;
        disposed = true;
        removeCallbacks(remove);
        removeCallbacks(legacyFinish);
        if (target != null) target.setAlpha(originalAlpha);
        NovaGestureContract.release(this);
        contract = null;
        target = null;
        cancelAll = null;
        picture.beginRecording(1, 1);
        picture.endRecording();
        if (panelAdded) {
            panelAdded = false;
            try { panelManager.removeViewImmediate(this); }
            catch (IllegalArgumentException alreadyDetached) {
                TransitionDiagnostics.event("gesture_panel_already_detached", sessionId);
            }
        } else if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
        host = null;
        panelManager = null;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
        updatePosition();
    }
    @Override protected void onDetachedFromWindow() {
        if (getViewTreeObserver().isAlive())
            getViewTreeObserver().removeOnGlobalLayoutListener(this);
        if (target != null) target.setAlpha(originalAlpha);
        super.onDetachedFromWindow();
    }
    @Override public void onGlobalLayout() { updatePosition(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (cancelAll != null) cancelAll.run();
        return false;
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { drawSurface(); sendPosition(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        drawSurface(); sendPosition();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { }
    @Override public void surfaceRedrawNeeded(SurfaceHolder holder) { drawSurface(); }
}
