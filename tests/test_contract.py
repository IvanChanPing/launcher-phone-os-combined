"""Source-only contracts. No Android compiler, APK assembly, signing, or install."""
import hashlib
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest import mock
import xml.etree.ElementTree as ET
import zipfile

from patcher.prepare_clone import (
    CLONE_PACKAGE, ORIGINAL_PACKAGE, PACKAGE_TEXT_FILES, RUNTIME_METHODS, SmaliEditor,
    install_resources, install_runtime_smali, patch_apktool_identity,
    patch_smali_hooks, rename_manifest, verify_base_apk,
)
from tools.build_clone import validate_inputs

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "runtime/src/main/java/com/ivanchan/launcher/combined/transitions"
NS = "{http://schemas.android.com/apk/res/android}"
OWNERS = ("CombinedTransitionController", "IconOverlayView", "UnlockSignalTracker",
          "MotionMath", "LauncherAccess", "SnapshotGridView", "TransitionDiagnostics",
          "NovaGestureContract", "NovaGestureSurface")

def fixture(root):
    directory = root / "smali/com/android/launcher3"
    directory.mkdir(parents=True)
    (directory / "BaseDraggingActivity.smali").write_text("""
.class public Lcom/android/launcher3/BaseDraggingActivity;
.method public j0(Landroid/view/View;Landroid/content/Intent;Lcom/android/launcher3/e0;)Z
    .locals 7
    if-eqz v0, :cond_0
    return v0
    :cond_0
    invoke-virtual {p0, p1}, Lcom/android/launcher3/BaseDraggingActivity;->Y(Landroid/view/View;)Landroid/os/Bundle;
    return v0
.end method
""")
    methods = (
        "protected onCreate(Landroid/os/Bundle;)V", "protected onStart()V",
        "protected onResume()V", "protected onPause()V", "protected onStop()V",
        "protected onNewIntent(Landroid/content/Intent;)V",
        "public onWindowFocusChanged(Z)V",
        "public onConfigurationChanged(Landroid/content/res/Configuration;)V",
        "public F()V", "public onDestroy()V",
    )
    text = """.class public Lcom/android/launcher3/Launcher;
.method public X(Landroid/view/View;)Landroid/app/ActivityOptions;
    .locals 1
    iget-object v0, p0, Lcom/android/launcher3/Launcher;->o:Ljava/lang/Object;
    return-object v0
.end method
"""
    for method in methods:
        text += f""".method {method}
    .locals 20
    const/4 v0, 0x0
    const/4 p1, 0x0
    if-eqz v0, :alternate
    invoke-static {{}}, LFixture;->body()V
    return-void
    :alternate
    invoke-static {{}}, LFixture;->other()V
    return-void
.end method
"""
    (directory / "Launcher.smali").write_text(text)
    (directory / "LauncherApplication.smali").write_text("""
.class public Lcom/android/launcher3/LauncherApplication;
.method public onCreate()V
    .locals 0
    invoke-super {p0}, Landroid/app/Application;->onCreate()V
    return-void
.end method
""")
    return directory

class ContractTest(unittest.TestCase):
    def test_reference_grid_timing(self):
        source = (JAVA / "MotionMath.java").read_text()
        tables = {}
        for name, expected in (("DELAYS", [0,30,45,45,45,45,45]),
                               ("DURATIONS", [350,750,1070,1310,1470,1550,1550])):
            match = re.search(name + r" = \{([^}]+)\}", source)
            self.assertIsNotNone(match)
            tables[name] = [int(value) for value in match[1].split(",")]
            self.assertEqual(expected, tables[name])
        self.assertEqual([615,855,1015], [
            tables["DELAYS"][n] + tables["DURATIONS"][n] - 500 for n in (2,3,4)])

    def test_sdk_imports(self):
        sdk = os.environ.get("ANDROID_JAR")
        if not sdk:
            self.skipTest("Set ANDROID_JAR for real SDK import checks")
        with zipfile.ZipFile(sdk) as archive:
            classes = set(archive.namelist())
        for path in JAVA.glob("*.java"):
            for name in re.findall(r"^import (android\.[\w.]+);", path.read_text(), re.M):
                self.assertIn(name.replace(".", "/") + ".class", classes)

    def test_snapshot_and_layer_ownership(self):
        grid = (JAVA / "SnapshotGridView.java").read_text()
        overlay = (JAVA / "IconOverlayView.java").read_text()
        access = (JAVA / "LauncherAccess.java").read_text()
        self.assertIn("source.draw(new Canvas(bitmap))", grid)
        self.assertIn("scene.root.getOverlay().add(this)", grid)
        self.assertIn("shot.source.setAlpha(shot.alpha)", grid)
        self.assertIn("item.view != selected", grid)
        self.assertIn("scene.sourceInStrip", grid)
        self.assertIn('getField("a")', access)
        self.assertIn('getField("b")', access)
        self.assertIn("layers.getBackground()", overlay)
        self.assertIn("layers.getForeground()", overlay)
        self.assertIn("if (!adaptive)", overlay)
        self.assertIn("cropWidth * fit", overlay)
        self.assertIn("cropHeight * fit", overlay)

    def test_lifecycle_gate_and_one_shot(self):
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        tracker = (JAVA / "UnlockSignalTracker.java").read_text()
        for item in ("WeakHashMap<Activity", "WeakReference<Activity>",
                     "LauncherAccess.binding", "a.hasWindowFocus()", "keyguard.isKeyguardLocked()",
                     "source.setAlpha(sourceAlpha)", "generation++", "if (opening) return true;",
                     "Build.VERSION.SDK_INT >= 33", "options.setSplashScreenStyle(1)",
                     "PackageManager.PERMISSION_GRANTED"):
            self.assertIn(item, controller)
        self.assertLess(controller.index("issued = true;"), controller.index("LauncherAccess.replay(a,"))
        self.assertIn("CombinedTransitionController.onUnlocked()", tracker)
        self.assertIn("Intent.ACTION_SCREEN_OFF", tracker)
        self.assertIn("SystemClock.uptimeMillis() - receivedAt <= 5000", tracker)

    def test_no_live_scale_or_physics(self):
        for path in JAVA.glob("*.java"):
            for banned in ("setScaleX(", "setScaleY(", "setTranslationX(", "setTranslationY(",
                           "DynamicAnimation", "animateToFinalPosition", "animate()."):
                self.assertNotIn(banned, path.read_text(), str(path))

    def test_post_body_hooks_and_range_registers(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = fixture(Path(tmp))
            patch_smali_hooks(Path(tmp))
            base = (directory / "BaseDraggingActivity.smali").read_text()
            self.assertLess(base.index("\n    :cond_0"), base.index("->interceptLaunch"))
            self.assertLess(base.index("->interceptLaunch"), base.index("->Y("))
            self.assertIn("invoke-static/range {p0 .. p3}", base)
            editor = SmaliEditor(directory / "Launcher.smali")
            for signature, callback in (
                (".method protected onCreate(Landroid/os/Bundle;)V", "onLauncherCreated"),
                (".method protected onResume()V", "onLauncherResumed"),
                (".method public F()V", "onLauncherModelReady"),
                (".method public onWindowFocusChanged(Z)V", "onLauncherFocused"),
            ):
                owner = editor.method(signature)
                lines = editor.lines[owner.start:owner.end]
                returns = [i for i, line in enumerate(lines) if line.strip() == "return-void"]
                self.assertEqual(2, len(returns))
                for i in returns:
                    self.assertIn("->" + callback, lines[i - 1])
                    self.assertIn("invoke-static/range", lines[i - 1])
            owner = editor.method(".method protected onPause()V")
            body = "".join(editor.lines[owner.start:owner.end])
            self.assertLess(body.index("->onLauncherPaused"), body.index("->body"))
            owner = editor.method(".method protected onNewIntent(Landroid/content/Intent;)V")
            body = "".join(editor.lines[owner.start:owner.end])
            self.assertLess(body.index("->recordHomeIntent"), body.index("const/4 p1"))
            self.assertIn("->onLauncherNewIntent(Landroid/app/Activity;)V", body)
            owner = editor.method(".method public onWindowFocusChanged(Z)V")
            body = "".join(editor.lines[owner.start:owner.end])
            self.assertIn("->onLauncherFocused(Landroid/app/Activity;)V", body)

    def test_hook_drift_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = fixture(Path(tmp))
            path = directory / "BaseDraggingActivity.smali"
            path.write_text(path.read_text().replace("\n    :cond_0", "\n    :different"))
            with self.assertRaises(ValueError):
                patch_smali_hooks(Path(tmp))

    def test_manifest_identity_not_class_or_product(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "AndroidManifest.xml"
            path.write_text(f"""<manifest xmlns:android="http://schemas.android.com/apk/res/android"
package="{ORIGINAL_PACKAGE}"><permission android:name="{ORIGINAL_PACKAGE}.SELF"/>
<application android:name=".App"><activity android:name="{ORIGINAL_PACKAGE}.Main"/>
<provider android:name="Provider" android:authorities="{ORIGINAL_PACKAGE}.data"/>
<meta-data android:name="sku" android:value="{ORIGINAL_PACKAGE}.premium"/></application></manifest>""")
            rename_manifest(path)
            root = ET.parse(path).getroot()
            self.assertEqual(CLONE_PACKAGE, root.get("package"))
            self.assertEqual(ORIGINAL_PACKAGE + ".App", root.find("application").get(NS + "name"))
            self.assertEqual(ORIGINAL_PACKAGE + ".Main", root.find(".//activity").get(NS + "name"))
            self.assertEqual(CLONE_PACKAGE + ".data", root.find(".//provider").get(NS + "authorities"))
            self.assertEqual(ORIGINAL_PACKAGE + ".premium", root.find(".//meta-data").get(NS + "value"))
            self.assertNotIn("res/values/strings.xml", PACKAGE_TEXT_FILES)

    def test_nested_apktool_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "apktool.yml"
            path.write_text("version: 2.10.0\napkFileName: base.apk\npackageInfo:\n"
                            "  forcedPackageId: 127\n  renameManifestPackage: null\nversionInfo:\n  versionCode: 41\n")
            patch_apktool_identity(path)
            result = path.read_text()
            self.assertIn("  renameManifestPackage: " + CLONE_PACKAGE, result)
            self.assertNotIn("\nrenameManifestPackage:", result)
            self.assertIn("  versionCode: 41", result)

    def test_runtime_descriptors_and_duplicate_rejection(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            package = root / "runtime/com/ivanchan/launcher/combined/transitions"
            package.mkdir(parents=True)
            for name in OWNERS:
                (package / (name + ".smali")).write_text(
                    ".class public Lcom/ivanchan/launcher/combined/transitions/" + name + ";\n")
            controller = package / "CombinedTransitionController.smali"
            controller.write_text(controller.read_text() + "".join(
                ".method public static " + signature + "\n.end method\n"
                for signature in RUNTIME_METHODS))
            output = root / "decoded"
            (output / "smali_classes2").mkdir(parents=True)
            destination = install_runtime_smali(root / "runtime", output)
            self.assertEqual("smali_classes3", destination.name)
            with self.assertRaises(ValueError):
                install_runtime_smali(root / "runtime", output)
            original = controller.read_text()
            controller.write_text(original.replace("recordHomeIntent(", "wrongIntentHook("))
            with self.assertRaises(ValueError):
                install_runtime_smali(root / "runtime", root / "missing-method")
            controller.write_text(original)
            (package / "MotionMath.smali").write_text(".class public LWrong;\n")
            with self.assertRaises(ValueError):
                install_runtime_smali(root / "runtime", root / "another")

    def test_base_hash_and_xapk_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "base.apk"
            path.write_bytes(b"fixture")
            with mock.patch("patcher.prepare_clone.EXPECTED_BASE_SHA256",
                            hashlib.sha256(b"fixture").hexdigest()):
                verify_base_apk(path)
                path.write_bytes(b"changed")
                with self.assertRaises(ValueError):
                    verify_base_apk(path)
            with self.assertRaises(ValueError):
                validate_inputs(path)

    def test_window_selectors_and_exact_exit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            values = root / "res/values"; values.mkdir(parents=True)
            (values / "styles.xml").write_text('<resources><style name="LauncherTheme"/></resources>')
            install_resources(ROOT, root)
            styles = ET.parse(values / "styles.xml").getroot()
            combined = styles.find("style[@name='CombinedLauncherWindowAnimation']")
            self.assertEqual("@android:style/Animation.Activity", combined.get("parent"))
            self.assertEqual(6, len(combined.findall("item")))
            exit_anim = ET.parse(root / "res/anim/combined_foreground_return_exit.xml").getroot()
            self.assertEqual("top", exit_anim.get(NS + "zAdjustment"))
            self.assertEqual("109.99756%", exit_anim.find("translate").get(NS + "toYDelta"))

    def test_diagnostics_are_bounded_and_private(self):
        source = (JAVA / "TransitionDiagnostics.java").read_text()
        for item in ("queue.size() == 64", "queue.removeFirst()", "NET_CAPABILITY_VALIDATED",
                     "setConnectTimeout(3000)", "setReadTimeout(3000)", "setRequestMethod(\"POST\")"):
            self.assertIn(item, source)
        for item in ("printStackTrace", "intent.toString", "getInstalledPackages", "logcat"):
            self.assertNotIn(item + "(", source)

    def test_nova_gesture_wire_contract(self):
        protocol = (JAVA / "NovaGestureContract.java").read_text()
        for key in ("gesture_nav_contract_v1", "gesture_nav_contract_icon_position",
                    "gesture_nav_contract_surface_control", "gesture_nav_contract_finish_callback",
                    "android.intent.extra.REMOTE_CALLBACK"):
            self.assertIn('"' + key + '"', protocol)
        self.assertIn("Intent.EXTRA_COMPONENT_NAME", protocol)
        self.assertIn("Intent.EXTRA_USER", protocol)
        self.assertIn("Build.VERSION.SDK_INT < 30", protocol)
        self.assertIn("intent.removeExtra(EXTRA)", protocol)
        self.assertIn("reply.copyFrom(callback)", protocol)
        self.assertIn("reply.replyTo.send(reply)", protocol)
        self.assertIn("finish.what = 0", protocol)
        self.assertIn("message.arg1 == surface.sessionId", protocol)
        self.assertNotIn("reply.obj =", protocol)

    def test_nova_surface_is_system_owned_not_reverse_animation(self):
        surface = (JAVA / "NovaGestureSurface.java").read_text()
        for required in ("surface.setZOrderOnTop(true)", "PixelFormat.TRANSLUCENT",
                         "picture.beginRecording", "holder.lockHardwareCanvas()",
                         "surface.getSurfaceControl()", "contract.send(position, control, this)",
                         "SurfaceHolder.Callback2", "postDelayed(remove, twoFrames)"):
            self.assertIn(required, surface)
        for path in JAVA.glob("*.java"):
            code = path.read_text()
            self.assertNotIn("returnProgress(", code)
            self.assertNotIn("class ReturnTarget", code)
            # Purpose: Catch stale uses too; declaration-only checks missed the exception path.
            # Contract: The retired target field must be absent from every runtime owner.
            self.assertNotIn("returnTarget", code)
        for forbidden in ("ValueAnimator", "PathInterpolator", "setGeometry(", "setMatrix("):
            self.assertNotIn(forbidden, surface)

    def test_nova_surface_api_branches_and_native_layout(self):
        surface = (JAVA / "NovaGestureSurface.java").read_text()
        for marker in ("Build.VERSION.SDK_INT <= 30", "params.copyFrom(",
                       "params.token = null", "TYPE_APPLICATION_PANEL", "FLAG_NOT_FOCUSABLE",
                       "FLAG_NOT_TOUCHABLE", "FLAG_WATCH_OUTSIDE_TOUCH", "host.addView(view)",
                       "Build.VERSION.SDK_INT < 32", "Build.VERSION.PREVIEW_SDK_INT < 1",
                       "LEGACY_FOCUS_CLEANUP_MS = 400", "if ((!finishReceived && !surfaceFailed) || gridHeld"):
            self.assertIn(marker, surface)
        xml = ET.parse(ROOT / "integration/res/layout/combined_gesture_surface.xml").getroot()
        self.assertEqual("com.ivanchan.launcher.combined.transitions.NovaGestureSurface", xml.tag)
        self.assertEqual("true", xml.get("{http://schemas.android.com/apk/res-auto}layout_ignoreInsets"))

    def test_gesture_target_comes_from_home_contract(self):
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        access = (JAVA / "LauncherAccess.java").read_text()
        self.assertIn("pendingGesture = NovaGestureContract.consume(intent)", controller)
        self.assertIn("LauncherAccess.gestureTarget(scene, contract.component, contract.user)", controller)
        self.assertIn("pendingGesture == null ? eligible(a) : gestureEligible(a)", controller)
        self.assertIn("new SnapshotGridView(scene, landing)", controller)
        self.assertIn("animateGrid && scene.sourceInStrip", controller)
        self.assertIn("gestureSurface.releaseGridHold()", controller)
        self.assertIn("new int[] {0, 4}", access)
        self.assertIn('model.getField("n")', access)
        self.assertNotIn("LauncherAccess.remember", controller)

    def test_nova_handoff_not_elongated_crop(self):
        overlay = (JAVA / "IconOverlayView.java").read_text()
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        self.assertIn("Rect rect = icon.launchBounds()", controller)
        self.assertIn("MotionMath.launchExtent(current.width(), current.height()", overlay)
        self.assertIn("host.getWidth() * .5f - screenOrigin[0]", overlay)
        self.assertIn("host.getHeight() * .5f - screenOrigin[1]", overlay)
        self.assertNotIn("adaptive ? cropWidth", overlay)

    def test_pause_preserves_issued_open_and_stop_cleans(self):
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        pause = controller.split("public static void onLauncherPaused", 1)[1].split(
            "public static void onLauncherStopped", 1)[0]
        stop = controller.split("public static void onLauncherStopped", 1)[1].split(
            "public static void onLauncherConfigurationChanged", 1)[0]
        self.assertIn("if (!(value.opening && value.issued && value.animator != null))", pause)
        self.assertIn("value.clearVisuals()", stop)
        self.assertIn("clock.getAnimatedFraction() * duration", controller)

    def test_dock_starts_at_viewport_edge_without_retiming_grid(self):
        grid = (JAVA / "SnapshotGridView.java").read_text()
        self.assertIn("MotionMath.stripTravel(", grid)
        self.assertIn("LauncherAccess.bounds(strip, scene.root).top", grid)
        self.assertNotIn("stripTravel +=", grid)

    def test_compile_gate_and_no_untrusted_decoded_cli(self):
        script = (ROOT / "tools/build_clone.py").read_text()
        self.assertIn('mode.add_argument("--compile-authorized"', script)
        self.assertLess(script.index("if args.check:"), script.index('":runtime:assembleRelease"'))
        self.assertIn('"--lib", str(sdk / "platforms/android-36/android.jar")', script)
        self.assertNotIn('parser.add_argument("--decoded"', (ROOT / "patcher/prepare_clone.py").read_text())

if __name__ == "__main__":
    unittest.main()
