package com.ivanchan.launcher.combined.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Purpose: Preserve Home$3's selected-icon cleanup.
 * Invocation: Original return-to-Home animation end.
 * Contract: Only the owning class reference changes; original listener detach and field clear remain.
 * Verification: Generated from the original JADX callback and checked against pristine Smali;
 * Android compilation and UI behavior remain unverified.
 * Visual: Selected card has reached its original cell.
 */
class MiniOsReturnListener extends AnimatorListenerAdapter {
    final /* synthetic */ MiniOsAnimator this$0;

    MiniOsReturnListener(MiniOsAnimator home) {
        this.this$0 = home;
    }

    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator$AnimatorListener
    public void onAnimationEnd(Animator animator) {
        super.onAnimationEnd(animator);
        if (MiniOsAnimator.access$200(this.this$0) != null) {
            MiniOsAnimator.access$200(this.this$0).animate().setListener(null);
        }
        MiniOsAnimator.access$202(this.this$0, null);
    }
}
