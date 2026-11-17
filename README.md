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
| Volume keys | Used on a Redmi Pad SE; about 150 ms slower than on-screen buttons, see [Volume keys](#volume-keys) |
| Gyro | Tilt math and controls unit tested. iOS reader checked against CoreMotion's gravity on an iPhone 13 mini; **tilting not yet tried on a device** on either platform |
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

### Gyro

Tap **Gyro** in the layout editor to drive outputs by tilting the phone. Each stick takes the
horizontal tilt, the vertical tilt or both; each trigger takes one direction of one axis, such as
"pulls when tilted right". One tilt can drive several outputs. Horizontal is either steering
(turning the phone like a wheel) or turning (around its vertical axis), set per layout.

Tilt is measured from the angle the phone has when gyro turns on. Three button actions control
it: **Gyro on/off**, which starts off with each layout, **Gyro on while held**, and **Recenter
gyro**. A layout can use one of the first two, not both. With neither, gyro is on whenever the
gamepad is open.

Gyro adds to the touch stick, after the stick's response curve, and a gyro trigger and a touch
trigger take the higher value.

The sensor is Android's game rotation vector (gyroscope and accelerometer, no compass), read at
200 Hz only while the gamepad is on screen and the layout has gyro bindings. Devices without a
gyroscope, such as many budget tablets, fall back to the accelerometer alone. It measures
steering and forward/back tilt from the direction of gravity, smoothed over about 20 ms to hide
the phone's own movement, and cannot measure turning. The editor says when this applies. While gyro is active
the input keeps changing, so reports go out at the 8 ms cap instead of the 16 ms keep-alive.

On iOS the sensor is CoreMotion's device motion, also gyroscope and accelerometer without the
compass, at 100 Hz. Every device iOS 16 runs on has a gyroscope, so there is no fallback.

### Volume keys

Tap **Keys** in the layout editor to bind the volume keys. Each key can send any action a button
can, including the gyro ones, and sends it while held. A bound key no longer changes the volume
while the gamepad is on screen; an unbound key still does, and both keys change the volume again
while the menu is open. Volume keys have no on-screen control, so no button lights up and there is
no haptic tick.

Volume keys are slower than on-screen buttons. Android holds each press back about 150 ms, on
both keys, to check for shortcuts such as volume down + power, and no app setting turns that off.
Measured on a Redmi Pad SE from 50 presses (`adb logcat -s GametapKeys` logs each one): a held
press reached the app after 153 to 155 ms, the release after 3 to 6 ms. A tap released before
that wait ends arrives as a press and release a few milliseconds apart, so every press is held
for at least 40 ms to make sure the host sees it. Use the keys for actions where timing matters
less, such as gyro hold or recenter. An accessibility service would get the keys before the
wait; it is not built.

Android only. iOS has no public API for the volume keys: the usual workaround watches the system
volume, which cannot tell a hold from a press and still changes the volume. The editor hides
**Keys** on iOS.

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

Measured on a Redmi Pad SE paired with a Linux PC, from 159 injected button presses and releases
(`cmd input motionevent`) with pauses of 0.3 to 0.7 s between them:

| Stage | Median | p90 | Max |
|-------|--------|-----|-----|
| Input dispatcher to report sent | 6 ms | 11 ms | 13 ms |
| Report sent to PC | 11 ms | 17 ms | 24 ms |
| End to end | 18 ms | 24 ms | 30 ms |

On the tablet, `atrace` timed each touch from Android's input dispatcher to the app's
`sendReport` binder call. On the PC, the times come from evdev. The two clocks were synced over
adb to within about 1.5 ms. The touchscreen's own scan delay is not included, so a real finger
adds a few milliseconds.

The link dominates. An idle link falls into Bluetooth sniff subrating, which raised send-to-PC
time to a median of 66 ms and a maximum of 180 ms. Android has no public API to turn sniff off, so
`ReportSender` repeats the last report every 16 ms while the gamepad screen is open. Do not remove
this to save traffic. It costs some battery, so it stops when the gamepad screen closes or the
app leaves the foreground.

Reports are capped at one per 8 ms. Both intervals count from when a send starts, because
`sendReport` blocks for about 1.5 ms and occasionally 10 ms. Timers fire slightly late, so the
keep-alive measures about 17 ms. Stricter HID QoS settings did not improve latency.

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
