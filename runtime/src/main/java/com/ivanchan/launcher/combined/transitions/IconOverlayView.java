package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * Purpose: Nova-derived selected-artwork expansion, independent of the whole-cell flight renderer.
 * Invocation: Created only after drawable state and source-to-host geometry are ready.
 * Contract: Clone drawable state; never mutate vendor artwork. Adaptive background and foreground
 * keep independent bounds; nonadaptive artwork has no adaptive crop. All tracks use one linear time.
 * Verification: Reference track/geometry source checks; rendered parity is not claimed before UI tests.
 * Visual: Translation, scale, aspect crop, radius and alpha run on separate documented tracks.
 */
final class IconOverlayView extends View {
    private final ViewGroup host;
    private final RectF start;
    private final RectF current = new RectF();
    private final Drawable icon;
    private final Drawable background;
    private final Drawable foreground;
    private final Rect backgroundBounds;
    private final Rect foregroundBounds;
    private final boolean adaptive;
    private final boolean alternate;
    private final float base;
    private final float endRadius;
    private final float destinationX;
    private final float destinationY;
    private final Path clip = new Path();
    private final PathInterpolator movement = new PathInterpolator(.2f, 0f, 0f, 1f);
    private final PathInterpolator emphasized;
    private float outputAlpha = 1f;
    private float radius;

    IconOverlayView(Activity activity, ViewGroup host, View source, float cellHeight)
            throws ReflectiveOperationException {
        super(activity);
        this.host = host;
        start = LauncherAccess.artwork(source);
        LauncherAccess.toRoot(source, host).mapRect(start);
        if (start.width() <= 0 || start.height() <= 0)
            throw new IllegalArgumentException("Empty artwork bounds");
        Drawable original = LauncherAccess.drawable(source);
        if (original == null || original.getConstantState() == null)
            throw new IllegalArgumentException("Drawable has no independent state");
        icon = original.getConstantState().newDrawable(getResources()).mutate();
        icon.setState(original.getState());
        icon.setLevel(original.getLevel());
        base = Math.max(1f, Math.min(start.width(), start.height()));
        int size = Math.max(1, Math.round(base));
        icon.setBounds(0, 0, size, size);
        adaptive = Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable;
        if (adaptive) {
            AdaptiveIconDrawable layers = (AdaptiveIconDrawable) icon;
            background = layers.getBackground(); foreground = layers.getForeground();
            backgroundBounds = new Rect(background.getBounds());
            foregroundBounds = new Rect(foreground.getBounds());
        } else {
            background = null; foreground = null;
            backgroundBounds = new Rect(); foregroundBounds = new Rect();
        }
        int[] screenOrigin = new int[2];
        host.getLocationOnScreen(screenOrigin);
        // f10/d0.G subtracts the drag-layer screen origin before constructing its X/Y deltas.
        destinationX = host.getWidth() * .5f - screenOrigin[0];
        destinationY = host.getHeight() * .5f - screenOrigin[1];
        alternate = MotionMath.alternate(start.top, start.centerY(), destinationY, cellHeight);
        endRadius = activity.isInMultiWindowMode() ? 0f : 32f * getResources().getDisplayMetrics().density;
        Path curve = new Path();
        curve.moveTo(0f, 0f);
        curve.cubicTo(.05f, 0f, .133333f, .08f, .166666f, .4f);
        curve.cubicTo(.225f, .94f, .5f, 1f, 1f, 1f);
        emphasized = new PathInterpolator(curve);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        progress(0);
    }

    void attach() {
        layout(0, 0, host.getWidth(), host.getHeight());
        host.getOverlay().add(this);
    }

    float progress(float elapsed) {
        float width = host.getWidth(), height = host.getHeight(), shorter = Math.min(width, height);
        float x = movement.getInterpolation(MotionMath.clamp(elapsed / (alternate ? 250f : 360f)));
        float y = movement.getInterpolation(MotionMath.clamp(elapsed / (alternate ? 450f : 200f)));
        float scaleProgress = emphasized.getInterpolation(MotionMath.clamp(elapsed / MotionMath.OPEN_MS));
        float scale = MotionMath.lerp(1f, shorter / base, scaleProgress);
        float crop = emphasized.getInterpolation(MotionMath.clamp(elapsed / MotionMath.CROP_MS));
        float aspectProgress = crop;
        float cropWidth = width <= height ? width : MotionMath.lerp(height, width, aspectProgress);
        float cropHeight = width <= height ? MotionMath.lerp(width, height, aspectProgress) : height;
        float fit = Math.min(1f, Math.max(start.width() * scale / cropWidth,
                start.height() * scale / cropHeight));
        // az/n computes the aspect rectangle for BOTH drawable branches. Nonadaptive
        // artwork remains uniformly scaled inside it; its window handoff still uses this origin.
        float w = cropWidth * fit;
        float h = cropHeight * fit;
        float cx = MotionMath.lerp(start.centerX(), destinationX, x);
        float cy = MotionMath.lerp(start.centerY(), destinationY, y);
        current.set(cx - w * .5f, cy - h * .5f, cx + w * .5f, cy + h * .5f);
        radius = MotionMath.lerp(shorter * .5f, endRadius, aspectProgress) * fit;
        outputAlpha = MotionMath.alpha(elapsed, alternate);
        invalidate();
        return outputAlpha;
    }

    /**
     * Purpose: Synchronize Android's new-window scale-up with Nova's scaled icon-view origin.
     * Invocation: Controller replay once alpha crosses .13.
     * Contract: Crop bounds and launch bounds differ: v00/d scales the original measured view,
     * not its elongated crop. Integer rounding matches the reference view/options boundary.
     * Verification: Raw v00/d and o9/m comparison plus independent dimension tests.
     */
    Rect launchBounds() {
        int left = Math.round(current.left), top = Math.round(current.top);
        return new Rect(left, top, left + MotionMath.launchExtent(current.width(), current.height(),
                base, Math.round(start.width())), top + MotionMath.launchExtent(
                current.width(), current.height(), base, Math.round(start.height())));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int layer = canvas.saveLayerAlpha(current, Math.round(outputAlpha * 255));
        canvas.translate(current.left, current.top);
        if (!adaptive) {
            float outer = Math.max(1f, Math.min(current.width() / base, current.height() / base));
            canvas.scale(outer, outer);
            icon.draw(canvas);
        } else {
            clip.reset();
            clip.addRoundRect(new RectF(0, 0, current.width(), current.height()),
                    radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            float outer = Math.max(1f, Math.min(current.width() / base, current.height() / base));
            canvas.scale(outer, outer);
            float localWidth = current.width() / outer, localHeight = current.height() / outer;
            float aspect = Math.max(localWidth, localHeight) / base;
            canvas.save();
            canvas.translate((localWidth - base * aspect) * .5f,
                    (localHeight - base * aspect) * .5f);
            canvas.scale(aspect, aspect);
            background.setBounds(backgroundBounds);
            background.draw(canvas);
            canvas.restore();
            canvas.translate((localWidth - base) * .5f, (localHeight - base) * .5f);
            foreground.setBounds(foregroundBounds);
            foreground.draw(canvas);
        }
        canvas.restoreToCount(layer);
    }

    void release() { host.getOverlay().remove(this); }
}
