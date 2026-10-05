package com.huna.ios.launcher.ui.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import com.huna.ios.launcher.util.Constant;

/* loaded from: /mnt/HC_Volume_105518598/agent-work/Codex/2026-10-01-decompile-nova-launcher-and-extract-how/minios-dex-input/classes6.dex */
class UITool$1$1 extends AnimatorListenerAdapter {
    final /* synthetic */ UITool$1 this$0;

    UITool$1$1(UITool$1 uITool$1) {
        this.this$0 = uITool$1;
    }

    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator$AnimatorListener
    public void onAnimationEnd(Animator animator) {
        super.onAnimationEnd(animator);
        if (this.this$0.val$action != null) {
            this.this$0.val$action.run();
        }
        this.this$0.val$view.animate().setDuration(Constant.DEFAULT_DURATION_ANIMATION);
    }
}
