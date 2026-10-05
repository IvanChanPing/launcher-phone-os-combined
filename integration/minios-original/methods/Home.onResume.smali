.method protected onResume()V
    .locals 6

    .line 725
    invoke-super {p0}, Lcom/home/base/activity/BaseAdsActivity;->onResume()V

    .line 726
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    const/4 v2, 0x0

    .line 729
    iput-boolean v2, p0, Lcom/huna/ios/launcher/ui/activity/Home;->isStop:Z

    const-wide/16 v3, 0x0

    .line 730
    iput-wide v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->timeStop:J

    .line 731
    new-instance v3, Ljava/lang/StringBuilder;

    const-string v4, "onResume doneLoading: "

    invoke-direct {v3, v4}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    iget-boolean v4, p0, Lcom/huna/ios/launcher/ui/activity/Home;->doneLoading:Z

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    const-string v4, " "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Lcom/home/base/util/Log;->v(Ljava/lang/String;)V

    .line 734
    iget-boolean v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->appRestartRequired:Z

    if-eqz v3, :cond_0

    .line 735
    iput-boolean v2, p0, Lcom/huna/ios/launcher/ui/activity/Home;->appRestartRequired:Z

    .line 736
    new-instance v0, Ljava/lang/StringBuilder;

    const-string v1, "Home recreate "

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lcom/home/base/util/Log;->w(Ljava/lang/String;)V

    .line 737
    invoke-virtual {p0}, Lcom/huna/ios/launcher/ui/activity/Home;->recreate()V

    return-void

    .line 742
    :cond_0
    invoke-static {}, Lcom/huna/ios/launcher/manager/WidgetManager;->get()Lcom/huna/ios/launcher/manager/WidgetManager;

    .line 743
    invoke-direct {p0}, Lcom/huna/ios/launcher/ui/activity/Home;->fullScreen()V

    .line 746
    iget-object v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->binding:Lcom/mini/os/launcher/databinding/ActivityHomeBinding;

    iget-object v3, v3, Lcom/mini/os/launcher/databinding/ActivityHomeBinding;->clContent:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3}, Landroidx/constraintlayout/widget/ConstraintLayout;->getScaleX()F

    move-result v3

    const/high16 v4, 0x3f800000    # 1.0f

    cmpl-float v3, v3, v4

    if-nez v3, :cond_1

    iget-object v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->binding:Lcom/mini/os/launcher/databinding/ActivityHomeBinding;

    iget-object v3, v3, Lcom/mini/os/launcher/databinding/ActivityHomeBinding;->clContent:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3}, Landroidx/constraintlayout/widget/ConstraintLayout;->getScaleY()F

    move-result v3

    cmpl-float v3, v3, v4

    if-eqz v3, :cond_2

    .line 747
    :cond_1
    iget-object v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->binding:Lcom/mini/os/launcher/databinding/ActivityHomeBinding;

    iget-object v3, v3, Lcom/mini/os/launcher/databinding/ActivityHomeBinding;->clContent:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {v3}, Landroidx/constraintlayout/widget/ConstraintLayout;->animate()Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    invoke-virtual {v3, v4}, Landroid/view/ViewPropertyAnimator;->scaleX(F)Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    invoke-virtual {v3, v4}, Landroid/view/ViewPropertyAnimator;->scaleY(F)Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    const/4 v5, 0x0

    invoke-virtual {v3, v5}, Landroid/view/ViewPropertyAnimator;->setListener(Landroid/animation/Animator$AnimatorListener;)Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    invoke-virtual {v3}, Landroid/view/ViewPropertyAnimator;->start()V

    .line 749
    :cond_2
    iget-object v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->itemAnimationStart:Landroid/view/View;

    if-eqz v3, :cond_3

    const v5, 0x3fb33333    # 1.4f

    .line 750
    invoke-virtual {v3, v5}, Landroid/view/View;->setScaleX(F)V

    .line 751
    iget-object v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->itemAnimationStart:Landroid/view/View;

    invoke-virtual {v3, v5}, Landroid/view/View;->setScaleY(F)V

    .line 753
    iget-object v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->itemAnimationStart:Landroid/view/View;

    invoke-virtual {v3}, Landroid/view/View;->animate()Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    invoke-virtual {v3, v4}, Landroid/view/ViewPropertyAnimator;->scaleX(F)Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    invoke-virtual {v3, v4}, Landroid/view/ViewPropertyAnimator;->scaleY(F)Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    new-instance v4, Lcom/huna/ios/launcher/ui/activity/Home$3;

    invoke-direct {v4, p0}, Lcom/huna/ios/launcher/ui/activity/Home$3;-><init>(Lcom/huna/ios/launcher/ui/activity/Home;)V

    invoke-virtual {v3, v4}, Landroid/view/ViewPropertyAnimator;->setListener(Landroid/animation/Animator$AnimatorListener;)Landroid/view/ViewPropertyAnimator;

    move-result-object v3

    .line 760
    invoke-virtual {v3}, Landroid/view/ViewPropertyAnimator;->start()V

    .line 764
    :cond_3
    iget-boolean v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->doneLoading:Z

    if-eqz v3, :cond_5

    .line 766
    iput-boolean v2, p0, Lcom/huna/ios/launcher/ui/activity/Home;->disableSwipe:Z

    .line 770
    invoke-static {}, Lcom/huna/ios/launcher/util/AppSettings;->get()Lcom/huna/ios/launcher/util/AppSettings;

    move-result-object v2

    invoke-virtual {v2}, Lcom/huna/ios/launcher/util/AppSettings;->notificationsBadge()Z

    move-result v2

    if-eqz v2, :cond_4

    invoke-static {p0}, Lcom/huna/ios/launcher/util/NotificationEnabledUtil;->hasPermission(Landroid/content/Context;)Z

    move-result v2

    if-eqz v2, :cond_4

    sget-object v2, Lcom/huna/ios/launcher/service/NotificationListenerExt;->instance:Lcom/huna/ios/launcher/service/NotificationListenerExt;

    if-eqz v2, :cond_4

    .line 771
    sget-object v2, Lcom/huna/ios/launcher/service/NotificationListenerExt;->instance:Lcom/huna/ios/launcher/service/NotificationListenerExt;

    invoke-virtual {v2}, Lcom/huna/ios/launcher/service/NotificationListenerExt;->updateCurrentNotifications()V

    :cond_4
    const/4 v2, 0x1

    .line 775
    invoke-direct {p0, v2, v0, v1}, Lcom/huna/ios/launcher/ui/activity/Home;->resumeExt(ZJ)V

    :cond_5
    return-void
.end method
