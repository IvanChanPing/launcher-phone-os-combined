.method public startApp(Lcom/huna/ios/launcher/model/App;Landroid/view/View;)Z
    .locals 8

    const/4 v0, 0x0

    if-nez p1, :cond_0

    const p1, 0x7f1307a9

    .line 384
    invoke-static {p0, p1}, Lcom/huna/ios/launcher/ui/util/UITool;->toast(Landroid/content/Context;I)V

    return v0

    .line 387
    :cond_0
    new-instance v1, Ljava/lang/StringBuilder;

    const-string v2, "Home startApp "

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lcom/home/base/util/Log;->d(Ljava/lang/String;)V

    .line 389
    invoke-static {}, Lcom/huna/ios/launcher/manager/DatabaseManager;->get()Lcom/huna/ios/launcher/manager/DatabaseManager;

    move-result-object v1

    sget-object v2, Lcom/huna/ios/launcher/util/Constant$RecentState;->OPEN:Lcom/huna/ios/launcher/util/Constant$RecentState;

    invoke-virtual {v1, p1, v2}, Lcom/huna/ios/launcher/manager/DatabaseManager;->addRecentApp(Lcom/huna/ios/launcher/model/App;Lcom/huna/ios/launcher/util/Constant$RecentState;)V

    const/high16 v1, 0x10000000

    const/4 v2, 0x1

    .line 392
    :try_start_0
    invoke-virtual {p0}, Lcom/huna/ios/launcher/ui/activity/Home;->getPackageName()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {p1}, Lcom/huna/ios/launcher/model/App;->getPackageName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_1

    invoke-virtual {p1}, Lcom/huna/ios/launcher/model/App;->getClassName()Ljava/lang/String;

    move-result-object v3

    const-class v4, Lcom/huna/ios/launcher/ui/activity/SplashActivity;

    invoke-virtual {v4}, Ljava/lang/Class;->getName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_1

    .line 393
    new-instance v3, Landroid/content/Intent;

    const-class v4, Lcom/huna/ios/launcher/ui/activity/settings/SettingsActivity;

    invoke-direct {v3, p0, v4}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 394
    invoke-virtual {v3, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 395
    invoke-static {p2}, Lcom/huna/ios/launcher/util/Tool;->getActivityAnimationOpts(Landroid/view/View;)Landroid/os/Bundle;

    move-result-object v4

    invoke-virtual {p0, v3, v4}, Lcom/huna/ios/launcher/ui/activity/Home;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    return v2

    :catch_0
    move-exception v3

    .line 399
    const-string v4, "startApp 0"

    invoke-static {v4, v3}, Lcom/home/base/util/Log;->e(Ljava/lang/String;Ljava/lang/Throwable;)V

    .line 403
    :cond_1
    :try_start_1
    const-string v3, "launcherapps"

    invoke-virtual {p0, v3}, Lcom/huna/ios/launcher/ui/activity/Home;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/content/pm/LauncherApps;

    .line 404
    invoke-virtual {p1}, Lcom/huna/ios/launcher/model/App;->getComponentName()Landroid/content/ComponentName;

    move-result-object v4

    invoke-virtual {p1}, Lcom/huna/ios/launcher/model/App;->getUserHandle()Landroid/os/UserHandle;

    move-result-object v5

    invoke-static {p2}, Lcom/huna/ios/launcher/util/Tool;->getActivityAnimationOpts(Landroid/view/View;)Landroid/os/Bundle;

    move-result-object v6

    const/4 v7, 0x0

    invoke-virtual {v3, v4, v5, v7, v6}, Landroid/content/pm/LauncherApps;->startMainActivity(Landroid/content/ComponentName;Landroid/os/UserHandle;Landroid/graphics/Rect;Landroid/os/Bundle;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    return v2

    :catch_1
    move-exception v3

    .line 408
    new-instance v4, Ljava/lang/StringBuilder;

    const-string v5, "onStartApp 1 "

    invoke-direct {v4, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v3}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Lcom/home/base/util/Log;->e(Ljava/lang/String;)V

    .line 410
    :try_start_2
    invoke-virtual {p1}, Lcom/huna/ios/launcher/model/App;->getIntent()Landroid/content/Intent;

    move-result-object v3

    .line 412
    invoke-virtual {v3, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 413
    invoke-static {p2}, Lcom/huna/ios/launcher/util/Tool;->getActivityAnimationOpts(Landroid/view/View;)Landroid/os/Bundle;

    move-result-object v1

    invoke-virtual {p0, v3, v1}, Lcom/huna/ios/launcher/ui/activity/Home;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_2

    return v2

    :catch_2
    move-exception v1

    .line 416
    new-instance v3, Ljava/lang/StringBuilder;

    const-string v4, "onStartApp 2 "

    invoke-direct {v3, v4}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lcom/home/base/util/Log;->e(Ljava/lang/String;)V

    .line 418
    :try_start_3
    invoke-virtual {p0}, Lcom/huna/ios/launcher/ui/activity/Home;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v1

    invoke-virtual {p1}, Lcom/huna/ios/launcher/model/App;->getPackageName()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v1, p1}, Landroid/content/pm/PackageManager;->getLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object p1

    .line 419
    invoke-static {p2}, Lcom/huna/ios/launcher/util/Tool;->getActivityAnimationOpts(Landroid/view/View;)Landroid/os/Bundle;

    move-result-object p2

    invoke-virtual {p0, p1, p2}, Lcom/huna/ios/launcher/ui/activity/Home;->startActivity(Landroid/content/Intent;Landroid/os/Bundle;)V
    :try_end_3
    .catch Ljava/lang/Exception; {:try_start_3 .. :try_end_3} :catch_3

    return v2

    :catch_3
    move-exception p1

    .line 422
    const-string p2, "onStartApp 3"

    invoke-static {p2, p1}, Lcom/home/base/util/Log;->e(Ljava/lang/String;Ljava/lang/Throwable;)V

    const p1, 0x7f1307a8

    .line 427
    invoke-static {p0, p1}, Lcom/huna/ios/launcher/ui/util/UITool;->toast(Landroid/content/Context;I)V

    return v0
.end method
