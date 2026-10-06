package com.ivanchan.launcher.combined.transitions;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Purpose: Carry MiniOS's original native View animation sequence into a launcher.
 * Invocation: A host places an icon card in its viewport, then supplies fullscreen scale/position.
 * Contract: Original .84 press phase, half-duration stages, easing, launch action ordering, and
 * return listener cleanup are retained. Fullscreen bounds are the explicit user-authorized change.
 * The host owns the card, source identity, attachment, lifecycle cancellation, and sibling grid.
 * Verification: Original method/callback copies have byte comparisons; Java parsing is source-only.
 * Visual: White icon card grows across the viewport before the app starts; return shrinks into its cell.
 */
public final class MiniOsAnimator {
    // Constant.<clinit>: new ValueAnimator().getDuration(), with no invented timing number.
    static final long DEFAULT_DURATION_ANIMATION = new ValueAnimator().getDuration();
    private View itemAnimationStart;

    /**
     * Purpose: Original UITool.createScaleInScaleOutAnim with fullscreen endpoint bindings.
     * Invocation: After the host's icon card is attached and laid out.
     * Contract: The launch Runnable remains in the original second animation-end callback.
     * Verification: Native call sequence compared with UITool and its two original listeners.
     * Visual: .84 press scale followed by growth to the supplied full viewport.
     */
    public static void createScaleInScaleOutAnim(View view, Runnable runnable,
            float fullScaleX, float fullScaleY, float fullX, float fullY) {
        long j = DEFAULT_DURATION_ANIMATION / 2;
        view.animate().scaleX(0.84f).scaleY(0.84f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new MiniOsGrowListener(view, runnable, j, fullScaleX, fullScaleY, fullX, fullY)).setDuration(j).start();
    }

    /**
     * Purpose: Original Home.onResume selected-icon scale and Home$3 cleanup.
     * Invocation: Host return-to-Home preparation, alongside the sibling grid.
     * Contract: Original 1.4 initial extent is replaced by the matching full viewport extent.
     * Added translation endpoints connect that viewport to the original card cell; no new easing.
     * Verification: Original Home onResume instructions and Home$3 read; device UI unverified.
     * Visual: Full white icon card shrinks back into its source cell and remains there for the grid.
     */
    public void returnHome(View view, float fullScaleX, float fullScaleY,
            float fullX, float fullY) {
        returnHome(view, fullScaleX, fullScaleY, fullX, fullY,
                DEFAULT_DURATION_ANIMATION, null);
    }

    /**
     * Purpose: Match selected-card return timing to the Home grid entrance.
     * Invocation: IconOverlayView supplies the captured grid duration and start callback.
     * Contract: Preserve the original endpoints, default easing and return listener.
     * Explicit duration replaces the unrelated Android default; the native start action starts
     * the grid clock. No delay is inherited. Existing host cancellation clears pending actions.
     * Verification: Source timing/caller checks and Java parsing; phone playback untested.
     * Visual: Full-screen card continues shrinking throughout the grid entrance.
     */
    public void returnHome(View view, float fullScaleX, float fullScaleY,
            float fullX, float fullY, long duration, Runnable startGrid) {
        this.itemAnimationStart = view;
        this.itemAnimationStart.setScaleX(fullScaleX);
        this.itemAnimationStart.setScaleY(fullScaleY);
        this.itemAnimationStart.setTranslationX(fullX);
        this.itemAnimationStart.setTranslationY(fullY);
        this.itemAnimationStart.animate().scaleX(1.0f).scaleY(1.0f)
                .translationX(0f).translationY(0f)
                .setDuration(duration).setStartDelay(0).withStartAction(startGrid)
                .setListener(new MiniOsReturnListener(this)).start();
    }

    static View access$200(MiniOsAnimator owner) { return owner.itemAnimationStart; }
    static View access$202(MiniOsAnimator owner, View view) {
        owner.itemAnimationStart = view; return view;
    }
}
