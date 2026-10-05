package com.ivanchan.launcher.combined.transitions;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Purpose: Bind the requested white icon card to MiniOS's original View animation.
 * Invocation: Controller open or return preparation with the actual source cell.
 * Contract: Standard ImageView and root ViewGroupOverlay own drawing. Bounds are host-local;
 * the independent drawable leaves vendor artwork unchanged. MiniOsAnimator owns all timing,
 * easing, and animation callbacks. The host removes the card on completion or interruption.
 * Verification: Bounds arithmetic and original callback parity checks; Android/UI unverified.
 * Visual: White rectangle with app artwork, expanding from the icon to the viewport and back.
 */
final class IconOverlayView extends ImageView {
    private final ViewGroup host;
    private final Rect start;
    private final MiniOsAnimator motion = new MiniOsAnimator();

    IconOverlayView(Activity activity, ViewGroup host, View source, float cellHeight)
            throws ReflectiveOperationException {
        super(activity);
        this.host = host;
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
        setScaleType(ScaleType.FIT_CENTER);
        setBackgroundColor(Color.WHITE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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

    void returnHome() {
        motion.returnHome(this, fullScaleX(), fullScaleY(), fullX(), fullY());
    }

    Rect launchBounds() { return new Rect(0, 0, host.getWidth(), host.getHeight()); }

    /**
     * Purpose: Release the host-owned transient card without firing its launch action.
     * Invocation: Existing controller generation-gated visual teardown.
     * Contract: Detach the original listener before cancelling the native animator.
     * Verification: Cancellation ordering source check; lifecycle UI unverified.
     * Visual: Restored source icon replaces the temporary card.
     */
    void release() {
        animate().setListener(null);
        animate().cancel();
        host.getOverlay().remove(this);
    }
}
