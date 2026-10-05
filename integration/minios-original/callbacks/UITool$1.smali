.class Lcom/huna/ios/launcher/ui/util/UITool$1;
.super Landroid/animation/AnimatorListenerAdapter;
.source "UITool.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/huna/ios/launcher/ui/util/UITool;->createScaleInScaleOutAnim(Landroid/view/View;Ljava/lang/Runnable;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$action:Ljava/lang/Runnable;

.field final synthetic val$animTime:J

.field final synthetic val$view:Landroid/view/View;


# direct methods
.method constructor <init>(Landroid/view/View;Ljava/lang/Runnable;J)V
    .locals 0

    .line 62
    iput-object p1, p0, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$view:Landroid/view/View;

    iput-object p2, p0, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$action:Ljava/lang/Runnable;

    iput-wide p3, p0, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$animTime:J

    invoke-direct {p0}, Landroid/animation/AnimatorListenerAdapter;-><init>()V

    return-void
.end method


# virtual methods
.method public onAnimationEnd(Landroid/animation/Animator;)V
    .locals 2

    .line 65
    invoke-super {p0, p1}, Landroid/animation/AnimatorListenerAdapter;->onAnimationEnd(Landroid/animation/Animator;)V

    .line 66
    iget-object p1, p0, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$view:Landroid/view/View;

    invoke-virtual {p1}, Landroid/view/View;->animate()Landroid/view/ViewPropertyAnimator;

    move-result-object p1

    const/high16 v0, 0x3f800000    # 1.0f

    invoke-virtual {p1, v0}, Landroid/view/ViewPropertyAnimator;->scaleX(F)Landroid/view/ViewPropertyAnimator;

    move-result-object p1

    invoke-virtual {p1, v0}, Landroid/view/ViewPropertyAnimator;->scaleY(F)Landroid/view/ViewPropertyAnimator;

    move-result-object p1

    new-instance v0, Landroid/view/animation/AccelerateDecelerateInterpolator;

    invoke-direct {v0}, Landroid/view/animation/AccelerateDecelerateInterpolator;-><init>()V

    invoke-virtual {p1, v0}, Landroid/view/ViewPropertyAnimator;->setInterpolator(Landroid/animation/TimeInterpolator;)Landroid/view/ViewPropertyAnimator;

    move-result-object p1

    new-instance v0, Lcom/huna/ios/launcher/ui/util/UITool$1$1;

    invoke-direct {v0, p0}, Lcom/huna/ios/launcher/ui/util/UITool$1$1;-><init>(Lcom/huna/ios/launcher/ui/util/UITool$1;)V

    invoke-virtual {p1, v0}, Landroid/view/ViewPropertyAnimator;->setListener(Landroid/animation/Animator$AnimatorListener;)Landroid/view/ViewPropertyAnimator;

    move-result-object p1

    iget-wide v0, p0, Lcom/huna/ios/launcher/ui/util/UITool$1;->val$animTime:J

    .line 74
    invoke-virtual {p1, v0, v1}, Landroid/view/ViewPropertyAnimator;->setDuration(J)Landroid/view/ViewPropertyAnimator;

    move-result-object p0

    invoke-virtual {p0}, Landroid/view/ViewPropertyAnimator;->start()V

    return-void
.end method
