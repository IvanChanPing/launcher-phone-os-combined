package com.huna.ios.launcher.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

/* loaded from: /mnt/HC_Volume_105518598/agent-work/Codex/2026-10-01-decompile-nova-launcher-and-extract-how/minios-dex-input/classes6.dex */
class Home$3 extends AnimatorListenerAdapter {
    final /* synthetic */ Home this$0;

    Home$3(Home home) {
        this.this$0 = home;
    }

    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator$AnimatorListener
    public void onAnimationEnd(Animator animator) {
        super.onAnimationEnd(animator);
        if (Home.access$200(this.this$0) != null) {
            Home.access$200(this.this$0).animate().setListener(null);
        }
        Home.access$202(this.this$0, null);
    }
}
