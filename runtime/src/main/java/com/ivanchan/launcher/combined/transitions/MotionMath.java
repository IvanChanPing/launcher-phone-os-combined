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
        float inverse = 1f - clamp((elapsed - STRIP_START_MS)
                / Math.max(1f, sceneDuration - STRIP_START_MS));
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
