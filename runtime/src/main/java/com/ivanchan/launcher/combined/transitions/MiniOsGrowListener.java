package com.ivanchan.launcher.combined.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Purpose: Preserve UITool$1's expansion callback.
 * Invocation: Original press-stage animation end.
 * Contract: Only fullscreen endpoint data and host class references are changed.
 * Verification: Generated from the original JADX callback and checked against pristine Smali;
 * Android compilation and UI behavior remain unverified.
 * Visual: Second stage expands to the full viewport using the original easing.
 */
class MiniOsGrowListener extends AnimatorListenerAdapter {
    final /* synthetic */ Runnable val$action;
    final /* synthetic */ long val$animTime;
    final /* synthetic */ View val$view;

    final float fullScaleX, fullScaleY, fullX, fullY;

    MiniOsGrowListener(View view, Runnable runnable, long j,
            float fullScaleX, float fullScaleY, float fullX, float fullY) {
        this.val$view = view;
        this.val$action = runnable;
        this.val$animTime = j;
        this.fullScaleX = fullScaleX; this.fullScaleY = fullScaleY;
        this.fullX = fullX; this.fullY = fullY;
    }

    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator$AnimatorListener
    public void onAnimationEnd(Animator animator) {
        super.onAnimationEnd(animator);
        this.val$view.animate().scaleX(this.fullScaleX).scaleY(this.fullScaleY).translationX(this.fullX).translationY(this.fullY).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new MiniOsLaunchListener(this)).setDuration(this.val$animTime).start();
    }
}
