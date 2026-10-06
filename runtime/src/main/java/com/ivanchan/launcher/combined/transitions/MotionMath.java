package com.ivanchan.launcher.combined.transitions;

/**
 * Purpose: Keep reference timing and grid arithmetic independent of Android rendering.
 * Invocation: SnapshotGridView and IconOverlayView evaluate these functions on one linear clock.
 * Contract: All times are milliseconds; supported iLauncher grids preserve their original indices.
 * Other grids use the documented nearest-middle-cell extension; sparse scenes use a 650 ms floor
 * only when the reference endpoint would not leave a positive dock interval.
 * Verification: Golden timing/grid checks and host-only arithmetic tests; pixels require a device.
 * Visual: Center groups settle first; the dock starts at 300 ms and shares the scene endpoint.
 */
public final class MotionMath {
    public static final int OPEN_MS = 450;
    public static final int WINDOW_RETURN_MS = 225;
    public static final int CROP_MS = 375;
    public static final int STRIP_START_MS = 300;
    public static final int UNLOCK_DELAY_MS = 60;
    public static final int RETURN_DELAY_MS = 35;
    // User-selected movement threshold: 20% from the full-screen card toward its icon size.
    public static final float RETURN_GRID_START_SHRINK = .20f;
    private static final int[] DELAYS = {0, 30, 45, 45, 45, 45, 45};
    private static final int[] DURATIONS = {350, 750, 1070, 1310, 1470, 1550, 1550};

    private MotionMath() {}

    public static float clamp(float x) { return Math.max(0f, Math.min(1f, x)); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static int delay(int ring) { return DELAYS[Math.max(0, Math.min(6, ring))]; }
    public static int duration(int ring) { return DURATIONS[Math.max(0, Math.min(6, ring))]; }

    public static int centerRow(int columns, int rows) {
        if (columns == 4 && rows >= 4 && rows <= 6) return rows == 6 ? 2 : 1;
        if (columns == 5 && rows == 4) return 1;
        return Math.max(0, (rows - 1) / 2);
    }

    public static int ring(int column, int row, int columns, int rows) {
        int left = Math.max(0, (columns - 1) / 2);
        int right = Math.max(left, columns / 2);
        int horizontal = Math.min(Math.abs(column - left), Math.abs(column - right));
        return Math.min(6, horizontal + Math.abs(row - centerRow(columns, rows)));
    }

    public static int sceneDuration(int maxRing) {
        int reference = delay(maxRing) + duration(maxRing) - 500;
        return reference > STRIP_START_MS ? reference : 650;
    }

    public static float remaining(int ring, float elapsed) {
        float inverse = 1f - clamp((elapsed - delay(ring)) / duration(ring));
        float square = inverse * inverse;
        return square * square * square * square;
    }

    public static float stripRemaining(float elapsed, int sceneDuration) {
        return stripRemaining(elapsed, sceneDuration, STRIP_START_MS);
    }

    /** Purpose: Apply a captured live dock delay without changing its endpoint or easing.
     * Invocation: SnapshotGridView drawing. Contract: Start precedes end; times use the original
     * grid timeline. Verification: Default parity and varied-delay host tests.
     * Visual: Bottom dock waits at the viewport edge, then slides to its normal position.
     */
    public static float stripRemaining(float elapsed, int sceneDuration, int startMs) {
        float inverse = 1f - clamp((elapsed - startMs)
                / Math.max(1f, sceneDuration - startMs));
        float square = inverse * inverse;
        return square * square * square * square;
    }

    /** Purpose: Finish the selected card exactly at the existing grid scene endpoint.
     * Invocation: SnapshotGridView supplies its outermost ring and shared elapsed time.
     * Contract: Reuse remaining(), not a second cosine curve. The original scene truncates
     * the outer ring's tail, so subtract that tail and normalize the range to [1,0].
     * A sparse scene's 650 ms floor can outlast its ring; stretch only the card's sampling
     * interval in that case. Grid, dock, opening tables and their clocks are unchanged.
     * Verification: Host Java tests cover every ring, exact endpoints and skipped frames.
     * Visual: Full white card reaches its icon rectangle on the grid's completion frame.
     */
    public static float synchronizedReturnRemaining(int maxRing, float elapsed) {
        int end = sceneDuration(maxRing);
        if (elapsed <= 0f) return 1f;
        if (elapsed >= end) return 0f;
        float span = Math.min(end, delay(maxRing) + duration(maxRing));
        float tail = remaining(maxRing, span);
        return clamp((remaining(maxRing, elapsed * span / end) - tail) / (1f - tail));
    }

    /** Purpose: Compute the card duration without speeding up the delayed fly-in.
     * Invocation: Controller prepares a selected-card return using the scene's outer ring.
     * Contract: Invert synchronizedReturnRemaining's eighth-power curve at 20% shrink.
     * If that point is fraction q of card time and G is the unchanged grid duration,
     * T = G / (1-q) and grid delay = T-G, so both finish at T. Round T upward to an
     * Android millisecond: the gate is at or just after 20%, less than 1 ms late.
     * Calculate once per return; no frame polling, independent timer or changed grid curve.
     * Verification: Host tests check threshold, unchanged grid speed and shared endpoints.
     * Visual: White card shrinks first; the normal-speed fly-in then finishes with it.
     */
    public static int returnDuration(int maxRing) {
        int gridDuration = sceneDuration(maxRing);
        return returnDuration(maxRing, gridDuration, RETURN_GRID_START_SHRINK, 8f);
    }

    /** Purpose: Solve the shared return endpoint from one validated live snapshot.
     * Invocation: SnapshotGridView.returnDuration once per return.
     * Contract: Grid duration is explicit; only card timing compensates for the chosen gate.
     * Gate 0 starts both immediately. Power changes card easing only, never the grid.
     * Verification: Host tests compare defaults and all configurable boundaries.
     * Visual: Card reaches the selected icon when the delayed fly-in restores the originals.
     */
    public static int returnDuration(int maxRing, int gridDuration, float shrinkGate, float power) {
        if (shrinkGate == 0f) return gridDuration;
        return (int) Math.ceil(gridDuration / (1d - gateFraction(maxRing, shrinkGate, power)));
    }

    // Fraction of card time at which the power curve has shrunk by shrinkGate.
    private static double gateFraction(int maxRing, float shrinkGate, float power) {
        double span = Math.min(sceneDuration(maxRing), delay(maxRing) + duration(maxRing));
        double tail = cardCurve(maxRing, (float) span, power);
        double target = tail + (1d - shrinkGate) * (1d - tail);
        return (delay(maxRing) + duration(maxRing) * (1d - Math.pow(target, 1d / power))) / span;
    }

    /** Purpose: Evaluate only the card with a captured live easing exponent.
     * Invocation: Same-frame controller update via SnapshotGridView.
     * Contract: Original-time input, exact fullscreen/icon endpoints; power 8 reuses original
     * arithmetic to preserve defaults. Grid curve and geometry are untouched.
     * Verification: Host endpoint, gate inversion and default parity tests; phone unverified.
     * Visual: Controls how the white card shrinks, with no independent end callback.
     */
    public static float synchronizedReturnRemaining(int maxRing, float elapsed, float power) {
        if (power == 8f) return synchronizedReturnRemaining(maxRing, elapsed);
        int end = sceneDuration(maxRing);
        if (elapsed <= 0f) return 1f;
        if (elapsed >= end) return 0f;
        float span = Math.min(end, delay(maxRing) + duration(maxRing));
        float tail = cardCurve(maxRing, span, power);
        return clamp((cardCurve(maxRing, elapsed * span / end, power) - tail) / (1f - tail));
    }

    // Purpose: Reuse original remaining at default power; evaluate a validated custom card power.
    // Invocation: Card sampling/inversion only. Contract: Never used for grid easing; host-tested.
    private static float cardCurve(int ring, float elapsed, float power) {
        return power == 8f ? remaining(ring, elapsed)
                : (float) Math.pow(1f - clamp((elapsed - delay(ring)) / duration(ring)), power);
    }

    /** Purpose: Evaluate a CSS-style cubic-bezier(x1,y1,x2,y2) easing at time fraction x.
     * Invocation: Eased card sampling and gate inversion. Contract: control points in [0,1]
     * make x(s) and y(s) monotone, so bisection on s is exact to float precision.
     * Verification: Host endpoint/monotonicity tests. Visual: none (pure arithmetic).
     */
    public static float bezierProgress(float x1, float y1, float x2, float y2, float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return (float) bezier(y1, y2, solveBezier(x1, x2, x));
    }

    // Inverse of bezierProgress: the time fraction at which the curve reaches progress y.
    public static double bezierTime(float x1, float y1, float x2, float y2, float y) {
        if (y <= 0f) return 0d;
        if (y >= 1f) return 1d;
        return bezier(x1, x2, solveBezier(y1, y2, y));
    }

    private static double bezier(double a, double b, double s) {
        double r = 1d - s;
        return 3d * a * s * r * r + 3d * b * s * s * r + s * s * s;
    }

    private static double solveBezier(double a, double b, double value) {
        double low = 0d, high = 1d;
        for (int i = 0; i < 48; i++) {
            double mid = (low + high) * .5d;
            if (bezier(a, b, mid) < value) low = mid; else high = mid;
        }
        return (low + high) * .5d;
    }

    /** Purpose: Ease the card's clock while keeping its live power curve and 20% gate.
     * Invocation: SnapshotGridView.cardRemaining when the live document supplies a curve.
     * Contract: The bezier warps time only (as ValueAnimator applies an interpolator to the
     * fraction before evaluating); the power-curve values are unchanged, so 0,0,1,1 is the
     * plain power curve. 1 at elapsed 0, exactly 0 at the scene endpoint, monotone.
     * Verification: Host endpoint, monotonicity, gate and identity-warp tests; phone unverified.
     * Visual: Same shrink shape and end, with a gentle start instead of an instant snap.
     */
    public static float easedReturnRemaining(int maxRing, float elapsed, float power,
            float x1, float y1, float x2, float y2) {
        int end = sceneDuration(maxRing);
        if (elapsed <= 0f) return 1f;
        if (elapsed >= end) return 0f;
        float warped = bezierProgress(x1, y1, x2, y2, elapsed / end) * end;
        return synchronizedReturnRemaining(maxRing, warped, power);
    }

    /** Purpose: Shared endpoint for the warped card, keeping the fly-in gate and speed.
     * Invocation: SnapshotGridView.returnDuration when eased. Contract: the power curve reaches
     * the gate at fraction p; the warp reaches p at q = bezierTime(p); T = G / (1-q).
     * Verification: Host tests check the card is at the gate when the fly-in starts.
     * Visual: Card reaches the gate, then the normal-speed fly-in finishes with it.
     */
    public static int easedReturnDuration(int maxRing, int gridDuration, float shrinkGate,
            float power, float x1, float y1, float x2, float y2) {
        if (shrinkGate == 0f) return gridDuration;
        double q = bezierTime(x1, y1, x2, y2, (float) gateFraction(maxRing, shrinkGate, power));
        return (int) Math.ceil(gridDuration / (1d - q));
    }

    /** Purpose: Pause the fly-in, then play its original timeline at normal speed.
     * Invocation: Controller supplies shared elapsed time, computed delay and grid duration.
     * Contract: Subtract only the delay; never multiply grid time by a speed-up factor.
     * Clamp before/after the active interval so skipped frames reach the same endpoint.
     * Verification: Host tests compare each grid/dock sample to its undelayed counterpart.
     * Visual: Surrounding icons wait for the card, then retain their original motion.
     */
    public static float delayedGridElapsed(float elapsed, float start, int gridDuration) {
        return Math.max(0f, Math.min(gridDuration, elapsed - start));
    }

    /** Purpose: Begin a floating dock/indicator group entirely below the overlay's bottom edge.
     * Invocation: Snapshot capture, once per scene. Contract: Include the target's gaps/insets;
     * original iLauncher's sum-of-heights is the bottom-aligned special case.
     * Verification: Host geometry examples include a floating dock with a bottom margin.
     */
    public static float stripTravel(float viewportBottom, float groupTop) {
        return Math.max(0f, viewportBottom - groupTop);
    }

    /** Purpose: Nova's ActivityOptions extent comes from scaled measured icon-view dimensions,
     * not the longer evolving crop. Invocation: At the strict-alpha launch handoff.
     * Contract: Caller supplies one shared coordinate space; sourceDimension is the initial view size.
     * Verification: Raw v00/d + o9/m; portrait/landscape/non-square host arithmetic checks.
     */
    public static int launchExtent(float cropWidth, float cropHeight, float base,
            float sourceDimension) {
        float outer = Math.max(1f, Math.min(cropWidth / base, cropHeight / base));
        return Math.max(1, Math.round(sourceDimension * outer));
    }

    public static float alpha(float elapsed, boolean alternate) {
        return 1f - clamp((elapsed - 25f) / (alternate ? 50f : 40f));
    }

    public static boolean alternate(float iconTop, float iconCenterY,
            float destinationCenterY, float cellHeight) {
        return !(iconTop <= destinationCenterY
                && Math.abs(destinationCenterY - iconCenterY) >= cellHeight);
    }
}
