# MiniOS icon-card transitions

The selected icon uses MiniOS's native View animation sequence. The other icons and
bottom bar keep the existing iLauncher snapshot animation. Unlock uses the grid only.

## Connect it to your launcher

1. Copy the entire `runtime` module and follow the module and `LauncherHost` setup in
   the README. Keep your launcher's existing app model and safe-launch method.
2. Supply the real artwork bounds and drawable for each icon. Bounds are local to the
   same cell View used for taps and scene membership; exclude its text label.
3. Supply the corner radius used by your renderer, divided by the artwork width:

   ```java
   @Override
   public float transitionIconCornerFraction(View icon) {
       return 0.25f;
   }
   ```

   `0.25f` is the example launcher's 45/180 rounded-square mask. Measure your own
   renderer instead of copying that number blindly. Use 0 for a square or .5 for a
   circle inside square artwork. Values must be finite and between 0 and .5.
   You can choose a different value per icon or surface. Arbitrary non-round-rectangle
   masks require their own host drawing adapter; this callback specifies round corners.
4. Forward the Application, Activity, model-ready and Home-intent events in the README.
   Workspace, folder, app-list and dock taps must reach the existing safe-launch hook.
5. `interceptLaunch` captures the grid and selected artwork. If it returns true, let
   the controller call `launchFromTransition`; do not also launch immediately.
   Use `consumeLaunchOptions` in your existing launch-options provider.
6. Keep the opened cell View available while the app is away. On return, the controller
   uses that same View if it remains visible in the same root. If the launcher rebuilt
   the cell, it skips the selected card and still animates the grid. The older Nova
   target-lookup and surface methods are retained in the interface but are unused here.
7. Test unlock, workspace opening, dock opening, Home button and Home gesture separately.
   Check both the initial handoff and the final landing frame for a shape jump.

## What happens on a tap

`MiniOsAnimator` retains the original .84 press phase, followed by the growth phase.
Each phase uses half of the default Android animator duration and the original
accelerate/decelerate easing. `MiniOsGrowListener` starts the growth;
`MiniOsLaunchListener` launches only after the white card reaches the full viewport.

The card's bounds begin at the selected artwork. Its final scale is viewport width
divided by artwork width, and viewport height divided by artwork height. Translation
aligns the two centers in the root's coordinate space.

## Corners and return

For timing changes without rebuilding, see [Live timing controls](LIVE_TIMING.md).
The values below are the defaults; live updates apply only between Home returns.

On return, one animation clock updates both the card and the grid in the same frame.
The card follows the outer icons' easing, with its endpoint mapped to the end of the
grid entrance. It no longer has a separate return animator or completion callback.
The fly-in stays at its initial frame until the card is 20% of the way from full-screen
size to icon size. This uses shrink progress, not 20% of elapsed time. The grid and
dock then run at their original speed, with their original delays and easing.
The phone calculates the card duration for the current scene: if its easing reaches
20% shrink at time fraction `q`, and the normal fly-in takes `G` milliseconds, the
card takes `G / (1-q)`. The fly-in starts that many milliseconds minus `G` later.
The duration rounds upward to whole milliseconds, so the gate is never early and is
less than one millisecond late. No frame-count estimate or fixed delay is needed.
Both overlays are removed and the original icon restored by the same completion
callback. Only the card's duration changes. Opening and unlock are unchanged.

`IconOverlayView.draw` clips the white background and drawable together. Radius comes
from the host and decreases continuously with the existing scale progress, reaching
zero at full-screen size. Return reads the same progress in reverse, so the radius is
back to the host's value when the icon lands. No extra clock or launch listener is added.

During opening, the native animator's update callback invalidates the clip. This matters
because Android can change rendering-node transforms without calling View scale setters.
Return invalidates the clip directly from the shared frame update. Release detaches
the opening callback before cancelling/removing the card.

The white card represents launcher artwork. Android controls the real app window;
this code does not capture or shrink the app's live contents.

## Verification

The example launcher baseline was compiled and tested by the user, who accepted its
motion and reported the square-versus-rounded corner mismatch. The subsequent corner
patch passed source checks and Java parsing; it still needs a fresh APK and phone test.

The generic runtime keeps `LauncherHost`, `LauncherScene`, and `LauncherGeometry` as
its boundary. It contains no launcher class names or reflection. You still need a
View bridge for renderers that draw many icons on one Canvas, Compose, Flutter or GL surface.
