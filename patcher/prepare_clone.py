#!/usr/bin/env python3
"""Prepare a decoded Launcher Phone OS 1.4.1 tree for the combined transition runtime.

Purpose: Apply the exact package-identity, resource, and Smali hook changes without distributing
vendor code or silently accepting another APK version.
Invocation: python3 patcher/prepare_clone.py --base-apk <verified-base> --out <new-tree> ...
Contract: The exact verified base is decoded by pinned Apktool in this invocation; every owner/signature/anchor is validated before its
smallest complete method is changed. Any mismatch exits nonzero before the output is declared ready.
Verification: Unit tests exercise a representative decoded fixture. APK assembly is not performed.
Visual: Installs hooks for selected-icon growth, sibling icon fly-out, and unlock/return grid fly-in.
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ORIGINAL_PACKAGE = "com.iphonelauncher.ioslauncher.launcherios.ios19"
CLONE_PACKAGE = "com.ivanchan.launcher.combined"
EXPECTED_BASE_SHA256 = "40161f556b783375b0a528f6810efa1233454398bf7bd4f3940e6a51d19f2ddc"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)

# Purpose: Link every injected call to an actual public static compiled runtime method.
# Invocation: Runtime installation before any vendor APK assembly.
# Contract: Exact descriptors, including post-body callbacks that must not read reused p1.
# Verification: Descriptor fixtures reject missing/wrong owners or method signatures.
RUNTIME_METHODS = (
    "install(Landroid/app/Application;)V",
    "interceptLaunch(Landroid/app/Activity;Landroid/view/View;Landroid/content/Intent;Ljava/lang/Object;)Z",
    "consumeLaunchOptions(Landroid/app/Activity;Landroid/view/View;)Landroid/app/ActivityOptions;",
    "recordHomeIntent(Landroid/app/Activity;Landroid/content/Intent;)V",
    *[name + "(Landroid/app/Activity;)V" for name in (
        "onLauncherCreated", "onLauncherStarted", "onLauncherResumed", "onLauncherPaused",
        "onLauncherStopped", "onLauncherNewIntent", "onLauncherFocused",
        "onLauncherConfigurationChanged", "onLauncherModelReady", "onLauncherDestroyed")],
)

PACKAGE_TEXT_FILES = (
    "smali/com/android/launcher3/f2$c.smali",
    "smali/i3/r$d.smali",
    "smali/i3/h.smali",
    "smali/com/android/launcher3/widget/custom/d.smali",
    "smali/l3/a.smali",
    "smali/com/android/launcher3/util/s.smali",
    "smali/com/android/launcher3/LauncherApplication.smali",
    "smali/com/android/launcher3/m2.smali",
    "smali/com/android/launcher3/LauncherProvider$b.smali",
    "smali/com/android/launcher3/F1.smali",
    "smali/com/android/launcher3/Q2.smali",
    "smali/com/android/launcher3/o2.smali",
    "smali/com/android/launcher3/n2.smali",
    "smali_classes3/p9/a.smali",
    "smali_classes5/fb/q.smali",
    "res/xml/default_workspace_4.xml",
    "res/xml/default_workspace_5.xml",
    "res/xml/default_workspace_6.xml",
)


@dataclass(frozen=True)
class MethodRange:
    start: int
    end: int


class SmaliEditor:
    """Exact method-boundary editor; never deletes by line number or regular expression."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.lines = path.read_text(encoding="utf-8").splitlines(keepends=True)

    def method(self, signature: str) -> MethodRange:
        starts = [index for index, line in enumerate(self.lines) if line.strip() == signature]
        if len(starts) != 1:
            raise ValueError(f"{self.path}: expected one {signature!r}, found {len(starts)}")
        start = starts[0]
        for end in range(start + 1, len(self.lines)):
            if self.lines[end].strip() == ".end method":
                return MethodRange(start, end)
        raise ValueError(f"{self.path}: unterminated {signature!r}")

    def insert_after(self, owner: MethodRange, anchor: str, block: str) -> None:
        matches = [index for index in range(owner.start, owner.end + 1)
                   if self.lines[index].strip() == anchor]
        if len(matches) != 1:
            raise ValueError(f"{self.path}: expected one anchor {anchor!r}, found {len(matches)}")
        self.lines[matches[0] + 1:matches[0] + 1] = [line + "\n" for line in block.splitlines()]

    def replace_once(self, owner: MethodRange, old: str, new: str) -> None:
        matches = [index for index in range(owner.start, owner.end + 1)
                   if self.lines[index].strip() == old]
        if len(matches) != 1:
            raise ValueError(f"{self.path}: expected one line {old!r}, found {len(matches)}")
        indent = self.lines[matches[0]][:len(self.lines[matches[0]]) - len(self.lines[matches[0]].lstrip())]
        self.lines[matches[0]] = indent + new + "\n"

    def write(self) -> None:
        self.path.write_text("".join(self.lines), encoding="utf-8")


def rename_manifest(path: Path) -> None:
    """Purpose: Isolate install identity without renaming Java classes or billing products.
    Invocation: Exact-base and configuration-split preparation.
    Contract: Component class names remain original, relative classes become fully qualified.
    Verification: Manifest fixtures cover identity, authorities, permissions and relative classes.
    """
    tree = ET.parse(path)
    root = tree.getroot()
    if root.get("package") != ORIGINAL_PACKAGE:
        raise ValueError(f"unexpected manifest package: {root.get('package')}")
    root.set("package", CLONE_PACKAGE)
    component_tags = {"application", "activity", "activity-alias", "service", "receiver", "provider"}
    identity_attrs = {"authorities", "permission", "readPermission", "writePermission",
                      "taskAffinity", "process"}
    for element in root.iter():
        for key, value in list(element.attrib.items()):
            local = key.rsplit("}", 1)[-1]
            is_class = (element.tag in component_tags and local in
                        {"name", "targetActivity", "parentActivityName", "appComponentFactory", "backupAgent"})
            if is_class:
                if value.startswith("."):
                    element.set(key, ORIGINAL_PACKAGE + value)
                elif "." not in value:
                    element.set(key, ORIGINAL_PACKAGE + "." + value)
            elif (local in identity_attrs or
                  (local == "name" and element.tag in {"permission", "uses-permission", "permission-tree"})):
                element.set(key, value.replace(ORIGINAL_PACKAGE, CLONE_PACKAGE))
    tree.write(path, encoding="utf-8", xml_declaration=True)

def rename_explicit_internal_references(root: Path) -> None:
    for relative in PACKAGE_TEXT_FILES:
        path = root / relative
        if not path.is_file():
            raise FileNotFoundError(path)
        current = path.read_text(encoding="utf-8")
        if ORIGINAL_PACKAGE not in current:
            raise ValueError(f"{path}: expected original package reference")
        path.write_text(current.replace(ORIGINAL_PACKAGE, CLONE_PACKAGE), encoding="utf-8")


def patch_apktool_identity(path: Path) -> None:
    """Update Apktool 2.10's nested packageInfo, not an ignored top-level lookalike."""
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    if "version: 2.10.0\n" not in lines:
        raise ValueError("Only Apktool 2.10.0 metadata is supported")
    section = False
    found = 0
    for index, line in enumerate(lines):
        if line.startswith("apkFileName: "):
            lines[index] = f"apkFileName: {CLONE_PACKAGE}.apk\n"
        if line == "packageInfo:\n":
            section = True
        elif line and not line[0].isspace():
            section = False
        if section and line.startswith("  renameManifestPackage: "):
            lines[index] = f"  renameManifestPackage: {CLONE_PACKAGE}\n"
            found += 1
    if found != 1:
        raise ValueError("Expected exactly one packageInfo.renameManifestPackage")
    path.write_text("".join(lines), encoding="utf-8")

def patch_smali_hooks(root: Path) -> None:
    """Install hooks at the verified launch gate, options owner, and lifecycle owners.

    Purpose: Preserve the target launch contract while adding one delayed visual replay.
    Invocation: Called once for a copied exact decoded tree.
    Contract: The launch interceptor is after BaseDraggingActivity's safe-mode rejection and before
    options creation. Entry cleanup precedes pause/stop/destroy; other hooks follow the complete vendor body. Any owner drift
    fails closed before a partially patched output can pass verification.
    Verification: Fixture tests assert method-local ordering and exactly-once hook counts.
    """
    base = SmaliEditor(root / "smali/com/android/launcher3/BaseDraggingActivity.smali")
    launch = base.method(
        ".method public j0(Landroid/view/View;Landroid/content/Intent;Lcom/android/launcher3/e0;)Z"
    )
    hook = """    # Purpose: Combined Launcher Transitions launch interception.
    # Invocation: BaseDraggingActivity.j0 after safe-mode rejection and before options creation.
    # Contract: true means the controller owns one delayed replay; false preserves the original path.
    # Verification: exact signature/anchor checked by prepare_clone.py; runtime UI remains unverified.
    # Visual: tapped icon expands while visible sibling icons fly outward.
    invoke-static/range {p0 .. p3}, Lcom/ivanchan/launcher/combined/transitions/CombinedTransitionController;->interceptLaunch(Landroid/app/Activity;Landroid/view/View;Landroid/content/Intent;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :combined_launch_continue
    const/4 v0, 0x1
    return v0
    :combined_launch_continue
"""
    base.insert_after(launch, ":cond_0", hook)
    base.write()

    launcher = SmaliEditor(root / "smali/com/android/launcher3/Launcher.smali")
    options = launcher.method(".method public X(Landroid/view/View;)Landroid/app/ActivityOptions;")
    first_owner_line = next(index for index in range(options.start, options.end + 1)
                            if launcher.lines[index].strip().startswith("iget-object v0, p0"))
    options_hook = """    # Purpose: Supplies the one-shot scale-up options for the delayed replay.
    # Invocation: Launcher.X(View), before the existing G1 clip-reveal fallback.
    # Contract: null preserves G1 unchanged; non-null is consumed once for the replayed launch.
    # Verification: exact method owner checked statically; runtime transition remains unverified.
    # Visual: target app begins from the live selected-icon overlay bounds.
    invoke-static/range {p0 .. p1}, Lcom/ivanchan/launcher/combined/transitions/CombinedTransitionController;->consumeLaunchOptions(Landroid/app/Activity;Landroid/view/View;)Landroid/app/ActivityOptions;
    move-result-object v0
    if-eqz v0, :combined_options_fallback
    return-object v0
    :combined_options_fallback
"""
    launcher.lines[first_owner_line:first_owner_line] = [line + "\n" for line in options_hook.splitlines()]

    lifecycle_hooks = (
        (".method protected onCreate(Landroid/os/Bundle;)V", "onLauncherCreated", False, False),
        (".method protected onStart()V", "onLauncherStarted", False, False),
        (".method protected onResume()V", "onLauncherResumed", False, False),
        (".method protected onPause()V", "onLauncherPaused", True, False),
        (".method protected onStop()V", "onLauncherStopped", True, False),
        (".method protected onNewIntent(Landroid/content/Intent;)V", "onLauncherNewIntent", False, False),
        (".method public onWindowFocusChanged(Z)V", "onLauncherFocused", False, False),
        (".method public onConfigurationChanged(Landroid/content/res/Configuration;)V",
         "onLauncherConfigurationChanged", False, False),
        (".method public F()V", "onLauncherModelReady", False, False),
        (".method public onDestroy()V", "onLauncherDestroyed", True, False),
    )
    for signature, callback, entry, second in lifecycle_hooks:
        owner = launcher.method(signature)
        if entry:
            anchors = [index + 1 for index in range(owner.start, owner.end)
                       if launcher.lines[index].strip().startswith(".locals ")]
        else:
            anchors = [index for index in range(owner.start, owner.end)
                       if launcher.lines[index].strip() == "return-void"]
        if not anchors:
            raise ValueError(f"Missing lifecycle insertion point: {signature}")
        descriptor = "Landroid/app/Activity;"
        registers = "{p0 .. p1}" if second else "{p0 .. p0}"
        block = (f"    # Purpose: transition {callback}; "
                 + ("entry cleanup" if entry else "after complete vendor body") + ".\n"
                 "    # Contract: preserve vendor flow; generation-gated, reversible visuals only.\n"
                 "    # Verification: exact-method return/entry placement checked before assembly.\n"
                 f"    invoke-static/range {registers}, "
                 "Lcom/ivanchan/launcher/combined/transitions/CombinedTransitionController;"
                 f"->{callback}({descriptor})V\n")
        for anchor in reversed(anchors):
            launcher.lines[anchor:anchor] = block.splitlines(keepends=True)
    # Purpose: Capture HOME and consume the Nova gesture parcel before onNewIntent overwrites p1.
    # Invocation: entry; post-body hook still owns all rendering/readiness decisions.
    # Contract: no new locals or changed vendor registers; range encoding supports high registers.
    # Verification: real target parameter-write scan and fixture with p1 deliberately clobbered.
    owner = launcher.method(".method protected onNewIntent(Landroid/content/Intent;)V")
    anchor = next(launcher.lines[index].strip() for index in range(owner.start, owner.end)
                  if launcher.lines[index].strip().startswith(".locals "))
    launcher.insert_after(owner, anchor,
        "    invoke-static/range {p0 .. p1}, "
        "Lcom/ivanchan/launcher/combined/transitions/CombinedTransitionController;"
        "->recordHomeIntent(Landroid/app/Activity;Landroid/content/Intent;)V")
    launcher.write()

    application = SmaliEditor(root / "smali/com/android/launcher3/LauncherApplication.smali")
    on_create = application.method(".method public onCreate()V")
    super_call = "invoke-super {p0}, Landroid/app/Application;->onCreate()V"
    application.insert_after(on_create, super_call, """    # Purpose: Installs the application-scoped transition lifecycle and unlock receiver.
    # Invocation: LauncherApplication.onCreate immediately after Application.onCreate.
    # Contract: idempotent install; stores no Activity or View in application-scoped unlock state.
    # Verification: exact owner/anchor checked statically; broadcast delivery remains unverified.
    # Visual: enables unlock-only grid fly-in and app-return grid fly-in.
    invoke-static {p0}, Lcom/ivanchan/launcher/combined/transitions/CombinedTransitionController;->install(Landroid/app/Application;)V""")
    application.write()


def install_runtime_smali(runtime_smali: Path, output_root: Path) -> Path:
    """Install the compiled runtime's disassembly as a new, isolated dex source directory.

    Purpose: Make every injected hook resolve to an actual runtime class during Apktool assembly.
    Invocation: prepare_clone.py --runtime-smali <baksmali-output>.
    Contract: The expected package must exist and the destination must be a new smali_classesN tree;
    existing vendor dex sources are never merged into or overwritten.
    Verification: Unit tests cover directory selection, package validation, and copied class count.
    Visual: Supplies the controller that draws the icon overlay and grid transitions.
    """
    package = runtime_smali / "com/ivanchan/launcher/combined/transitions"
    classes = sorted(package.glob("*.smali"))
    required = {
        "CombinedTransitionController.smali",
        "IconOverlayView.smali",
        "UnlockSignalTracker.smali",
        "LauncherAccess.smali",
        "SnapshotGridView.smali",
        "MotionMath.smali",
        "TimingSettings.smali",
        "LiveTimingConfig.smali",
        "TransitionDiagnostics.smali",
        "NovaGestureContract.smali",
        "NovaGestureSurface.smali",
        "MiniOsAnimator.smali",
        "MiniOsGrowListener.smali",
        "MiniOsLaunchListener.smali",
        "MiniOsReturnListener.smali",
    }
    if not required.issubset({path.name for path in classes}):
        raise ValueError(f"runtime Smali package is incomplete: {package}")
    for path in classes:
        descriptor = "Lcom/ivanchan/launcher/combined/transitions/" + path.stem + ";"
        declarations = [line for line in path.read_text().splitlines() if line.startswith(".class ")]
        if len(declarations) != 1 or declarations[0].split()[-1] != descriptor:
            raise ValueError(f"Invalid runtime class descriptor: {path}")
    if any(output_root.glob("smali*/com/ivanchan/launcher/combined/transitions/*.smali")):
        raise ValueError("Runtime package already present")
    controller = (package / "CombinedTransitionController.smali").read_text()
    methods = [line.strip() for line in controller.splitlines() if line.startswith(".method ")]
    for signature in RUNTIME_METHODS:
        matches = [line for line in methods if line.split()[-1] == signature
                   and "public" in line.split() and "static" in line.split()]
        if len(matches) != 1:
            raise ValueError("Missing/duplicate runtime hook method: " + signature)
    index = 2
    while (output_root / f"smali_classes{index}").exists():
        index += 1
    destination_root = output_root / f"smali_classes{index}"
    destination = destination_root / "com/ivanchan/launcher/combined/transitions"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(package, destination)
    return destination_root


def install_resources(source_root: Path, output_root: Path) -> None:
    """Purpose: Copy transition assets while preserving the vendor's window animation theme.
    Invocation: Exact decoded clone preparation.
    Contract: MiniOS uses public clip-reveal options and native View card motion; no custom
    foreign-window return animation is installed. The existing LauncherTheme stays authoritative.
    Verification: Source fixture compares the untouched theme and copied resources.
    Visual: Android's app window appears above the expanding white card and uses native Home return.
    """
    source = source_root / "integration/res"
    for path in source.rglob("*"):
        if path.is_file():
            destination = output_root / "res" / path.relative_to(source)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)

    styles = output_root / "res/values/styles.xml"
    tree = ET.parse(styles)
    resources = tree.getroot()
    launcher_style = next((element for element in resources.findall("style")
                           if element.get("name") == "LauncherTheme"), None)
    if launcher_style is None:
        raise ValueError("LauncherTheme style not found")


def verify_output(root: Path) -> None:
    launcher = (root / "smali/com/android/launcher3/Launcher.smali").read_text(encoding="utf-8")
    base = (root / "smali/com/android/launcher3/BaseDraggingActivity.smali").read_text(
        encoding="utf-8"
    )
    application = (root / "smali/com/android/launcher3/LauncherApplication.smali").read_text(encoding="utf-8")
    for text, marker in (
        (base, "CombinedTransitionController;->interceptLaunch"),
        (launcher, "CombinedTransitionController;->consumeLaunchOptions"),
        (launcher, "CombinedTransitionController;->onLauncherCreated"),
        (launcher, "CombinedTransitionController;->onLauncherStarted"),
        (launcher, "CombinedTransitionController;->onLauncherResumed"),
        (launcher, "CombinedTransitionController;->onLauncherStopped"),
        (launcher, "CombinedTransitionController;->onLauncherNewIntent"),
        (launcher, "CombinedTransitionController;->onLauncherDestroyed"),
        (launcher, "CombinedTransitionController;->onLauncherPaused"),
        (launcher, "CombinedTransitionController;->onLauncherFocused"),
        (launcher, "CombinedTransitionController;->onLauncherConfigurationChanged"),
        (launcher, "CombinedTransitionController;->onLauncherModelReady"),
        (launcher, "CombinedTransitionController;->recordHomeIntent"),
    ):
        if marker not in text:
            raise ValueError(f"missing hook: {marker}")
    if application.count("CombinedTransitionController;->install") != 1:
        raise ValueError("missing or duplicate application hook")
    if ET.parse(root / "AndroidManifest.xml").getroot().get("package") != CLONE_PACKAGE:
        raise ValueError("clone manifest identity mismatch")
    runtime_classes = list(root.glob(
        "smali_classes*/com/ivanchan/launcher/combined/transitions/CombinedTransitionController.smali"
    ))
    if len(runtime_classes) != 1:
        raise ValueError("compiled transition runtime was not injected exactly once")


def verify_base_apk(path: Path) -> None:
    """Fail closed unless the supplied base is the exact mapped Launcher Phone OS artifact.

    Purpose: Prevent applying obfuscated method anchors to an adjacent app version.
    Invocation: Before copying or mutating the decoded tree.
    Contract: Streams the APK through SHA-256; no APK bytes are modified or retained.
    Verification: Unit tests cover the accepting and rejecting hashes with temporary fixtures.
    """
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != EXPECTED_BASE_SHA256:
        raise ValueError("--base-apk is not the mapped Launcher Phone OS 1.4.1 base APK")


def prepare_tree(output: Path, runtime_smali: Path) -> None:
    """Apply the complete verified patch to a canonical decode owned by this invocation."""
    rename_manifest(output / "AndroidManifest.xml")
    rename_explicit_internal_references(output)
    patch_apktool_identity(output / "apktool.yml")
    patch_smali_hooks(output)
    install_runtime_smali(runtime_smali, output)
    install_resources(Path(__file__).resolve().parents[1], output)
    verify_output(output)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-apk", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--runtime-smali", required=True, type=Path)
    parser.add_argument("--apktool", default="apktool")
    parser.add_argument("--framework", required=True, type=Path)
    args = parser.parse_args()
    output = args.out.resolve()
    if output.exists():
        raise FileExistsError(output)
    verify_base_apk(args.base_apk)
    version = subprocess.check_output([args.apktool, "--version"], text=True).strip()
    if version != "2.10.0":
        raise ValueError("Apktool 2.10.0 required")
    # No arbitrary --decoded input: provenance is tied directly to the verified APK bytes.
    subprocess.run([args.apktool, "d", "-p", str(args.framework.resolve()),
                    str(args.base_apk.resolve()), "-o", str(output)], check=True)
    prepare_tree(output, args.runtime_smali.resolve())
    print(f"Prepared canonical source clone: {output}; compilation was not run.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
