"""Purpose: host-only return timing regression checks; never builds an Android app."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "runtime/src/main/java/com/ivanchan/launcher/combined/transitions"


class ReturnClockTest(unittest.TestCase):
    def test_actual_java_endpoints_and_frames(self):
        """Execute the actual pure-Java math, including sparse grids and skipped frames."""
        source = (JAVA / "MotionMath.java").read_text()
        package, separator, body = source.partition(";")
        self.assertTrue(package.startswith("package "))
        self.assertEqual(";", separator)
        checks = """
boolean passed = true;
for (int ring = 0; ring <= 6; ring++) {
    int end = MotionMath.sceneDuration(ring);
    passed &= MotionMath.synchronizedReturnRemaining(ring, -1f) == 1f;
    passed &= MotionMath.synchronizedReturnRemaining(ring, 0f) == 1f;
    passed &= MotionMath.synchronizedReturnRemaining(ring, end) == 0f;
    passed &= MotionMath.synchronizedReturnRemaining(ring, end + 250f) == 0f;
    float previous = 1f;
    for (int t = 1; t < end; t++) {
        float value = MotionMath.synchronizedReturnRemaining(ring, t);
        passed &= Float.isFinite(value) && value > 0f && value <= previous;
        previous = value;
    }
    int total = MotionMath.returnDuration(ring);
    float gate = total - end;
    passed &= total > end;
    // Independent numerical inversion checks the closed-form runtime calculation.
    float low = 0f, high = end;
    for (int iteration = 0; iteration < 24; iteration++) {
        float mid = (low + high) * .5f;
        if (MotionMath.synchronizedReturnRemaining(ring, mid) > .8f) low = mid;
        else high = mid;
    }
    double exactTotal = end / (1d - high / end);
    passed &= total >= exactTotal - .001 && total < exactTotal + 1.001;
    passed &= MotionMath.synchronizedReturnRemaining(ring, gate / total * end) <= .800001f;
    passed &= MotionMath.synchronizedReturnRemaining(ring, (gate - 1f) / total * end) > .8f;
    for (int t = 0; t <= total; t++) {
        float grid = MotionMath.delayedGridElapsed(t, gate, end);
        float expected = Math.max(0f, t - gate);
        passed &= grid == expected;
        for (int itemRing = 0; itemRing <= ring; itemRing++)
            passed &= MotionMath.remaining(itemRing, grid) == MotionMath.remaining(itemRing, expected);
        passed &= MotionMath.stripRemaining(grid, end) == MotionMath.stripRemaining(expected, end);
    }
    for (int hz : new int[]{60, 90, 120}) {
        float step = 1000f / hz;
        int last = (int) Math.ceil(total / step);
        float previousGrid = 0f, previousTime = 0f;
        for (int frame = 0; frame <= last; frame++) {
            float t = Math.min(total, frame * step);
            float remaining = MotionMath.synchronizedReturnRemaining(ring, t / total * end);
            passed &= (remaining == 0f) == (t >= total);
            float grid = MotionMath.delayedGridElapsed(t, gate, end);
            passed &= Float.isFinite(grid) && grid >= previousGrid && grid <= end;
            if (remaining > .8f || t <= gate) passed &= grid == 0f;
            if (t > gate && t < total) passed &= grid > 0f && grid < end;
            if (previousTime >= gate)
                passed &= Math.abs((grid - previousGrid) - (t - previousTime)) < .001f;
            passed &= (grid == end) == (t >= total);
            previousGrid = grid; previousTime = t;
        }
    }
    // A jump past the gate or straight to the last frame must not restart a delay.
    passed &= MotionMath.delayedGridElapsed(gate + 100f, gate, end) == 100f;
    passed &= MotionMath.delayedGridElapsed(total, gate, end) == end;
    passed &= MotionMath.delayedGridElapsed(total + 250f, gate, end) == end;
    passed &= MotionMath.synchronizedReturnRemaining(ring, (total + 250f) / total * end) == 0f;
    System.out.println("RING " + ring + ": grid=" + end + " delay=" + gate + " total=" + total);
}
passed &= MotionMath.RETURN_GRID_START_SHRINK == .2f;
passed &= MotionMath.delayedGridElapsed(199f, 200f, 1000) == 0f;
passed &= MotionMath.delayedGridElapsed(200f, 200f, 1000) == 0f;
passed &= MotionMath.delayedGridElapsed(600f, 200f, 1000) == 400f;
passed &= MotionMath.delayedGridElapsed(1000f, 200f, 1000) == 800f;
passed &= MotionMath.delayedGridElapsed(1200f, 200f, 1000) == 1000f;
System.out.println("RETURN_CLOCK_" + (passed ? "PASS" : "FAIL"));
/exit
"""
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                ["jshell", "--feedback", "concise",
                 "-J-Djava.util.prefs.userRoot=" + directory,
                 "-R-Djava.util.prefs.userRoot=" + directory],
                input=body + "\n" + checks, text=True, capture_output=True,
                timeout=60, check=True)
        output = result.stdout + result.stderr
        self.assertNotIn("Error:", output)
        self.assertNotIn("Exception", output)
        self.assertIn("RETURN_CLOCK_PASS", output)
        self.assertNotIn("RETURN_CLOCK_FAIL", output)


if __name__ == "__main__":
    unittest.main()
