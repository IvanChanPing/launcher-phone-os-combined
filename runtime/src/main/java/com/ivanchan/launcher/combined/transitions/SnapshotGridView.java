package com.ivanchan.launcher.combined.transitions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Purpose: Render complete icon/label/badge snapshots above all ancestor clipping.
 * Invocation: Controller captures before hiding originals, attaches to the drag-layer overlay.
 * Contract: A 24 MiB pixel budget bounds capture; any failure releases captures before fallback.
 * Only alpha is temporarily changed, restored exactly on every exit; strip children never get
 * duplicate flights. The selected icon is excluded from the dock bitmap before its own overlay.
 * Verification: Source/ownership checks; actual hardware bitmap compatibility needs device tests.
 * Visual: Cell-index groups fly about one anchor; dock and indicator translate together after 300 ms.
 */
final class SnapshotGridView extends View {
    private static final long MAX_PIXELS = 6L * 1024L * 1024L;
    private final LauncherAccess.Scene scene;
    private final List<Shot> shots = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final int duration;
    private final int referenceDuration;
    private final TimingSettings timing;
    private boolean inward;
    private float elapsed;
    private boolean hidden;
    private long pixels;
    private float stripTravel;

    private static final class Shot {
        final View source;
        final float alpha;
        final Matrix matrix;
        final Bitmap bitmap;
        final int ring;
        final boolean strip;
        Shot(View source, Matrix matrix, Bitmap bitmap, int ring, boolean strip) {
            this.source = source; this.alpha = source.getAlpha(); this.matrix = matrix;
            this.bitmap = bitmap; this.ring = ring; this.strip = strip;
        }
    }

    SnapshotGridView(LauncherAccess.Scene scene, View selected) {
        this(scene, selected, TimingSettings.DEFAULT);
    }

    /** Purpose: Freeze live return timing before capturing any pixels.
     * Invocation: Return preparation supplies live settings; opening/unlock use defaults.
     * Contract: One immutable snapshot for the whole animation. No config/network reads in draw.
     * Verification: Host default parity and snapshot wiring checks; Android rendering unverified.
     * Visual: Geometry is unchanged; only explicitly configured return timing differs.
     */
    SnapshotGridView(LauncherAccess.Scene scene, View selected, TimingSettings timing) {
        super(scene.root.getContext());
        this.scene = scene;
        this.timing = timing;
        referenceDuration = MotionMath.sceneDuration(scene.maxRing);
        duration = Math.round(referenceDuration * timing.flyInDurationScale);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        try {
            for (LauncherAccess.Item item : scene.items)
                if (item.view != selected) capture(item.view, item.matrix, item.ring, false);
            float alpha = selected == null ? 1f : selected.getAlpha();
            try {
                if (selected != null && scene.sourceInStrip) selected.setAlpha(0f);
                for (View strip : scene.strips) {
                    // iLauncher starts the group at the viewport bottom. The target dock floats
                    // above a bottom margin, so summing child heights omits part of its travel.
                    stripTravel = Math.max(stripTravel, MotionMath.stripTravel(
                            scene.root.getHeight(), LauncherAccess.bounds(strip, scene.root).top));
                    capture(strip, LauncherAccess.toRoot(strip, scene.root), 0, true);
                }
            } finally {
                if (selected != null) selected.setAlpha(alpha);
            }
        } catch (RuntimeException failure) {
            release(); throw failure;
        }
    }

    private void capture(View source, Matrix matrix, int ring, boolean strip) {
        long count = (long) source.getWidth() * source.getHeight();
        if (count <= 0 || pixels + count > MAX_PIXELS)
            throw new IllegalArgumentException("Snapshot budget exceeded");
        Bitmap bitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(),
                Bitmap.Config.ARGB_8888);
        try {
            source.draw(new Canvas(bitmap));
            shots.add(new Shot(source, matrix, bitmap, ring, strip));
            pixels += count;
        } catch (RuntimeException failure) {
            bitmap.recycle(); throw failure;
        }
    }

    void attach(boolean entering) {
        inward = entering;
        layout(0, 0, scene.root.getWidth(), scene.root.getHeight());
        scene.root.getOverlay().add(this);
        for (Shot shot : shots) shot.source.setAlpha(0f);
        hidden = true;
        progress(0f);
    }

    void progress(float milliseconds) { elapsed = milliseconds; invalidate(); }

    /** Purpose: Supply this scene's computed card clock without changing grid duration.
     * Invocation: Controller prepares a selected-card return, not opening or unlock.
     * Contract: Use captured live gate/power and duration; reference grid/dock tables stay untouched.
     * Verification: Host math and controller wiring checks; phone appearance unverified.
     * Visual: Card and delayed normal-speed fly-in share their final frame.
     */
    int returnDuration() {
        if (timing.eased) return MotionMath.easedReturnDuration(scene.maxRing, duration,
                timing.shrinkGate, timing.cardPower,
                timing.easeX1, timing.easeY1, timing.easeX2, timing.easeY2);
        return MotionMath.returnDuration(scene.maxRing, duration, timing.shrinkGate, timing.cardPower);
    }

    /** Purpose: Share the outer-icon return curve with the selected card.
     * Invocation: Controller's same-frame update, before the existing shared teardown.
     * Contract: Convert this scene's configured duration to reference time, then sample captured
     * card easing. No live reads, geometry changes or extra clock; default arithmetic is preserved.
     * Verification: Host endpoint/monotonicity tests; phone appearance unverified.
     * Visual: Selected card settles alongside the surrounding-icon entrance.
     */
    float cardRemaining(float milliseconds) {
        float time = duration == referenceDuration ? milliseconds
                : milliseconds * referenceDuration / duration;
        if (timing.eased) return MotionMath.easedReturnRemaining(scene.maxRing, time, timing.cardPower,
                timing.easeX1, timing.easeY1, timing.easeX2, timing.easeY2);
        return MotionMath.synchronizedReturnRemaining(scene.maxRing, time, timing.cardPower);
    }

    /** Purpose: Draw existing snapshots using the timing captured at preparation.
     * Invocation: Android draw callback. Contract: Default speed is unchanged; a live duration
     * multiplier applies uniformly to the return grid. Dock delay is in reference milliseconds.
     * Opening uses its existing clock; no per-frame network or mutable-config access.
     * Verification: Host default/sample parity checks; phone rendering unverified.
     * Visual: Same icons, labels and dock paths; timing controls do not change layout.
     */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float time = inward ? elapsed : duration * (1f - MotionMath.clamp(elapsed / MotionMath.OPEN_MS));
        if (inward && duration != referenceDuration) time = elapsed * referenceDuration / duration;
        for (Shot shot : shots) {
            if (inward && time < (shot.strip ? timing.dockStartMs : MotionMath.delay(shot.ring)))
                continue;
            canvas.save();
            if (shot.strip) {
                // Shared bottom-edge travel preserves the target's actual inter-strip gaps and inset.
                canvas.translate(0, stripTravel * MotionMath.stripRemaining(time, referenceDuration, timing.dockStartMs));
            } else {
                float remaining = !inward && elapsed == 0f ? 0f : MotionMath.remaining(shot.ring, time);
                float scale = 1f + (6 - shot.ring) * remaining;
                canvas.scale(scale, scale, scene.anchorX, scene.anchorY);
            }
            canvas.concat(shot.matrix);
            paint.setAlpha(Math.round(255 * shot.alpha));
            canvas.drawBitmap(shot.bitmap, 0, 0, paint);
            canvas.restore();
        }
    }

    void release() {
        scene.root.getOverlay().remove(this);
        for (Shot shot : shots) {
            if (hidden) shot.source.setAlpha(shot.alpha);
            if (!shot.bitmap.isRecycled()) shot.bitmap.recycle();
        }
        hidden = false;
        shots.clear();
    }
}
