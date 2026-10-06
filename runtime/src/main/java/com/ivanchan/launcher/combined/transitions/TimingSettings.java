package com.ivanchan.launcher.combined.transitions;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;

/**
 * Purpose: Immutable, validated live controls for Home-return timing.
 * Invocation: LiveTimingConfig parses a complete properties document; each return captures one instance.
 * Contract: Numeric data only, five required keys, no executable content. Reject the whole update on
 * unknown/missing keys or nonfinite/out-of-range values. Defaults preserve the shipped motion.
 * Verification: Host Java parser/range and timing tests; Android playback remains unverified.
 * Visual: Adjust card shrink threshold/easing, fly-in duration multiplier and dock start, not geometry.
 */
public final class TimingSettings {
    public static final TimingSettings DEFAULT = new TimingSettings(0, .20f, 8f, 1f, 300,
            false, 0f, 0f, 1f, 1f);
    public final int revision;
    public final float shrinkGate;
    public final float cardPower;
    public final float flyInDurationScale;
    public final int dockStartMs;
    // Optional card clock easing: cubic-bezier(x1,y1,x2,y2) time-warps the returnCardPower curve.
    public final boolean eased;
    public final float easeX1, easeY1, easeX2, easeY2;

    private TimingSettings(int revision, float shrinkGate, float cardPower,
            float flyInDurationScale, int dockStartMs, boolean eased,
            float easeX1, float easeY1, float easeX2, float easeY2) {
        this.revision = revision; this.shrinkGate = shrinkGate; this.cardPower = cardPower;
        this.flyInDurationScale = flyInDurationScale; this.dockStartMs = dockStartMs;
        this.eased = eased; this.easeX1 = easeX1; this.easeY1 = easeY1;
        this.easeX2 = easeX2; this.easeY2 = easeY2;
    }

    /** Purpose: Validate one complete server/cache snapshot before publishing it.
     * Invocation: Background fetch/cache load, also host tests. Contract: At most 4096 characters;
     * all five keys required; unknown keys reject typos. Revision is an identifier, not an ordering
     * gate, so restoring an older document is supported. No shared mutable Properties escape.
     * Verification: Host tests exercise defaults, alternate values, bounds and malformed documents.
     */
    public static TimingSettings parse(String text) throws IOException {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("Config size");
        Properties values = new Properties();
        values.load(new StringReader(text));
        HashSet<String> keys = new HashSet<>(Arrays.asList(
                "revision", "returnShrinkGate", "returnCardPower", "flyInDurationScale", "dockStartMs"));
        HashSet<String> ease = new HashSet<>(Arrays.asList(
                "returnEaseX1", "returnEaseY1", "returnEaseX2", "returnEaseY2"));
        boolean eased = values.stringPropertyNames().containsAll(ease);
        if (eased) keys.addAll(ease);
        // All four curve keys or none; any other key set is a typo and rejects the document.
        if (!values.stringPropertyNames().equals(keys))
            throw new IllegalArgumentException("Config keys");
        int revision = Integer.parseInt(values.getProperty("revision").trim());
        int dock = Integer.parseInt(values.getProperty("dockStartMs").trim());
        float gate = number(values, "returnShrinkGate", 0f, .99f);
        float power = number(values, "returnCardPower", 1f, 100f);
        float scale = number(values, "flyInDurationScale", .5f, 100f);
        if (revision < 0 || revision > 1000000 || dock < 0 || dock > 500)
            throw new IllegalArgumentException("Config integer range");
        // x and y stay in [0,1] so the curve is monotone: the card never undershoots its icon.
        float x1 = eased ? number(values, "returnEaseX1", 0f, 1f) : 0f;
        float y1 = eased ? number(values, "returnEaseY1", 0f, 1f) : 0f;
        float x2 = eased ? number(values, "returnEaseX2", 0f, 1f) : 1f;
        float y2 = eased ? number(values, "returnEaseY2", 0f, 1f) : 1f;
        return new TimingSettings(revision, gate, power, scale, dock, eased, x1, y1, x2, y2);
    }

    // Purpose: Reject invalid floats rather than publishing partially corrected timing.
    // Invocation: parse only. Contract: inclusive bounds and finite values; host tests cover both.
    private static float number(Properties values, String key, float low, float high) {
        float value = Float.parseFloat(values.getProperty(key).trim());
        if (!Float.isFinite(value) || value < low || value > high)
            throw new IllegalArgumentException("Config number range");
        return value;
    }
}
