.class Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1$1;
.super Ljava/lang/Object;
.source "AppItemViewLite.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;->onClick(Landroid/view/View;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$1:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;


# direct methods
.method constructor <init>(Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            null
        }
    .end annotation

    .line 242
    iput-object p1, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1$1;->this$1:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 3

    .line 245
    sget-object v0, Lcom/huna/ios/launcher/ui/activity/Home;->launcher:Lcom/huna/ios/launcher/ui/activity/Home;

    invoke-static {}, Lcom/huna/ios/launcher/manager/AppManager;->get()Lcom/huna/ios/launcher/manager/AppManager;

    move-result-object v1

    iget-object v2, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1$1;->this$1:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;

    iget-object v2, v2, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;->val$item:Lcom/huna/ios/launcher/model/Item;

    invoke-virtual {v1, v2}, Lcom/huna/ios/launcher/manager/AppManager;->findApp(Lcom/huna/ios/launcher/model/Item;)Lcom/huna/ios/launcher/model/App;

    move-result-object v1

    iget-object p0, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1$1;->this$1:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;

    iget-object p0, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;->this$0:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;

    invoke-virtual {p0}, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;->getView()Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite;

    move-result-object p0

    invoke-virtual {v0, v1, p0}, Lcom/huna/ios/launcher/ui/activity/Home;->startApp(Lcom/huna/ios/launcher/model/App;Landroid/view/View;)Z

    return-void
.end method
