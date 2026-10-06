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
          "NovaGestureContract", "NovaGestureSurface", "MiniOsAnimator",
          "MiniOsGrowListener", "MiniOsLaunchListener", "MiniOsReturnListener",
          "TimingSettings", "LiveTimingConfig")

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
        self.assertIn("extends ImageView", overlay)
        self.assertIn("setBackgroundColor(Color.WHITE)", overlay)
        self.assertIn("host.getOverlay().add(this)", overlay)
        self.assertNotIn("void onDraw(", overlay)

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
            for banned in ("DynamicAnimation", "animateToFinalPosition"):
                self.assertNotIn(banned, path.read_text(), str(path))
        for name in ("SnapshotGridView", "LauncherAccess"):
            for banned in ("setScaleX(", "setScaleY(", "setTranslationX(", "setTranslationY("):
                self.assertNotIn(banned, (JAVA / (name + ".java")).read_text())

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

    def test_native_window_theme_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            values = root / "res/values"; values.mkdir(parents=True)
            (values / "styles.xml").write_text('<resources><style name="LauncherTheme"/></resources>')
            install_resources(ROOT, root)
            styles = ET.parse(values / "styles.xml").getroot()
            self.assertIsNone(styles.find("style[@name='CombinedLauncherWindowAnimation']"))
            self.assertEqual(0, len(styles.find("style[@name='LauncherTheme']")))

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

    def test_minio_return_uses_actual_opened_cell(self):
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        access = (JAVA / "LauncherAccess.java").read_text()
        self.assertNotIn("NovaGestureContract.consume(intent)", controller)
        self.assertIn("if (accepted) lastOpenedSource = new WeakReference<>(source)", controller)
        self.assertIn("unlock ? null : lastOpenedSource.get()", controller)
        self.assertIn("new SnapshotGridView(scene, landing,", controller)
        self.assertIn("icon.applyReturnRemaining(1f)", controller)

    def test_return_card_uses_one_grid_clock(self):
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        overlay = (JAVA / "IconOverlayView.java").read_text()
        motion = (JAVA / "MiniOsAnimator.java").read_text()
        self.assertIn("runClock(grid.duration, true, token)", controller)
        self.assertIn("if (icon != null) icon.applyReturnRemaining(1f)", controller)
        update = controller.split("clock.addUpdateListener(value -> {", 1)[1].split("});", 1)[0]
        self.assertIn("if (token != generation) return;", update)
        self.assertIn("grid.progress(elapsed)", update)
        self.assertIn("float remaining = grid.cardRemaining(clock.getAnimatedFraction() * duration)", update)
        self.assertIn("icon.applyReturnRemaining(remaining)", update)
        self.assertIn("? grid.returnDuration() : duration", controller)
        self.assertIn("final float gridStart = clockDuration - duration", controller)
        self.assertIn("clock.setDuration(clockDuration)", controller)
        self.assertIn("grid.progress(MotionMath.delayedGridElapsed(elapsed, gridStart, duration))", update)
        self.assertIn("else if (grid != null) grid.progress(elapsed)", update)
        self.assertNotIn("icon.returnHome(", controller)
        preparation = controller.split("private void prepareReturnVisuals(", 1)[1].split(
            "private void closeGestureSurface()", 1)[0]
        self.assertNotIn("returnHome(", preparation)
        self.assertIn("void applyReturnRemaining(float remaining)", overlay)
        self.assertNotIn("motion.returnHome(", overlay)
        self.assertIn(".setDuration(duration).setStartDelay(0).withStartAction(startGrid)", motion)
        self.assertIn("DEFAULT_DURATION_ANIMATION, null);", motion)
        self.assertIn(".setListener(new MiniOsReturnListener(this)).start();", motion)

    def test_card_fullscreen_bounds_for_edge_cells(self):
        overlay = (JAVA / "IconOverlayView.java").read_text()
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        self.assertIn("Rect rect = icon.launchBounds()", controller)
        self.assertIn("makeClipRevealAnimation", controller)
        for width, height in ((1080, 2400), (2400, 1080), (582, 1280)):
            for left, top, size in ((12, 60, 72), (width-84, height-120, 72),
                                    (width//2-36, height-150, 72)):
                values = {"host.getWidth()": width, "host.getHeight()": height,
                          "start.width()": size, "start.height()": size,
                          "start.exactCenterX()": left+size/2,
                          "start.exactCenterY()": top+size/2}
                computed = {}
                for name in ("fullScaleX", "fullScaleY", "fullX", "fullY"):
                    expression = re.search(r"float " + name + r"\(\) \{ return ([^;]+);", overlay)[1]
                    expression = expression.replace("(float) ", "").replace(".5f", ".5")
                    for token, value in values.items():
                        expression = expression.replace(token, repr(value))
                    computed[name] = eval(expression, {"__builtins__": {}}, {})
                cx = left+size/2+computed["fullX"]
                cy = top+size/2+computed["fullY"]
                self.assertAlmostEqual(0, cx-size*computed["fullScaleX"]/2)
                self.assertAlmostEqual(width, cx+size*computed["fullScaleX"]/2)
                self.assertAlmostEqual(0, cy-size*computed["fullScaleY"]/2)
                self.assertAlmostEqual(height, cy+size*computed["fullScaleY"]/2)

    def test_pause_preserves_issued_open_and_stop_cleans(self):
        controller = (JAVA / "CombinedTransitionController.java").read_text()
        pause = controller.split("public static void onLauncherPaused", 1)[1].split(
            "public static void onLauncherStopped", 1)[0]
        stop = controller.split("public static void onLauncherStopped", 1)[1].split(
            "public static void onLauncherConfigurationChanged", 1)[0]
        self.assertIn("if (!(value.opening && value.issued && value.icon != null))", pause)
        self.assertIn("value.clearVisuals()", stop)
        self.assertIn("clock.getAnimatedFraction() * duration", controller)
        self.assertIn("if (!inward && issued)", controller)

    def test_original_minio_callback_bodies_preserved(self):
        """Compare original tool output, allowing only class references and fullscreen endpoints."""
        original = ROOT / "integration/minios-original/jadx-callbacks"
        cases = (("UITool$1", "MiniOsGrowListener"),
                 ("UITool$1$1", "MiniOsLaunchListener"),
                 ("Home$3", "MiniOsReturnListener"))
        for before, after in cases:
            old = (original / (before + ".java")).read_text()
            new = (JAVA / (after + ".java")).read_text()
            def body(code):
                return code.split("public void onAnimationEnd(Animator animator) {", 1)[1].split("\n    }", 1)[0]
            expected = body(old).replace("UITool$1$1", "MiniOsLaunchListener").replace(
                "UITool$1", "MiniOsGrowListener").replace("Home.", "MiniOsAnimator.").replace(
                "Constant.DEFAULT_DURATION_ANIMATION", "MiniOsAnimator.DEFAULT_DURATION_ANIMATION")
            actual = body(new)
            if after == "MiniOsGrowListener":
                actual = actual.replace(".scaleX(this.fullScaleX).scaleY(this.fullScaleY)",
                                        ".scaleX(1.0f).scaleY(1.0f)").replace(
                    ".translationX(this.fullX).translationY(this.fullY)", "")
            self.assertEqual("".join(expected.split()), "".join(actual.split()), after)

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
