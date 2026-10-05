package com.huna.ios.launcher.ui.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/* loaded from: /mnt/HC_Volume_105518598/agent-work/Codex/2026-10-01-decompile-nova-launcher-and-extract-how/minios-dex-input/classes6.dex */
class UITool$1 extends AnimatorListenerAdapter {
    final /* synthetic */ Runnable val$action;
    final /* synthetic */ long val$animTime;
    final /* synthetic */ View val$view;

    UITool$1(View view, Runnable runnable, long j) {
        this.val$view = view;
        this.val$action = runnable;
        this.val$animTime = j;
    }

    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator$AnimatorListener
    public void onAnimationEnd(Animator animator) {
        super.onAnimationEnd(animator);
        this.val$view.animate().scaleX(1.0f).scaleY(1.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new UITool$1$1(this)).setDuration(this.val$animTime).start();
    }
}
