# Live timing controls

This test version reads a small settings file over HTTPS. Once you install a build
containing these hooks, changing that file does not require another APK or reinstall.
The previously supplied APK does not contain the hooks.

## What can change

| Setting | Default | Allowed | Effect |
| --- | --- | --- | --- |
| `returnShrinkGate` | `0.20` | `0`–`0.8` | How far the card shrinks before fly-in starts. `0.30` means 30% of the distance to icon size, not 30% of elapsed time. |
| `returnCardPower` | `8` | `1`–`12` | Card easing only. Higher values put more movement near the beginning. |
| `flyInDurationScale` | `1.0` | `0.5`–`2.0` | `1` keeps normal speed. `2` takes twice as long. It never changes automatically to compensate for the card delay. |
| `dockStartMs` | `300` | `0`–`500` | Dock delay within the original fly-in timeline; the duration multiplier also scales this delay. |
| `revision` | `1` | `0`–`1000000` | Integer identifying an edit; increase it when changing values. |

The card duration is still calculated so it and the delayed fly-in share one end
callback, including restoration of the original selected icon. Opening, unlock,
icon artwork, corners and movement paths are unchanged.

## Change the timing

1. Edit `live-timing/timing.properties`. Keep all five keys. To try a later fly-in
   without changing its speed, change only `returnShrinkGate` and `revision`.
2. Publish the complete file at your configured HTTPS URL. Replace it atomically:
   upload to a temporary filename, then rename it over the served file. Do not
   partially overwrite a file while the phone may be downloading it.
3. Leave Home visible and connected to the internet. It checks after each completed
   request plus 1.5 seconds. A slow connection can take longer.
4. Open an app and return Home. Each return captures one complete configuration;
   an edit during that animation takes effect on a later return.
5. To undo an experiment, publish the default file again. Restoring an older
   revision is permitted.

The example launcher uses:
`https://204-168-163-118.sslip.io/trackers/static/launcher-live-timing/timing.properties`

That address is public and read-only to the phone. It contains numbers, no keys,
commands or executable code. Only someone with write access to the server can
publish changes. Every installed test copy using this URL receives the same values.

## Use in another launcher

Copy the runtime and follow the existing host integration guide. Live tuning is
**opt-in** in the reusable module. After the normal controller installation, call:

```java
CombinedTransitionController.install(this);
LiveTimingConfig.install(this, "https://your-server.example/timing.properties");
```

Use your own endpoint. Keep the controller's existing resume, pause, stop and destroy
callbacks connected: these start and stop polling. The runtime manifest declares
`INTERNET` and `ACCESS_NETWORK_STATE`. No special permission, root or ADB is needed.

## Offline and invalid settings

The last valid file is saved privately on the phone. Failed requests, invalid
numbers, missing/unknown keys and responses over 4 KiB do not replace it. A fresh
installation uses the built-in defaults until a valid response arrives. Cache loading
is asynchronous, so the first frame after a cold process start can use defaults.

Networking and cache writes run on one worker, not the UI thread. Polling stops when
Home is inactive; an in-flight bounded request may finish saving its result.
Status changes and revision numbers use the existing bounded automatic diagnostic
uploader. No configuration text or app names are added to those events.

Source/math and HTTPS delivery checks are separate from phone verification. The
live-hook APK still needs its first authorized compile and a real-device check.
