.class Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;
.super Ljava/lang/Object;
.source "AppItemViewLite.java"

# interfaces
.implements Landroid/view/View$OnClickListener;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;->setAppItem(Lcom/huna/ios/launcher/model/Item;)Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;

.field final synthetic val$item:Lcom/huna/ios/launcher/model/Item;


# direct methods
.method constructor <init>(Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;Lcom/huna/ios/launcher/model/Item;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010,
            0x1010
        }
        names = {
            null,
            null
        }
    .end annotation

    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 239
    iput-object p1, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;->this$0:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;

    iput-object p2, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;->val$item:Lcom/huna/ios/launcher/model/Item;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public onClick(Landroid/view/View;)V
    .locals 1

    .line 242
    iget-object p1, p0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;->this$0:Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;

    invoke-virtual {p1}, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder;->getView()Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite;

    move-result-object p1

    new-instance v0, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1$1;

    invoke-direct {v0, p0}, Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1$1;-><init>(Lcom/huna/ios/launcher/ui/view/widget/AppItemViewLite$Builder$1;)V

    invoke-static {p1, v0}, Lcom/huna/ios/launcher/ui/util/UITool;->createScaleInScaleOutAnim(Landroid/view/View;Ljava/lang/Runnable;)V

    return-void
.end method
