# Gametap

Turns an Android or iOS phone into a Bluetooth gamepad. The phone presents itself as a
standard HID gamepad, so the host needs no companion app and no driver.

Built with Kotlin Multiplatform and Compose Multiplatform. The HID descriptor, report
encoding, input handling and UI are all shared; platform code only moves bytes over the radio.

## Status

| Part | State |
|------|-------|
| Shared code (HID, input, sender, connection logic) | Unit tested |
| Android app with a Linux host | Used on a Redmi Pad SE; every control tested one at a time |
| Multi-touch | Unit tested in the tracker; used in a game on the Redmi Pad SE, not checked control by control |
| Leaving the app and coming back | Tested on the Redmi Pad SE, see [Using it](#using-it) |
| Windows and macOS hosts | **Not yet tested** |
| iOS code | Compiles against the iOS SDK bindings; **never linked or run** |
| iOS as a gamepad | **Unproven, see below** |

## Build

Requires JDK 17+ and the Android SDK with platform 37.

```sh
./gradlew :composeApp:testAndroidHostTest   # unit tests
./gradlew :androidApp:assembleDebug         # APK in androidApp/build/outputs/apk/debug/
./gradlew :composeApp:compileKotlinIosArm64 # type-checks iosMain, also on Linux
```

iOS needs a Mac with Xcode:

```sh
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

Set your signing team in Xcode, then run on a real device. The simulator has no Bluetooth.

## Using it

1. Tap **Start gamepad** and allow the Bluetooth prompts.
2. On the host, open Bluetooth settings and pair with the phone.
3. The gamepad opens when the host connects.

After the first pairing, **Start gamepad** reconnects to that host by itself. The discoverable
prompt only appears if no paired host reconnects within 5 seconds.

On Android, the host loses the gamepad about 2 seconds after Gametap leaves the screen, for
example after Home or locking the phone. Android unregisters any HID device app that is no
longer visible. Coming back reconnects by itself, in about a second, unless you pressed **Stop**.
Any control held when you leave is released first.

Hold the three dots in the top-left corner of the gamepad to open settings.

To check the result on a host, use Windows `joy.cpl`, <https://hardwaretester.com/gamepad>,
Steam controller settings, or `evtest` / `jstest` on Linux.

## Known limitations

- **XInput-only games.** Hosts see a generic HID gamepad (DirectInput-style on Windows). Steam,
  emulators and most modern games accept it. XInput-only games need Steam Input or a mapper.
- **Android phones without the HID Device profile.** Some makers disable it. The app then shows
  "Not supported on this device".
- **No connection in the background on Android.** Games may pause or report a disconnected
  controller while you are in another app. Staying connected would need a foreground service,
  which shows a notification while it runs. It is not built, and HyperOS and similar systems may
  stop it anyway.
- **Descriptor changes.** Hosts cache the descriptor. Remove the device from the host's Bluetooth
  settings, forget the host on the phone, and pair again after any change.
- **Placeholder iOS PnP ID.** It uses pid.codes test values. Replace it before release.

## Latency

Measured on a Redmi Pad SE paired with a Linux PC, using injected touches:

| Stage | Median | p90 |
|-------|--------|-----|
| Touch event to report sent | 10 ms | 15 ms |
| Report sent to PC, including after a pause | 17 ms | 22 ms |

The link dominates. An idle link falls into Bluetooth sniff subrating, which raised send-to-PC
time to a median of 66 ms and a maximum of 180 ms. Android has no public API to turn sniff off, so
`ReportSender` repeats the last report every 16 ms while the gamepad screen is open. Do not remove
this to save traffic. It costs some battery, so it stops when the gamepad screen closes or the
app leaves the foreground.

Reports are capped at one per 8 ms. Stricter HID QoS settings did not improve latency.

## Axis mapping

| Control | HID usage |
|---------|-----------|
| Left stick | X, Y |
| Right stick | Z, Rz |
| Left trigger | Brake |
| Right trigger | Accelerator |

Button bits follow the slots Linux and Android assign, so the gaps are required:

| Bit | 0 | 1 | 3 | 4 | 6 | 7 | 10 | 11 | 12 | 13 | 14 |
|-----|---|---|---|---|---|---|----|----|----|----|----|
| Button | A | B | X | Y | LB | RB | Select | Start | Home | L3 | R3 |

Do not put the triggers on Rx/Ry. SDL on Linux, which Steam uses, reads Rx/Ry as the right stick
when they exist and then reads Z/Rz as the triggers, which crosses the two controls.

## iOS risks

iOS has no public HID device role, so `IosHogpTransport` builds the HID GATT service by hand.
These points are the first things to check on a device:

- Apple has reportedly reserved the HID service for the system. Full 128-bit UUIDs are used
  because an open-source app is reported to get past the check that way, but that app only
  ships keyboard and mouse, so a **gamepad** descriptor is unproven.
- `CBMutableDescriptor` is documented to accept only two descriptor types. The Report Reference
  descriptor (`0x2908`) is neither, so creating it may throw at runtime.
- CoreBluetooth advertises only a name and service UUIDs, so gamepad appearance (`0x03C4`) cannot
  be advertised. Hosts may show a generic icon or filter the device out.
- iOS does not reveal the host name, so the status shows a short identifier instead.

If iOS refuses the service, the status pill shows the error. A keyboard and mouse fallback is
the planned mitigation and is not built.

## Layout

```
composeApp/   KMP module: commonMain, androidMain, iosMain, commonTest
androidApp/   Thin Android app: manifest, Application, MainActivity
iosApp/       SwiftUI entry point, Info.plist, XcodeGen spec
```

`androidApp` is separate because AGP 9 does not allow `com.android.application` alongside the
Kotlin Multiplatform plugin.

Versions are the newest stable set in the Kotlin Multiplatform compatibility table
(Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0). `compileSdk` is 37 because Compose requires it.

## License

Copyright (C) 2026 Robbie Ardiansyah

Gametap is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE).
