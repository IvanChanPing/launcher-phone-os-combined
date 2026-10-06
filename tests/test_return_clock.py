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
    for (int hz : new int[]{60, 90, 120}) {
        float step = 1000f / hz;
        int last = (int) Math.ceil(end / step);
        for (int frame = 0; frame <= last; frame++) {
            float t = Math.min(end, frame * step);
            passed &= (MotionMath.synchronizedReturnRemaining(ring, t) == 0f) == (t >= end);
        }
    }
}
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

