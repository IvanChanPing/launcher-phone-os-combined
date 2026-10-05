.method public animationStartApp(Landroid/view/View;)V
    .locals 2

    if-eqz p1, :cond_1

    .line 373
    iget-object v0, p0, Lcom/huna/ios/launcher/ui/activity/Home;->itemAnimationStart:Landroid/view/View;

    if-eqz v0, :cond_0

    const/high16 v1, 0x3f800000    # 1.0f

    .line 374
    invoke-virtual {v0, v1}, Landroid/view/View;->setScaleX(F)V

    .line 375
    iget-object v0, p0, Lcom/huna/ios/launcher/ui/activity/Home;->itemAnimationStart:Landroid/view/View;

    invoke-virtual {v0, v1}, Landroid/view/View;->setScaleY(F)V

    .line 377
    :cond_0
    iput-object p1, p0, Lcom/huna/ios/launcher/ui/activity/Home;->itemAnimationStart:Landroid/view/View;

    .line 379
    :cond_1
    iget-object p0, p0, Lcom/huna/ios/launcher/ui/activity/Home;->binding:Lcom/mini/os/launcher/databinding/ActivityHomeBinding;

    iget-object p0, p0, Lcom/mini/os/launcher/databinding/ActivityHomeBinding;->clContent:Landroidx/constraintlayout/widget/ConstraintLayout;

    invoke-virtual {p0}, Landroidx/constraintlayout/widget/ConstraintLayout;->animate()Landroid/view/ViewPropertyAnimator;

    move-result-object p0

    const p1, 0x3f75c28f    # 0.96f

    invoke-virtual {p0, p1}, Landroid/view/ViewPropertyAnimator;->scaleX(F)Landroid/view/ViewPropertyAnimator;

    move-result-object p0

    invoke-virtual {p0, p1}, Landroid/view/ViewPropertyAnimator;->scaleY(F)Landroid/view/ViewPropertyAnimator;

    move-result-object p0

    const/4 p1, 0x0

    invoke-virtual {p0, p1}, Landroid/view/ViewPropertyAnimator;->setListener(Landroid/animation/Animator$AnimatorListener;)Landroid/view/ViewPropertyAnimator;

    move-result-object p0

    invoke-virtual {p0}, Landroid/view/ViewPropertyAnimator;->start()V

    return-void
.end method
