.class Lcom/huna/ios/launcher/ui/activity/Home$3;
.super Landroid/animation/AnimatorListenerAdapter;
.source "Home.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/huna/ios/launcher/ui/activity/Home;->onResume()V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/huna/ios/launcher/ui/activity/Home;


# direct methods
.method constructor <init>(Lcom/huna/ios/launcher/ui/activity/Home;)V
    .locals 0
    .annotation system Ldalvik/annotation/MethodParameters;
        accessFlags = {
            0x8010
        }
        names = {
            null
        }
    .end annotation

    .line 753
    iput-object p1, p0, Lcom/huna/ios/launcher/ui/activity/Home$3;->this$0:Lcom/huna/ios/launcher/ui/activity/Home;

    invoke-direct {p0}, Landroid/animation/AnimatorListenerAdapter;-><init>()V

    return-void
.end method


# virtual methods
.method public onAnimationEnd(Landroid/animation/Animator;)V
    .locals 1

    .line 756
    invoke-super {p0, p1}, Landroid/animation/AnimatorListenerAdapter;->onAnimationEnd(Landroid/animation/Animator;)V

    .line 757
    iget-object p1, p0, Lcom/huna/ios/launcher/ui/activity/Home$3;->this$0:Lcom/huna/ios/launcher/ui/activity/Home;

    invoke-static {p1}, Lcom/huna/ios/launcher/ui/activity/Home;->access$200(Lcom/huna/ios/launcher/ui/activity/Home;)Landroid/view/View;

    move-result-object p1

    const/4 v0, 0x0

    if-eqz p1, :cond_0

    iget-object p1, p0, Lcom/huna/ios/launcher/ui/activity/Home$3;->this$0:Lcom/huna/ios/launcher/ui/activity/Home;

    invoke-static {p1}, Lcom/huna/ios/launcher/ui/activity/Home;->access$200(Lcom/huna/ios/launcher/ui/activity/Home;)Landroid/view/View;

    move-result-object p1

    invoke-virtual {p1}, Landroid/view/View;->animate()Landroid/view/ViewPropertyAnimator;

    move-result-object p1

    invoke-virtual {p1, v0}, Landroid/view/ViewPropertyAnimator;->setListener(Landroid/animation/Animator$AnimatorListener;)Landroid/view/ViewPropertyAnimator;

    .line 758
    :cond_0
    iget-object p0, p0, Lcom/huna/ios/launcher/ui/activity/Home$3;->this$0:Lcom/huna/ios/launcher/ui/activity/Home;

    invoke-static {p0, v0}, Lcom/huna/ios/launcher/ui/activity/Home;->access$202(Lcom/huna/ios/launcher/ui/activity/Home;Landroid/view/View;)Landroid/view/View;

    return-void
.end method
