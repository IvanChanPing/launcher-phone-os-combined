package com.ivanchan.launcher.combined.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Purpose: Preserve UITool$1$1's original app-launch action.
 * Invocation: Original growth-stage animation end.
 * Contract: Original action runs before restoring the animator's duration.
 * Verification: Generated from the original JADX callback and checked against pristine Smali;
 * Android compilation and UI behavior remain unverified.
 * Visual: App starts over the already expanded white card.
 */
class MiniOsLaunchListener extends AnimatorListenerAdapter {
    final /* synthetic */ MiniOsGrowListener this$0;

    MiniOsLaunchListener(MiniOsGrowListener uITool$1) {
        this.this$0 = uITool$1;
    }

    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator$AnimatorListener
    public void onAnimationEnd(Animator animator) {
        super.onAnimationEnd(animator);
        if (this.this$0.val$action != null) {
            this.this$0.val$action.run();
        }
        this.this$0.val$view.animate().setDuration(MiniOsAnimator.DEFAULT_DURATION_ANIMATION);
    }
}
