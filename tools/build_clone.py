#!/usr/bin/env python3
"""Purpose: Reproducible exact-input base+split preparation and explicitly gated compilation.
Invocation: --check for read-only prerequisite/input validation; --compile-authorized to build.
Contract: No Android compiler or decoder runs in --check. Fresh work only, hashes before decode,
Apktool 2.10.0, AGP 8.10.1/Gradle 8.11.1, SDK 36, same signing key for all three APKs.
Verification: Host tests cover input rejection, extraction and authorization; no build yet.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from patcher.prepare_clone import (ORIGINAL_PACKAGE, rename_manifest,
                                  patch_apktool_identity, prepare_tree)
XAPK_SHA = "da78a4c182798ee3222c26f85ae2c8a407d35c293b8aaedfe013b1f3e49ee67f"
INPUTS = {
    ORIGINAL_PACKAGE + ".apk": (56972176, "40161f556b783375b0a528f6810efa1233454398bf7bd4f3940e6a51d19f2ddc"),
    "config.arm64_v8a.apk": (2344033, "e2318e31ddb030205522c4193113b33e79c919f082e6a29ecd97617ce896f165"),
    "config.xxhdpi.apk": (547612, "b9c75078a2afbf2dc9c248411b2845f469a38ca60e8167107c8b787f35d127fe"),
}
OUTPUTS = ("launcher-phone-os-combined-release.apk",
           "launcher-phone-os-combined-arm64_v8a.apk",
           "launcher-phone-os-combined-xxhdpi.apk")

def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def validate_inputs(path: Path) -> None:
    if sha(path) != XAPK_SHA:
        raise ValueError("Not the mapped Launcher Phone OS 1.4.1 XAPK")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        for name, (size, digest) in INPUTS.items():
            if names.count(name) != 1 or archive.getinfo(name).file_size != size:
                raise ValueError("Missing, duplicate or wrong-sized APK: " + name)
            with archive.open(name) as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                    raise ValueError("APK hash mismatch: " + name)

def run(args: list[str], env: dict[str, str]) -> None:
    subprocess.run(args, check=True, cwd=ROOT, env=env)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--compile-authorized", action="store_true")
    parser.add_argument("--xapk", required=True, type=Path)
    parser.add_argument("--work", type=Path, default=ROOT / "work/release")
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--alias")
    args = parser.parse_args()
    validate_inputs(args.xapk)
    sdk = Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk")).resolve()
    gradle = Path(os.environ.get("GRADLE_RUNNER", str(ROOT / "work/toolchain/gradle-8.11.1/bin/gradle"))).resolve()
    apktool = shutil.which("apktool")
    baksmali = shutil.which("baksmali")
    java = shutil.which("java")
    required = [sdk / "platforms/android-36/android.jar", gradle,
                *[sdk / "build-tools/36.0.0" / name for name in ("d8", "apksigner", "zipalign")]]
    if not apktool or not baksmali or not java or any(not path.is_file() for path in required):
        raise ValueError("Missing toolchain; follow README prerequisites / bootstrap_gradle.py")
    if not (gradle.parent.parent / "lib/gradle-launcher-8.11.1.jar").is_file():
        raise ValueError("GRADLE_RUNNER must belong to the pinned Gradle 8.11.1 distribution")
    if subprocess.check_output([apktool, "--version"], text=True).strip() != "2.10.0":
        raise ValueError("Apktool 2.10.0 required")
    java_info = subprocess.run([java, "-version"], capture_output=True, text=True, check=True)
    if not any(f'version "{major}.' in java_info.stderr for major in (17, 21)):
        raise ValueError("Use JDK 17 or 21")
    print("Exact XAPK and three member APK hashes match; SDK/tool executables present.")
    if args.check:
        print("SOURCE PREFLIGHT ONLY: no Gradle, D8, Apktool decode/build, signing or install invoked.")
        return 0
    if not args.keystore or not args.keystore.is_file() or not args.alias:
        raise ValueError("Provide --keystore and --alias")
    if not os.environ.get("CLONE_STORE_PASS") or not os.environ.get("CLONE_KEY_PASS"):
        raise ValueError("Set CLONE_STORE_PASS and CLONE_KEY_PASS privately; never put them in Git")
    work = args.work.resolve()
    if work.exists():
        raise FileExistsError("Choose a fresh --work directory; existing work is never overwritten")
    for name in (*OUTPUTS, "launcher-phone-os-combined-release.apks"):
        if (ROOT / name).exists():
            raise FileExistsError("Archive the previous release before replacing " + name)
    work.mkdir(parents=True)
    tmp = work / "tmp"; tmp.mkdir()
    env = dict(os.environ, ANDROID_HOME=str(sdk), TMPDIR=str(tmp),
               GRADLE_USER_HOME=str(ROOT / "work/gradle-cache"),
               JAVA_TOOL_OPTIONS=f"-Xmx1024m -Djava.io.tmpdir={tmp}")
    run([str(gradle), "--no-daemon", "--max-workers=1", "-p", str(ROOT),
         ":runtime:assembleRelease"], env)
    runtime = work / "runtime"; runtime.mkdir()
    aar = ROOT / "runtime/build/outputs/aar/runtime-release.aar"
    with zipfile.ZipFile(aar) as archive:
        (runtime / "classes.jar").write_bytes(archive.read("classes.jar"))
    dex = runtime / "dex"; dex.mkdir()
    run([str(sdk / "build-tools/36.0.0/d8"), "--release", "--min-api", "24",
         "--lib", str(sdk / "platforms/android-36/android.jar"),
         "--output", str(dex), str(runtime / "classes.jar")], env)
    if len(list(dex.glob("*.dex"))) != 1:
        raise ValueError("Expected exactly one runtime dex")
    smali = runtime / "smali"
    run([baksmali, "disassemble", str(dex / "classes.dex"), "-o", str(smali)], env)
    framework = work / "framework"
    artifacts = []
    with zipfile.ZipFile(args.xapk) as archive:
        for index, name in enumerate(INPUTS):
            original = work / name
            original.write_bytes(archive.read(name))  # Exact allowlisted, size/hash-checked member only.
            decoded = work / ("decoded-" + str(index))
            run([apktool, "d", "-p", str(framework), str(original), "-o", str(decoded)], env)
            if index == 0:
                prepare_tree(decoded, smali)
            else:
                rename_manifest(decoded / "AndroidManifest.xml")
                patch_apktool_identity(decoded / "apktool.yml")
            unsigned = work / ("unsigned-" + str(index) + ".apk")
            aligned = work / ("aligned-" + str(index) + ".apk")
            signed = work / OUTPUTS[index]
            run([apktool, "b", "-p", str(framework), str(decoded), "-o", str(unsigned)], env)
            run([str(sdk / "build-tools/36.0.0/zipalign"), "-P", "16", "4",
                 str(unsigned), str(aligned)], env)
            run([str(sdk / "build-tools/36.0.0/apksigner"), "sign", "--ks",
                 str(args.keystore.resolve()), "--ks-key-alias", args.alias,
                 "--ks-pass", "env:CLONE_STORE_PASS", "--key-pass", "env:CLONE_KEY_PASS",
                 "--out", str(signed), str(aligned)], env)
            run([str(sdk / "build-tools/36.0.0/apksigner"), "verify", "--verbose", str(signed)], env)
            run([str(sdk / "build-tools/36.0.0/zipalign"), "-c", "-P", "16", "4", str(signed)], env)
            artifacts.append(signed)
    for signed in artifacts:
        shutil.copy2(signed, ROOT / signed.name)
        if sha(signed) != sha(ROOT / signed.name):
            raise ValueError("Top-level artifact copy mismatch")
    bundle = ROOT / "launcher-phone-os-combined-release.apks"
    with zipfile.ZipFile(bundle, "x", compression=zipfile.ZIP_STORED) as archive:
        for signed in artifacts:
            archive.write(signed, signed.name)
    for signed in artifacts:
        print(signed.name, sha(ROOT / signed.name))
    print("Compiled/signed set is at repository top level. Not installed or UI-tested.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
