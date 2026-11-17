# Gametap

Turns an Android or iOS phone into a Bluetooth gamepad. The phone presents itself as a
standard HID gamepad over Bluetooth Low Energy (HID over GATT), so the host needs no companion
app and no driver.

Built with Kotlin Multiplatform and Compose Multiplatform. The HID descriptor, report
encoding, input handling and UI are all shared; platform code only moves bytes over the radio.

## Status

| Part | State |
|------|-------|
| Shared code (HID, input, sender, connection logic) | Unit tested |
| Android app with a Linux host | Pairs, reconnects and sends buttons on a Redmi Pad SE and a Galaxy A54, see [Latency](#latency). Every control was tested one at a time on the Redmi Pad SE, but on classic Bluetooth, before the move to Low Energy |
| Multi-touch | Unit tested in the tracker; used in a game on the Redmi Pad SE (classic Bluetooth), not checked control by control |
| Leaving the app and coming back | Tested on the Redmi Pad SE, see [Using it](#using-it) |
| Volume keys | Used on a Redmi Pad SE; about 150 ms slower than on-screen buttons, see [Volume keys](#volume-keys) |
| Gyro | Tilt math and controls unit tested. iOS reader checked against CoreMotion's gravity on an iPhone 13 mini; **tilting not yet tried on a device** on either platform |
| Windows hosts | **Not yet tested** |
| macOS host | An iPhone pairs. Android not tried |
| iOS as a gamepad | Runs on an iPhone. Pairs with Linux and macOS, and Linux creates the gamepad device. Buttons not checked one at a time, see [iOS risks](#ios-risks) |

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

1. Tap **Start gamepad** and allow the Bluetooth prompts. The phone starts advertising.
2. On the host, open Bluetooth settings and pair with the phone. Both sides show a 6-digit code;
   confirm it on both. On Linux this needs a pairing agent, see [Linux hosts](#linux-hosts).
3. The gamepad opens when the host connects.

After the first pairing, the host reconnects by itself, usually within a second of **Start
gamepad**.

On Android, the host loses the gamepad about 2 seconds after Gametap leaves the screen, for
example after Home or locking the phone. Gametap stops advertising then, so the phone does not
keep the radio busy for a game that cannot see the screen. Coming back reconnects by itself, in
under a second, unless you pressed **Stop**. Any control held when you leave is released first.

On iOS the gamepad keeps running in the background. If you close Gametap from the app switcher
or restart the phone, a Linux host may stop reconnecting, see [Linux hosts](#linux-hosts).

Hold the ☰ button on the gamepad to open the menu, where you switch layouts, edit the current one
or exit.

### Sticks and haptics

Each stick has its own settings, saved with the layout. Select a stick in the layout editor to set
its **deadzone** (0 to 40% of its travel sends nothing), its **sensitivity curve** (linear, or up
to 3 for finer control near the center) and whether it is **floating**, centered where the thumb
lands instead of at its drawn position. Tap **Haptics** in the editor to turn the vibration on
press on or off for the layout; the button is hidden on devices that cannot vibrate.

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
- **Android phones that cannot advertise over Bluetooth Low Energy.** Mostly older or budget
  devices. The app then says the phone cannot act as a controller.
- **No connection in the background on Android.** Games may pause or report a disconnected
  controller while you are in another app. Staying connected would need a foreground service,
  which shows a notification while it runs. It is not built, and HyperOS and similar systems may
  stop it anyway.
- **Descriptor changes.** Hosts cache the descriptor. Remove the device from the host's Bluetooth
  settings, forget the host on the phone, and pair again after any change.
- **Placeholder PnP ID.** `HogpProfile.pnpId` uses pid.codes test values (1209:0001). Replace it
  before release.
- **The host may show the phone's own name and ID.** See [Linux hosts](#linux-hosts).

### Linux hosts

These were found with BlueZ 5.87. Other desktops and BlueZ versions may differ.

- **A pairing agent must be running.** Pairing needs a 6-digit code confirmed on both sides, and
  BlueZ hands that to a separate agent program. GNOME, KDE and shells such as Noctalia include
  one; a bare compositor such as niri does not. Without one, pairing fails with
  `No agent available` in `journalctl -u bluetooth`. Run `bluetoothctl` in a terminal while
  pairing, or start an agent such as `blueman-applet`. An agent registered before bluetoothd
  restarts is lost, so restart the shell or agent after restarting Bluetooth.
- **Phones pair over classic Bluetooth too.** Pairing over Low Energy also gives the host classic
  Bluetooth keys, and BlueZ shows one device with both. A plain connect, including
  `bluetoothctl connect` and most desktop Connect buttons, uses whichever was used last, which is
  often classic. That fails or connects only the phone's audio. To connect the gamepad, open
  Gametap and run, with the phone's address:

  ```sh
  busctl --system call org.bluez /org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF org.bluez.Bearer.LE1 Connect
  ```

  It may need a scan running first (`bluetoothctl scan le`). Once the gamepad has connected,
  BlueZ reconnects it by itself. To make plain connects use Low Energy, set `Experimental = true`
  in `/etc/bluetooth/main.conf`, restart Bluetooth, and set
  `busctl --system set-property org.bluez /org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF org.bluez.Device1 PreferredBearer s le`.
  This last step is untested.
- **If an iPhone stops reconnecting.** When Gametap is closed, the host forgets the gamepad
  service. If the iPhone reconnects to the host in that state, for example after a restart, the
  host no longer reconnects the gamepad. Open Gametap and connect as above.
- **Phones send their audio to the PC.** PipeWire lets phones use the PC as a speaker and call
  headset by default, and the phone then plays game sounds through the PC. To turn that off for
  all phones while keeping Bluetooth headphones working, create
  `~/.config/wireplumber/wireplumber.conf.d/51-no-phone-audio.conf` with
  `monitor.bluez.properties = { bluez5.roles = [ a2dp_source hfp_ag bap_source ] }` and run
  `systemctl --user restart wireplumber`.
- **The gamepad may show the phone's name and ID.** BlueZ names the gamepad after the phone and
  takes the vendor and product ID from the device record it saw last: the phone's own classic
  Bluetooth record (for example Apple 004C:7504) or Gametap's PnP ID. The ID can change between
  connections, so Steam may treat it as a new controller. Apps cannot change this; the fix would
  be in BlueZ.

## Latency

Measured against a Linux PC from injected presses of A (`input motionevent` over adb, 0.7 s
apart), comparing the phone's clock when the app had received the touch with evdev's time on the
PC. The clocks were synced over adb to within about 6 to 8 ms, so the table gives the spread
between the fastest and slowest press in each run of 15, which does not depend on that sync.
Every press arrived.

| Device | Transport | Presses | Spread per run | Notes |
|--------|-----------|---------|----------------|-------|
| Redmi Pad SE | Low Energy | 75 | 17 to 64 ms | Mostly within 20 ms; 2 presses near 60 ms |
| Galaxy A54 | Low Energy, phone Wi-Fi off | 60 | 12 to 42 ms | |
| Galaxy A54 | Low Energy, phone Wi-Fi on | 60 | 47 to 270 ms | Two of four runs had stretches of 100 to 250 ms delays, see below |
| Galaxy A54 | Classic, before the move | 30 | 391 to 709 ms | Phone connected to the PC |
| Galaxy A54 | Classic, before the move | 30 | 31 to 36 ms | PC connected to the phone |
| Redmi Pad SE | Classic, before the move | 30 | 22 to 49 ms | |

**Why Low Energy.** Classic Bluetooth HID puts an idle link into sniff mode, a power saving state
where the radio only wakes at intervals. Samsung phones let it sleep for up to 750 ms, and kept
doing so while reports were flowing. That stalled presses and occasionally lost them. It was
worst when the PC was central, the side that runs the radio schedule, and Linux takes that role
whenever the phone connects, because BlueZ's audio services ask for it. Apps cannot choose the
role or turn sniff off. Low Energy has no sniff mode: the host polls at a fixed connection
interval and a report goes out at the next poll, whichever side connected.

**Phone Wi-Fi.** Phones share one radio between Wi-Fi and Bluetooth, which take turns. On the
Galaxy A54, with Wi-Fi on, delays rose to 100 to 250 ms for stretches of several seconds, then
recovered; with Wi-Fi off, every run stayed within 42 ms, on the same build and connection. The
Redmi Pad SE, tested with Wi-Fi on, only had occasional presses near 60 ms. Whether moving the
phone to 5 GHz Wi-Fi is enough has not been tested. If a phone stutters, try it with Wi-Fi off.

**Connection interval.** Android gives a GATT server no way to ask for a short interval, so
`AndroidHogpTransport` also opens a GATT client back to the host on the same link and requests high
priority (11.25 to 15 ms). Samsung's Bluetooth log shows Linux starting the link at 45 ms; once
the request has been accepted, BlueZ saves it and later connections start at 15 ms. Without the
request the A54's spread was 85 to 102 ms against 43 to 70 ms with it, but both were measured with
the phone's Wi-Fi on, so treat that comparison as rough.

**Report pacing.** Reports are capped at one per 8 ms, and `ReportSender` repeats the last report
every 16 ms while the gamepad screen is open. The repeat was added because classic Bluetooth
fell into sniff mode on an idle link. On Low Energy it still helps a little: on the Redmi Pad SE,
with it the p90 was 18 ms above the fastest press (90 presses), without it 28 ms (45 presses). It
stops when the gamepad screen closes. Both intervals count from when a send starts. Android allows one
notification in flight, so a newer report replaces one still waiting to go out.

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

iOS has no public HID device role, so `IosHogpTransport` builds the HID GATT service by hand,
with the same layout as Android. What has been seen on an iPhone so far:

- Apple has reportedly reserved the HID service for the system. With full 128-bit UUIDs, iOS
  published it with the gamepad descriptor, and Linux read it and created a gamepad device.
- `CBMutableDescriptor` is documented to accept only two descriptor types. The Report Reference
  descriptor (`0x2908`) is neither, but creating it did not throw and the service was published.
- CoreBluetooth advertises only a name and service UUIDs, so gamepad appearance (`0x03C4`) cannot
  be advertised. Linux and macOS paired anyway; Windows is untested.
- iOS does not reveal the host name, so the status shows a short identifier instead.

Still to check on an iPhone: every control one at a time, and latency. If iOS refuses the
service, the status pill shows the error.

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
