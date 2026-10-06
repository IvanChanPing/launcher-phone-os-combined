package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Purpose: Bind the requested white icon card to MiniOS's original View animation.
 * Invocation: Controller open or return preparation with the actual source cell.
 * Contract: Standard ImageView and root ViewGroupOverlay own drawing. Bounds are host-local;
 * the independent drawable leaves vendor artwork unchanged. MiniOsAnimator owns opening;
 * the controller's grid clock owns return. The host removes both layers together.
 * Verification: Bounds arithmetic and original callback parity checks; Android/UI unverified.
 * Visual: White card matches icon corners, fills the viewport, then rounds again on return.
 */
final class IconOverlayView extends ImageView {
    private final ViewGroup host;
    private final View source;
    // Return-only scale at remaining 0; the open path keeps 1 so its corner math is unchanged.
    private float baseScaleX = 1f, baseScaleY = 1f;
    // Picture placement at uniform card scale; per-frame copy counters the card's uneven scale.
    private Matrix picture;
    private final Matrix framePicture = new Matrix();
    private boolean loggedLanding;
    private final Rect start;
    private final Path cardClip = new Path();
    private final float cornerFraction;

    IconOverlayView(Activity activity, ViewGroup host, View source, float cellHeight)
            throws ReflectiveOperationException {
        super(activity);
        this.host = host;
        this.source = source;
        // Original f3.s mask: 45-unit corners in a 180-unit square.
        cornerFraction = 45f / 180f;
        RectF bounds = LauncherAccess.artwork(source);
        LauncherAccess.toRoot(source, host).mapRect(bounds);
        if (bounds.width() <= 0 || bounds.height() <= 0)
            throw new IllegalArgumentException("Empty artwork bounds");
        start = new Rect(Math.round(bounds.left), Math.round(bounds.top),
                Math.round(bounds.right), Math.round(bounds.bottom));
        if (start.width() <= 0 || start.height() <= 0)
            throw new IllegalArgumentException("Empty rounded artwork bounds");
        Drawable original = LauncherAccess.drawable(source);
        if (original == null || original.getConstantState() == null)
            throw new IllegalArgumentException("Drawable has no independent state");
        Drawable icon = original.getConstantState().newDrawable(getResources()).mutate();
        icon.setState(original.getState()); icon.setLevel(original.getLevel());
        setImageDrawable(icon);
        // Draw the picture exactly where the real app_icon ImageView draws it relative to the
        // icon square, scaled with the card; the white card itself keeps the icon-square size.
        RectF art = LauncherAccess.artwork(source);
        RectF content = LauncherAccess.iconContent(source, original);
        if (content != null && icon.getIntrinsicWidth() > 0 && icon.getIntrinsicHeight() > 0) {
            float sx = start.width() / art.width(), sy = start.height() / art.height();
            picture = new Matrix();
            picture.setRectToRect(new RectF(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight()),
                    new RectF((content.left - art.left) * sx, (content.top - art.top) * sy,
                            (content.right - art.left) * sx, (content.bottom - art.top) * sy),
                    Matrix.ScaleToFit.FILL);
            setScaleType(ScaleType.MATRIX);
            setImageMatrix(picture);
            Log.i("CombinedReturn", "iconSquare=" + art.toShortString() + " drawn=" + content.toShortString());
        } else if (icon.getIntrinsicWidth() > 0 && icon.getIntrinsicHeight() > 0) {
            picture = new Matrix();
            picture.setRectToRect(new RectF(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight()),
                    new RectF(0, 0, start.width(), start.height()), Matrix.ScaleToFit.CENTER);
            setScaleType(ScaleType.MATRIX);
            setImageMatrix(picture);
        } else setScaleType(ScaleType.FIT_CENTER);
        setBackgroundColor(Color.WHITE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Native animation bypasses View setters; refresh the clip and logo fit from its existing
        // frame callback (ViewPropertyAnimator sets the frame's values before calling it).
        animate().setUpdateListener(frame -> { fitPicture(); invalidate(); });
    }

    /**
     * Purpose: Place the icon card above ancestor clipping in the host coordinate space.
     * Invocation: After grid capture, before MiniOS's animation starts.
     * Contract: Initial layout is the actual icon rectangle; fullscreen scale and translation
     * map its center and edges to the host viewport, without subtracting a screen-space origin.
     * Verification: Affine endpoint checks; real rendering remains unverified.
     * Visual: Starts directly over the tapped icon, above the sibling grid overlay.
     */
    void attach() {
        layout(start.left, start.top, start.right, start.bottom);
        host.getOverlay().add(this);
    }

    float fullScaleX() { return (float) host.getWidth() / start.width(); }
    float fullScaleY() { return (float) host.getHeight() / start.height(); }
    float fullX() { return host.getWidth() * .5f - start.exactCenterX(); }
    float fullY() { return host.getHeight() * .5f - start.exactCenterY(); }

    void open(Runnable launch) {
        MiniOsAnimator.createScaleInScaleOutAnim(this, launch,
                fullScaleX(), fullScaleY(), fullX(), fullY());
    }

    /**
     * Purpose: Apply the returning card's state without starting another animator.
     * Invocation: Controller preparation and each shared grid-clock update.
     * Contract: Remaining is 1 at full screen and 0 at the source cell. Reuse the existing
     * scale/translation endpoints and corner clipping. Only this transient overlay is changed.
     * Verification: Endpoint and common-frame source tests; phone playback unverified.
     * Visual: White rounded app card shrinks into its cell with the grid's completion.
     */
    void applyReturnRemaining(float remaining) {
        RectF end = landing();
        baseScaleX = end.width() / start.width();
        baseScaleY = end.height() / start.height();
        float endX = end.centerX() - start.exactCenterX();
        float endY = end.centerY() - start.exactCenterY();
        setScaleX(baseScaleX + (fullScaleX() - baseScaleX) * remaining);
        setScaleY(baseScaleY + (fullScaleY() - baseScaleY) * remaining);
        setTranslationX(endX + (fullX() - endX) * remaining);
        setTranslationY(endY + (fullY() - endY) * remaining);
        fitPicture();
        invalidate();
    }

    /**
     * Purpose: Keep the logo undistorted and tied to the card on open and return.
     * Invocation: Every return frame and every ViewPropertyAnimator frame of the open animation.
     * Contract: "Contain" fit (PlayCanvas fitMode / fitCenter): net picture scale is the uniform
     * min(scaleX, scaleY), centred on the card; the white card keeps its own scale. Identity when
     * the card scale is uniform (the .84 press and the icon endpoint), so those frames are unchanged.
     * Verification: Host tests; phone appearance unverified.
     * Visual: Logo moves and grows/shrinks with the card at the card's rate, never squashed.
     */
    private void fitPicture() {
        if (picture == null) return;
        float sx = getScaleX(), sy = getScaleY(), uniform = Math.min(sx, sy);
        framePicture.set(picture);
        if (sx > 0f && sy > 0f)
            framePicture.postScale(uniform / sx, uniform / sy, getWidth() * .5f, getHeight() * .5f);
        setImageMatrix(framePicture);
    }

    /**
     * Purpose: The rectangle the real icon artwork occupies once it is restored.
     * Invocation: Every return frame. Contract: no view's transform applies (settled layout, as AOSP
     * Launcher3 FloatingIconView ignoreTransform on close), so the card ends at the icon's
     * resting size and never undershoots it. On reflection failure, the captured start.
     * Verification: redroid logcat compares captured vs resting rects; phone unverified.
     * Visual: Card shrinks only to the icon's final size; no pop when the icon reappears.
     */
    private RectF landing() {
        try {
            // AOSP FloatingIconView close path (ignoreTransform): sum layout offsets minus
            // ancestor scroll up to the host, skipping every view's matrix.
            RectF rect = LauncherAccess.artwork(source);
            View view = source;
            while (view != host) {
                View parent = (View) view.getParent();
                rect.offset(view.getLeft() - parent.getScrollX(), view.getTop() - parent.getScrollY());
                view = parent;
            }
            if (rect.width() > 0f && rect.height() > 0f) {
                if (!loggedLanding) {
                    loggedLanding = true;
                    Log.i("CombinedReturn", "captured=" + start.toShortString() + " resting="
                            + rect.toShortString() + " sourceScale=" + source.getScaleX() + ","
                            + source.getScaleY() + " sourceTranslation=" + source.getTranslationX()
                            + "," + source.getTranslationY());
                }
                return rect;
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (!loggedLanding) { loggedLanding = true; Log.w("CombinedReturn", "landing", failure); }
        }
        return new RectF(start);
    }

    Rect launchBounds() { return new Rect(0, 0, host.getWidth(), host.getHeight()); }

    /**
     * Purpose: Match the white background and artwork to the selected icon's corners.
     * Invocation: View drawing during the existing MiniOS property animation.
     * Contract: Existing scale determines shape; no new clock. The .84 press keeps the
     * initial shape and full viewport scale flattens corners to fill the screen.
     * Reuse the Path and always restore the Canvas. Invalidate native transform changes.
     * Verification: Endpoint math and Java parsing; corrected phone visuals unverified.
     * Visual: Rounded icon card grows to a full white screen and rounds as it lands.
     */
    @Override public void draw(Canvas canvas) {
        float x = fullScaleX() > baseScaleX ? (getScaleX() - baseScaleX) / (fullScaleX() - baseScaleX) : 1f;
        float y = fullScaleY() > baseScaleY ? (getScaleY() - baseScaleY) / (fullScaleY() - baseScaleY) : 1f;
        float remaining = 1f - Math.max(0f, Math.min(1f, Math.max(x, y)));
        cardClip.reset();
        cardClip.addRoundRect(0f, 0f, getWidth(), getHeight(),
                getWidth() * cornerFraction * remaining,
                getHeight() * cornerFraction * remaining, Path.Direction.CW);
        int save = canvas.save();
        try {
            canvas.clipPath(cardClip);
            super.draw(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /**
     * Purpose: Release the host-owned transient card without firing its launch action.
     * Invocation: Existing controller generation-gated visual teardown.
     * Contract: Detach the original listener before cancelling the native animator.
     * Verification: Cancellation ordering source check; lifecycle UI unverified.
     * Visual: Restored source icon replaces the temporary card.
     */
    void release() {
        animate().setListener(null);
        animate().setUpdateListener(null);
        animate().cancel();
        host.getOverlay().remove(this);
    }
}
