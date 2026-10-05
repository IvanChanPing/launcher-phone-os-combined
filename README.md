# Combined Launcher Transitions

A patch kit for **Launcher Phone OS 1.4.1 (version code 41)**. The clone installs
as **com.ivanchan.launcher.combined**, alongside the original launcher.

| Action | Animation |
| --- | --- |
| First unlock or Home startup | The Home-screen icons enter, and the bottom app bar slides up separately |
| Open an app | The tapped icon expands into a white card that fills the screen while the other icons leave |
| Return to Home | The card shrinks back into the app icon while the Home-screen icons and bottom bar return |

The expanding card uses the same corner shape as the tapped icon. Its corners become square
as it fills the screen, then return as the card shrinks back into the icon.

For animation code and instructions for integrating it into any Android launcher, see
[launcher-combined-animations](https://github.com/IvanChanPing/launcher-combined-animations).

## Requirements

Use Linux, Python 3.11+, JDK 17 or 21, Android SDK platform 36 and build-tools 36.0.0,
Apktool **2.10.0**, and Baksmali **2.5.2**. Set ANDROID_HOME to your SDK.
The project uses AGP 8.10.1 and Gradle 8.11.1. Install Gradle:

```sh
python3 tools/bootstrap_gradle.py
```

## Source APK

Provide:
com.iphonelauncher.ioslauncher.launcherios.ios19@1.4.1.xapk

Expected XAPK SHA-256:
da78a4c182798ee3222c26f85ae2c8a407d35c293b8aaedfe013b1f3e49ee67f

The script checks the base APK and both ARM64/xxhdpi splits. Run the source checks:

```sh
export ANDROID_HOME=/path/to/android-sdk
export PYTHONDONTWRITEBYTECODE=1
ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar" python3 -m unittest discover -s tests -v
python3 tools/build_clone.py --check --xapk /absolute/path/to/original.xapk
```

## Build

Supply a signing keystore and alias. All three APKs use the same key.

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

The script builds and signs the clone, then places these files in the project root:

- launcher-phone-os-combined-release.apk
- launcher-phone-os-combined-arm64_v8a.apk
- launcher-phone-os-combined-xxhdpi.apk
- launcher-phone-os-combined-release.apks (ZIP containing those three APKs)

Use a fresh work directory and archive previous output files before rebuilding.

## Install

Install **all three APKs together**, on a compatible ARM64 device:

```sh
adb install-multiple launcher-phone-os-combined-release.apk \
  launcher-phone-os-combined-arm64_v8a.apk launcher-phone-os-combined-xxhdpi.apk
```

Select the clone in Android's default Home settings.

## Project layout

- runtime/src/main/java/com/ivanchan/launcher/combined/transitions — animation classes and launcher adapter.
- patcher/prepare_clone.py — patches the launcher package name, app-opening path and lifecycle callbacks.
- integration/res/anim — window animation resources used by the clone.
- tools/build_clone.py — complete three-APK workflow.
- docs/IMPLEMENTATION.md — launcher hooks, animation timing and test matrix.

This patcher targets Launcher Phone OS. The separate animation repository explains how to
connect the animation code to other launchers through their own adapters and lifecycle hooks.

## Diagnostics

`TransitionDiagnostics` uploads a small event log containing animation steps, run IDs and Android version;
see [Diagnostics and privacy](docs/IMPLEMENTATION.md#diagnostics-and-privacy) for details.
