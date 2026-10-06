"""Host-only checks for live return timing; no Android compilation or phone access."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "runtime/src/main/java/com/ivanchan/launcher/combined/transitions"


class LiveTimingTest(unittest.TestCase):
    def test_config_and_live_math(self):
        """Execute shipped parser/math across defaults, boundaries and invalid documents."""
        source = "\n".join((JAVA / name).read_text().partition(";")[2]
                           for name in ("MotionMath.java", "TimingSettings.java"))
        document = (ROOT / "live-timing/timing.properties").read_text()
        checks = """
boolean passed = true;
String config = CONFIG_DOCUMENT;
TimingSettings defaults = TimingSettings.parse(config);
passed &= defaults.shrinkGate == .2f && defaults.cardPower == 8f && defaults.flyInDurationScale == 1f && defaults.dockStartMs == 300;
String[] invalid = { "", config + "\\nunknown=1", config.replace("0.20", "NaN"), config.replace("0.20", "Infinity"), config.replace("0.20", "-.01"), config.replace("0.20", "1.0"), config.replace("returnCardPower=8", "returnCardPower=0"), config.replace("returnCardPower=8", "returnCardPower=101"), config.replace("flyInDurationScale=1.0", "flyInDurationScale=0"), config.replace("flyInDurationScale=1.0", "flyInDurationScale=100.1"), config.replace("dockStartMs=300", "dockStartMs=501"), config.replace("dockStartMs=300", "dockStartMs=2.5"), config.replace("revision=1", "revision=-1"), config.replace("revision=1", "revision=1000001"), config.replace("returnCardPower=8", "") };
for (String bad : invalid) {
    boolean rejected = false;
    try { TimingSettings.parse(bad); } catch (IllegalArgumentException | java.io.IOException error) { rejected = true; }
    passed &= rejected;
}
passed &= TimingSettings.parse(config.replace("revision=1", "revision=0")).revision == 0;
for (int ring = 0; ring <= 6; ring++) {
    int reference = MotionMath.sceneDuration(ring);
    passed &= MotionMath.returnDuration(ring) == MotionMath.returnDuration(ring, reference, defaults.shrinkGate, defaults.cardPower);
    for (int t = 0; t <= reference; t++) {
        passed &= MotionMath.synchronizedReturnRemaining(ring, t) == MotionMath.synchronizedReturnRemaining(ring, t, 8f);
        passed &= MotionMath.stripRemaining(t, reference) == MotionMath.stripRemaining(t, reference, 300);
    }
    for (float gate : new float[]{0f, .2f, .8f}) for (float power : new float[]{1f, 4f, 8f, 12f}) for (float scale : new float[]{.5f, 1f, 2f}) {
        int gridDuration = Math.round(reference * scale);
        int total = MotionMath.returnDuration(ring, gridDuration, gate, power);
        float delay = total - gridDuration;
        passed &= total >= gridDuration;
        if (gate == 0f) passed &= delay == 0f;
        else passed &= MotionMath.synchronizedReturnRemaining(ring, delay / total * reference, power) <= 1f - gate + .00001f;
        float previous = 1f;
        for (int frame = 0; frame <= 120; frame++) {
            float t = total * (frame / 120f);
            float card = MotionMath.synchronizedReturnRemaining(ring, frame / 120f * reference, power);
            float grid = MotionMath.delayedGridElapsed(t, delay, gridDuration);
            passed &= Float.isFinite(card) && card >= 0f && card <= previous;
            passed &= Math.abs(grid - Math.max(0f, Math.min(gridDuration, t - delay))) < .001f;
            previous = card;
        }
        passed &= MotionMath.delayedGridElapsed(total, delay, gridDuration) == gridDuration;
        passed &= MotionMath.synchronizedReturnRemaining(ring, reference, power) == 0f;
    }
    for (int dock : new int[]{0, 300, 500}) {
        passed &= MotionMath.stripRemaining(dock, reference, dock) == 1f;
        passed &= MotionMath.stripRemaining(reference, reference, dock) == 0f;
    }
}
String curve = config + "\\nreturnEaseX1=0.2\\nreturnEaseY1=0\\nreturnEaseX2=0\\nreturnEaseY2=1\\n";
TimingSettings eased = TimingSettings.parse(curve);
passed &= eased.eased && eased.easeX1 == .2f && eased.easeY2 == 1f && !defaults.eased;
for (String bad : new String[]{ config + "\\nreturnEaseX1=0.2\\n", curve.replace("returnEaseX1=0.2", "returnEaseX1=1.1"), curve.replace("returnEaseY1=0", "returnEaseY1=-0.1"), curve.replace("returnEaseY2=1", "returnEaseY2=1.2") }) {
    boolean rejected = false;
    try { TimingSettings.parse(bad); } catch (IllegalArgumentException | java.io.IOException error) { rejected = true; }
    passed &= rejected;
}
float[][] curves = { {.2f, 0f, 0f, 1f}, {.42f, 0f, .58f, 1f}, {0f, 0f, 1f, 1f}, {.25f, .1f, .25f, 1f} };
for (int ring = 0; ring <= 6; ring++) for (float[] c : curves) for (float gate : new float[]{0f, .2f, .8f}) {
    int reference = MotionMath.sceneDuration(ring);
    int total = MotionMath.easedReturnDuration(ring, reference, gate, 20f, c[0], c[1], c[2], c[3]);
    float delay = total - reference;
    passed &= total >= reference;
    if (gate == 0f) passed &= delay == 0f;
    else { float at = MotionMath.easedReturnRemaining(ring, delay / total * reference, 20f, c[0], c[1], c[2], c[3]); passed &= at <= 1f - gate + .00001f && at > 1f - gate - .05f; }
    float previous = 1f;
    for (int frame = 0; frame <= 240; frame++) {
        float card = MotionMath.easedReturnRemaining(ring, frame / 240f * reference, 20f, c[0], c[1], c[2], c[3]);
        passed &= Float.isFinite(card) && card >= 0f && card <= previous + 1e-6f;
        previous = card;
    }
    passed &= MotionMath.easedReturnRemaining(ring, 0f, 20f, c[0], c[1], c[2], c[3]) == 1f;
    passed &= MotionMath.easedReturnRemaining(ring, reference, 20f, c[0], c[1], c[2], c[3]) == 0f;
}
for (int ring = 0; ring <= 6; ring++) { int reference = MotionMath.sceneDuration(ring); passed &= Math.abs(MotionMath.easedReturnDuration(ring, reference, .2f, 20f, 0f, 0f, 1f, 1f) - MotionMath.returnDuration(ring, reference, .2f, 20f)) <= 1; for (int t = 0; t <= reference; t += 7) passed &= Math.abs(MotionMath.easedReturnRemaining(ring, t, 20f, 0f, 0f, 1f, 1f) - MotionMath.synchronizedReturnRemaining(ring, t, 20f)) < .002f; }
System.out.println("LIVE_TIMING_" + (passed ? "PASS" : "FAIL"));
/exit
""".replace("CONFIG_DOCUMENT", json.dumps(document))
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                ["jshell", "--feedback", "concise",
                 "-J-Djava.util.prefs.userRoot=" + directory,
                 "-R-Djava.util.prefs.userRoot=" + directory],
                input=source + "\n" + checks, text=True, capture_output=True,
                timeout=60, check=True)
        output = result.stdout + result.stderr
        self.assertNotIn("Error:", output)
        self.assertNotIn("Exception", output)
        self.assertIn("LIVE_TIMING_PASS", output)
        self.assertNotIn("LIVE_TIMING_FAIL", output)

    def test_foreground_snapshot_and_transport_contract(self):
        """Confirm live values cannot change an existing scene and I/O stays off draw callbacks."""
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        grid = (JAVA / "SnapshotGridView.java").read_text()
        config = (JAVA / "LiveTimingConfig.java").read_text()
        self.assertIn("unlock ? TimingSettings.DEFAULT : LiveTimingConfig.current()", controller)
        self.assertIn("this(scene, selected, TimingSettings.DEFAULT)", grid)
        self.assertIn("private final TimingSettings timing", grid)
        self.assertNotIn("LiveTimingConfig", grid)
        self.assertIn("LiveTimingConfig.setVisible(activity, true)", controller)
        self.assertEqual(3, controller.count("LiveTimingConfig.setVisible(activity, false)"))
        for marker in ("volatile TimingSettings", "scheduleWithFixedDelay", "poll.cancel(false)",
                       "worker.purge()", "worker.execute(LiveTimingConfig::loadCache)",
                       "if (!active) return", "MAX_BYTES = 4096", "setInstanceFollowRedirects(false)",
                       "setUseCaches(false)", "NET_CAPABILITY_VALIDATED", ".commit()",
                       "TransitionDiagnostics.event(code, revision)"):
            self.assertIn(marker, config)
        for forbidden in ("setSSLSocketFactory", "setHostnameVerifier", "Log.", "logcat"):
            self.assertNotIn(forbidden, config)


if __name__ == "__main__":
    unittest.main()
