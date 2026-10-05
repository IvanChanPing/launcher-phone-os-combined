.method protected onNewIntent(Landroid/content/Intent;)V
    .locals 5

    .line 861
    invoke-super {p0, p1}, Lcom/home/base/activity/BaseAdsActivity;->onNewIntent(Landroid/content/Intent;)V

    .line 862
    new-instance p1, Ljava/lang/StringBuilder;

    const-string v0, "onNewIntent "

    invoke-direct {p1, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {p1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v0, "  "

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-boolean v0, p0, Lcom/huna/ios/launcher/ui/activity/Home;->isStop:Z

    invoke-virtual {p1, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {p1}, Lcom/home/base/util/Log;->d(Ljava/lang/String;)V

    .line 863
    iget-boolean p1, p0, Lcom/huna/ios/launcher/ui/activity/Home;->doneLoading:Z

    if-eqz p1, :cond_2

    const/16 p1, 0x3034

    .line 864
    invoke-static {p0, p1}, Lcom/huna/ios/launcher/manager/PermissionManager;->requestExternalStorage(Landroid/app/Activity;I)V

    .line 865
    iget-boolean p1, p0, Lcom/huna/ios/launcher/ui/activity/Home;->isStop:Z

    const/4 v0, 0x0

    if-eqz p1, :cond_1

    .line 868
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v1

    iget-wide v3, p0, Lcom/huna/ios/launcher/ui/activity/Home;->timeStop:J

    sub-long/2addr v1, v3

    const-wide/16 v3, 0x7530

    cmp-long p1, v1, v3

    if-ltz p1, :cond_0

    .line 869
    invoke-virtual {p0, v0, v0, v0}, Lcom/huna/ios/launcher/ui/activity/Home;->onBackPressedExt(ZZZ)Z

    :cond_0
    return-void

    :cond_1
    const/4 p1, 0x1

    .line 872
    invoke-virtual {p0, p1, p1, v0}, Lcom/huna/ios/launcher/ui/activity/Home;->onBackPressedExt(ZZZ)Z

    return-void

    .line 874
    :cond_2
    const-string p0, "doneloading false -------"

    invoke-static {p0}, Lcom/home/base/util/Log;->d(Ljava/lang/String;)V

    return-void
.end method
