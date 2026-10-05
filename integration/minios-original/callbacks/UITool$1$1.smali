.class Lcom/huna/ios/launcher/ui/util/UITool$1$1;
.super Landroid/animation/AnimatorListenerAdapter;
.source "UITool.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/huna/ios/launcher/ui/util/UITool$1;->onAnimationEnd(Landroid/animation/Animator;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/huna/ios/launcher/ui/util/UITool$1;


# direct methods
.method constructor <init>(Lcom/huna/ios/launcher/ui/util/UITool$1;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            null
        }
    .end annotation

    .line 66
    iput-object p1, p0, Lcom/huna/ios/launcher/ui/util/UITool$1$1;->this$0:Lcom/huna/ios/launcher/ui/util/UITool$1;

    invoke-direct {p0}, Landroid/animation/AnimatorListenerAdapter;-><init>()V

    return-void
.end method


# virtual methods
.method public onAnimationEnd(Landroid/animation/Animator;)V
    .locals 2

    .line 69
    invoke-super {p0, p1}, Landroid/animation/AnimatorListenerAdapter;->onAnimationEnd(Landroid/animation/Animator;)V

    .line 70
    iget-object p1, p0, Lcom/huna/ios/launcher/ui/util/UITool$1$1;->this$0:Lcom/huna/ios/launcher/ui/util/UITool$1;

    iget-object p1, p1, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$action:Ljava/lang/Runnable;

    if-eqz p1, :cond_0

    iget-object p1, p0, Lcom/huna/ios/launcher/ui/util/UITool$1$1;->this$0:Lcom/huna/ios/launcher/ui/util/UITool$1;

    iget-object p1, p1, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$action:Ljava/lang/Runnable;

    invoke-interface {p1}, Ljava/lang/Runnable;->run()V

    .line 72
    :cond_0
    iget-object p0, p0, Lcom/huna/ios/launcher/ui/util/UITool$1$1;->this$0:Lcom/huna/ios/launcher/ui/util/UITool$1;

    iget-object p0, p0, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$view:Landroid/view/View;

    invoke-virtual {p0}, Landroid/view/View;->animate()Landroid/view/ViewPropertyAnimator;

    move-result-object p0

    sget-wide v0, Lcom/huna/ios/launcher/util/Constant;->DEFAULT_DURATION_ANIMATION:J

    invoke-virtual {p0, v0, v1}, Landroid/view/ViewPropertyAnimator;->setDuration(J)Landroid/view/ViewPropertyAnimator;

    return-void
.end method
