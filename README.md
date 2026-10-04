# Combined Launcher Transitions

A source-only patch kit for **Launcher Phone OS 1.4.1 (version code 41)**, installed
separately as **com.ivanchan.launcher.combined**. Vendor APKs are not distributed here.

| Action | Implemented source behavior |
| --- | --- |
| First eligible unlock / cold Home entry | iLauncher-style complete-icon grid fly-in only |
| Open an external app | Nova-derived selected artwork expansion plus reverse grid flight |
| Return to Home | System-supplied Nova gesture contract plus grid fly-in; window-style fallback without a contract |

**Status — 2026-10-04: experimental, with a known visual failure.** The 2026-10-03 build
compiled and signed, and 21 source tests passed. The user reported that the selected icon
did not expand and moved downward instead of the expected direction. That issue is unresolved;
compilation is not proof of Nova visual parity. This repository publishes the current source,
not a claim of a corrected animation. OEM navigation behavior remains unverified.
No protection or licensing code is bypassed.

For the separate runtime and integration guide, see
[launcher-combined-animations](https://github.com/IvanChanPing/launcher-combined-animations).

## 1. Prerequisites

Use Linux, Python 3.11+, JDK 17 or 21, Android SDK platform 36 and build-tools 36.0.0,
Apktool **2.10.0**, and Baksmali **2.5.2**. Set ANDROID_HOME to your SDK.
The checked-in dependency declaration pins AGP 8.10.1. Install its pinned Gradle
distribution without compiling anything:

```sh
python3 tools/bootstrap_gradle.py
```

Keep this repository, its work folder, SDK caches and temporary files on a data volume.
On this server the project is /root/agent-work/projects/launcher-phone-os-combined.
The bootstrap verifies the official distribution SHA-256 before extraction.
No secrets belong in the repository.

## 2. Supply the exact original XAPK

Use your lawful copy of:
com.iphonelauncher.ioslauncher.launcherios.ios19@1.4.1.xapk

Expected XAPK SHA-256:
da78a4c182798ee3222c26f85ae2c8a407d35c293b8aaedfe013b1f3e49ee67f

The preparation validates the base APK and both required ARM64/xxhdpi splits independently.
It does not accept an arbitrary decoded directory. Save the input outside Git, then run:

```sh
export ANDROID_HOME=/opt/android-sdk
export TMPDIR=/root/agent-work/tmp
export PYTHONDONTWRITEBYTECODE=1
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar" python3 -m unittest discover -s tests -v
python3 tools/build_clone.py --check --xapk /absolute/path/to/original.xapk
```

These checks do not invoke Gradle, D8, APK assembly, signing, or installation.

## 3. Compile only after explicit authorization

This section is instructions for later, **not authorization to run it now**.
Supply your existing private signing keystore and alias. All three APKs must use the same key.
Read passwords privately; do not put literal passwords in commands, logs, or Git.

```sh
read -rsp 'Keystore password: ' CLONE_STORE_PASS; echo
read -rsp 'Key password: ' CLONE_KEY_PASS; echo
export CLONE_STORE_PASS CLONE_KEY_PASS
python3 tools/build_clone.py --compile-authorized \
  --xapk /absolute/path/to/original.xapk \
  --keystore /private/path/clone.jks --alias clone \
  --work work/release-001
unset CLONE_STORE_PASS CLONE_KEY_PASS
```

The command compiles the runtime, converts it with D8 (including the SDK library),
disassembles it, freshly decodes the exact base and splits, patches identity/hooks/resources,
assembles, aligns, signs, verifies and copies the complete set to the repository top level:

- launcher-phone-os-combined-release.apk
- launcher-phone-os-combined-arm64_v8a.apk
- launcher-phone-os-combined-xxhdpi.apk
- launcher-phone-os-combined-release.apks (ZIP containing those three APKs)

Existing work or release files are never silently overwritten. Archive a previous release
before building another. After a future successful build, record and Git-track those top-level
artifacts together with the timestamped changelog. No repository publication happens automatically.

## 4. Install and test later

Install **all three APKs together**, on a compatible ARM64 device:

```sh
adb install-multiple launcher-phone-os-combined-release.apk \
  launcher-phone-os-combined-arm64_v8a.apk launcher-phone-os-combined-xxhdpi.apk
```

Choose the clone in Android's default Home UI. Do not uninstall or replace your current launcher.
Then follow docs/IMPLEMENTATION.md's test matrix. A successful build alone does not prove any
transition looks correct. PairIP/vendor service behavior with a renamed, re-signed app remains
a runtime risk; this kit does not remove those checks.

## Where to change or reuse it

- runtime/src/main/java/com/ivanchan/launcher/combined/transitions — nine runtime owners.
- patcher/prepare_clone.py — exact vendor launch/lifecycle hooks, identity and window selectors.
- integration/res/anim — foreground return and opaque Home entry resources.
- tools/build_clone.py — complete three-APK workflow.
- docs/IMPLEMENTATION.md — ordered flow, formulas, owner mapping and test matrix.
- docs/PRE_BUILD_RISK_PASS.md — evidence and unresolved runtime boundaries.

For another launcher, replace only LauncherAccess and the exact hook/identity map after mapping
that launcher's real contracts. Do not blindly apply this version-specific Smali patch.

## Diagnostics notice

The current runtime uploads bounded transition event codes to the author's collector;
see [Diagnostics and privacy](docs/IMPLEMENTATION.md#diagnostics-and-privacy) for the endpoint
and fields. Review that behavior before reusing the source in another application.
