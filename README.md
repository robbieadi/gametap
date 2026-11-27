# Gametap

Turns an Android or iOS phone into a Bluetooth gamepad. The phone presents itself as an Xbox
Wireless Controller over Bluetooth Low Energy (HID over GATT), so the host needs no companion
app and no driver, and Windows gives it XInput. See [Xbox identity](#xbox-identity).

Built with Kotlin Multiplatform and Compose Multiplatform. The HID descriptor, report
encoding, input handling and UI are all shared; platform code only moves bytes over the radio.

## Status

| Part | State |
|------|-------|
| Shared code (HID, input, sender, connection logic) | Unit tested |
| Android app with a Linux host | Pairs, reconnects and sends buttons on a Redmi Pad SE and a Galaxy A54, see [Latency](#latency). Every control was tested one at a time on the Redmi Pad SE over classic Bluetooth, over Low Energy with a Windows host, and again over Low Energy with a Linux host after Gametap took the Xbox identity, see [Linux hosts](#linux-hosts) |
| Multi-touch | Unit tested in the tracker. On the Redmi Pad SE over Low Energy, up to five fingers at once (both sticks, D-pad, a trigger and buttons) each held their own control while others were pressed, moved and released |
| Leaving the app and coming back | The gamepad stays connected; tested for 60 s on the Redmi Pad SE with a Windows host, see [Using it](#using-it). Bluetooth off and on tested three times in a row. A foreground service keeps the app running; swiping it away from Recents leaves the gamepad connected, tried once for 60 s |
| Volume keys | Used on a Redmi Pad SE; about 150 ms slower than on-screen buttons, see [Volume keys](#volume-keys) |
| Gyro | Tilt math and controls unit tested. iOS reader checked against CoreMotion's gravity on an iPhone 13 mini; **tilting not yet tried on a device** on either platform |
| Windows hosts | Windows 11 with an Intel adapter. The Redmi Pad SE pairs as an Xbox Wireless Controller and gets Windows' XInput driver. Every control arrives correctly through XInput, DirectInput (`joy.cpl`) and Steam's SDL, which recognizes it without setup. Leaving Gametap keeps the connection. Windows reconnected by itself after **Stop** → **Start**, when Gametap still had Stop, and after its own Bluetooth goes off and on (each tried once), but **not after Gametap's process ends or the phone's Bluetooth restarts**, see [Windows hosts](#windows-hosts). The iPhone has not been tried since the change |
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

### iOS without a Mac

The **iOS build** GitHub Actions workflow builds an unsigned `Gametap.ipa` on a macOS runner,
on every push to `main` that touches the app and on demand from the Actions tab (**Run
workflow**). Download the `Gametap-ipa` artifact from the run, unzip it, then sign and install
the `.ipa` from Windows with [Sideloadly](https://sideloadly.io) or AltStore and your Apple ID,
with the iPhone connected by USB. On iOS 16 and later, also turn on **Settings → Privacy &
Security → Developer Mode**.

With a free Apple ID the install expires after 7 days and at most 3 sideloaded apps can be
installed at once. With the paid Apple Developer Program it lasts a year.

## Using it

1. Open Gametap and allow the Bluetooth prompts. It opens straight on the gamepad, always in
   landscape, and the phone starts advertising. A banner under the status says how to pair, or
   what is wrong, with **Try again** after a refused permission or an error.
2. On the host, open Bluetooth settings and pair with the phone. Both sides show a 6-digit code;
   confirm it on both. On Linux this needs a pairing agent, see [Linux hosts](#linux-hosts).
3. The banner goes away when the host connects.

After the first pairing, a Linux host reconnects by itself, usually within a second of opening
Gametap. Windows does too while Gametap keeps running, see [Windows hosts](#windows-hosts).

On Android, the gamepad stays connected while Gametap is in the background, for example after
Home or locking the phone. Every control is released, reports slow to one every 500 ms, and the
link asks the host for a slower, cheaper connection interval; coming back restores the fast one,
and the gamepad works at once. If the host disconnects meanwhile, Gametap advertises again only
when it is back on screen.

There is no Stop. Once started, the gamepad stays on and the host stays connected; leave with
Home or Back, and away from the gamepad screen every control is released, so the host gets no
input. Disconnecting instead made Windows reconnect every 10 to
30 s, see [Windows hosts](#windows-hosts). The gamepad ends when the phone goes out of range, or
with Gametap's process.

While the gamepad is on, Android shows a "Gamepad is on" notification with the connection; it is
a foreground service, which keeps Android from ending Gametap in the background. On the Redmi Pad
SE, Gametap then ran at OOM priority 50 in the background instead of 900, the first to be killed.
Gametap asks for permission to post it; refusing hides the notification but changes nothing else.
Swiping Gametap away from Recents closes only the screen: the gamepad stays on and connected,
and the Redmi Pad SE kept the process through that swipe once the service ran (the host stayed
connected for the minute it was watched). A crash disconnects first, for up to 2 s; that path is
untested.

If Bluetooth is turned off, the status says so, and the gamepad starts again when Bluetooth comes
back on and Gametap is on screen.

On iOS the gamepad keeps running in the background. If you close Gametap from the app switcher
or restart the phone, a Linux host may stop reconnecting, see [Linux hosts](#linux-hosts).

Tap the connection status at the top of the gamepad to open the menu, where you switch layouts
or edit the current one. To leave, use Home or Back.

### Sticks and haptics

Each stick has its own settings, saved with the layout. Select a stick in the layout editor to set
its **deadzone** (0 to 40% of its travel sends nothing), its **sensitivity curve** (linear, or up
to 3 for finer control near the center) and whether it is **floating**, centered where the thumb
lands instead of at its drawn position. Tap **Haptics** in the editor to turn the vibration on
press on or off for the layout; the button is hidden on devices that cannot vibrate.

### Gyro

Gyro drives outputs by tilting the phone. In the layout editor, **+ Add** › **Gyro button** adds a
gyro button that aims with the right stick while held, and selecting a gyro button shows its
settings. Each stick takes the horizontal tilt, the vertical
tilt or both; each trigger takes one direction of one axis, such as "pulls when tilted right". One
tilt can drive several outputs. Horizontal is either steering (turning the phone like a wheel) or
turning (around its vertical axis).

The gyro button's action sets the mode, one per layout:

- **Gyro while held**: gyro works while the button is held.
- **Gyro on/off**: a press turns gyro on and the next turns it off. It starts off with each layout.
- **Gyro always on**: gyro is on whenever the gamepad is open, and a press recenters it.

Tilt is measured from the angle the phone has when gyro turns on, or when it is recentered. A
layout can have several gyro buttons, and volume keys can take gyro actions too, but they must all
use the same mode and they share one set of settings. A layout without a gyro button or key has
no gyro.

Gyro adds to the touch stick, after the stick's response curve, and a gyro trigger and a touch
trigger take the higher value.

The sensor is Android's game rotation vector (gyroscope and accelerometer, no compass), read at
200 Hz only while the gamepad is on screen and the layout has a gyro button and gyro bindings. Devices without a
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

To check the result on a host, use <https://hardwaretester.com/gamepad>, Steam controller
settings, or `evtest` / `jstest` on Linux.

## Known limitations

- **Android phones that cannot advertise over Bluetooth Low Energy.** Mostly older or budget
  devices. The app then says the phone cannot act as a controller.
- **The background connection lasts as long as Android keeps Gametap running.** The foreground
  service makes Android much less likely to end it (see [Using it](#using-it)), but systems such
  as HyperOS may still stop apps in the background, and the host then loses the gamepad. Linux
  reconnects once Gametap is open again; Windows has to pair again, see
  [Windows hosts](#windows-hosts). Hours in the background have not been tested.
- **Descriptor changes.** Hosts cache the descriptor. Remove the device from the host's Bluetooth
  settings, forget the host on the phone, and pair again after any change.
- **No rumble, no Share button.** Hosts can send rumble, which is accepted and ignored. The Share
  button is always released.
- **The host may show the phone's own name and ID.** See [Linux hosts](#linux-hosts).

### Xbox identity

Gametap reports Microsoft's IDs for the Xbox Wireless Controller, model 1914 (`045E:0B13`,
version `0x0509`), in its PnP ID, and that controller's Report Map and report layout. There is no
other way to XInput over Bluetooth: Windows' `xinputhid.inf` binds the XInput driver to a fixed
list of Microsoft IDs, and the Bluetooth Low Energy entries are all Xbox controllers. Hosts then
use their Xbox drivers: XInput on Windows, SDL's Xbox driver in Steam, and on Linux and macOS
whatever they use for that controller. Commercial controllers do the same; a Flydigi Vader 5 Pro in
Bluetooth mode reports `045E:02E0` (Xbox One S).

- **The report must stay byte for byte the same.** Hosts' Xbox drivers read fields at fixed
  offsets. The descriptor comes from ESP32-BLE-CompositeHID (MIT), and the offsets match SDL's
  Xbox Bluetooth parser; `HidDescriptorTest` and `HidReportEncoderTest` pin both.
- **The IDs are Microsoft's.** USB-IF rules reserve a vendor ID to the company it is assigned to,
  and app stores may treat presenting as another company's hardware as misrepresentation. Avoid
  Xbox names and logos in the app and its store listing.
- **Checked on Windows and Linux with Android; not yet on macOS or with an iPhone.** Linux does
  not always use the Xbox IDs: on the first connection after pairing it has none, and later it
  may use the phone's own, see [Linux hosts](#linux-hosts).

### Linux hosts

These were found with BlueZ 5.87 and 5.88. Other desktops and BlueZ versions may differ.

- **It is an Xbox controller to Linux when BlueZ uses the Xbox IDs**, which it does not always,
  see the next points. Checked with BlueZ 5.88 and a Redmi Pad SE. With the Xbox IDs, the kernel binds its `microsoft` driver, and Steam's SDL names it "Xbox Series X
  Controller" with its built-in mapping and Steam's Xbox configuration. Every control arrives in
  evdev, checked one at a time: the 11 buttons (`BTN_SOUTH` to `BTN_THUMBR`), the D-pad as
  `ABS_HAT0X`/`ABS_HAT0Y`, the sticks as `ABS_X`/`ABS_Y` and `ABS_Z`/`ABS_RZ` (about 660 to 64840,
  both directions), and the triggers as analog `ABS_BRAKE` (left) and `ABS_GAS` (right).
- **Linux reconnects by itself, also after Gametap's process ends.** After the PC disconnected
  it, the gamepad was back in 0.55 to 0.65 s (three times); after a force stop and opening
  Gametap again, in 3.7 s. The gamepad kept its Xbox IDs each time; the phone had no classic
  Bluetooth connection to the PC during these tests. A press reached evdev after the restart and
  after 30 s in the background.
- **The first connection after pairing has no IDs.** The gamepad is `0000:0000`, the kernel uses
  its generic HID driver, and Steam names it after the phone ("Redmi Pad SE") with a generic
  mapping. Every control still works. BlueZ 5.88 (`profiles/input/hog.c`) takes the IDs it
  already has for the device when the connection starts, and its `deviceinfo` plugin reads
  Gametap's PnP ID at the same time, too late for that gamepad. Moving Device Information before
  the HID service did not change this. From the next connection on, BlueZ uses the IDs it then
  has on record, see the next point.
- **Later connections use the IDs BlueZ saw last.** BlueZ keeps one vendor and product ID per
  device, written by Gametap's PnP ID or by the phone's own classic Bluetooth record (`038F:1200`
  on the Redmi Pad SE), whichever it read last. It reads the phone's record whenever the phone
  connects over classic Bluetooth, for example for audio (see "Phones send their audio to the
  PC" below, which also turns that off). To get the Xbox IDs back, disconnect
  the gamepad once while the phone has no classic connection to the PC.
- **No new pairing was needed for the Xbox identity.** A PC paired before the change read the
  new Report Map by itself.
- **Gametap sends nothing until pairing is done.** Two bugs, both found with BlueZ 5.88 and fixed:
  - Gametap opened its connection-priority client (see [Latency](#latency)) as soon as the PC
    connected. During pairing, Android's first read through it got no answer from BlueZ, and 30 s
    later Android dropped the link. Android then kept accepting connections from the PC without
    passing them to Gametap, so the gamepad did not come back until Bluetooth was turned off and
    on on the phone.
  - When the phone was removed only on the PC and paired again, the phone still had its old
    bond, and Gametap restored that host's saved notification setting at once and sent input
    reports 0.08 s after the PC connected, mid-pairing. An HCI capture showed BlueZ then starting
    its GATT client unencrypted, 0.14 s after connecting; its HID reads were refused with
    Insufficient Encryption, it tried each twice and never created the gamepad. In a clean
    pairing BlueZ sent nothing until 0.09 s after encryption.

  Now a bonded host's setting is restored, and the client opened, 1 s after it connects, unless
  it starts pairing first (0.13 to 0.23 s after connecting, four times); a pairing drops the
  saved setting, since the PC turns notifications on again itself. Otherwise the client opens
  when the PC turns on notifications or pairing completes. A clean pairing and pairing again
  after removing the phone only on the PC both then created the gamepad and stayed connected.
  Windows, which does not turn notifications on again when it reconnects, now gets input 1 s
  after connecting; not yet retested on Windows.

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
  Gametap and run `bluetoothctl scan le`: with BlueZ 5.88, the scan alone connected the paired
  gamepad within about a second. Or call, with the phone's device path:

  ```sh
  busctl --system call org.bluez /org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF org.bluez.Bearer.LE1 Connect
  ```

  The path may not match the phone's address: BlueZ 5.88 kept the Redmi Pad SE (address
  `2C:0B:97:23:41:95`) under `dev_5F_90_10_A6_51_C4`, a private address it saw earlier, and the
  address-based path did not exist. `busctl --system tree org.bluez` lists the paths, and the
  `Address` property of each says which one is the phone. Without a scan running, this call timed
  out after 25 s. Once the gamepad has connected, BlueZ reconnects it by itself. To make plain
  connects use Low Energy, set `Experimental = true` in `/etc/bluetooth/main.conf`, restart
  Bluetooth, and set
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
- **The gamepad may show the phone's name and ID.** BlueZ names the gamepad after the phone, and
  its ID can be none, the phone's own (for example Apple 004C:7504) or Gametap's, see the first
  points. The ID can change between connections, so Steam may treat it as a new controller. Apps
  cannot change this; the fix would be in BlueZ, for example by creating the gamepad again when
  the PnP ID arrives, or by preferring the Low Energy PnP ID for a Low Energy gamepad.

### Windows hosts

These were found on Windows 11 with an Intel adapter and a Redmi Pad SE, reading the gamepad
through XInput, Windows.Gaming.Input, winmm (what `joy.cpl` shows) and the SDL 3.5 that Steam
ships.

- **It is an Xbox controller to Windows.** Add the phone in Bluetooth settings and confirm the
  code on both sides. Windows loads its XInput driver for it ("Bluetooth LE XINPUT compatible
  input device", `xinputhid`), and Windows.Gaming.Input counts it as an Xbox `Gamepad`.
- **XInput reads every control.** Buttons, D-pad with diagonals, analog triggers (0 to 255) and
  both sticks to full travel, checked one at a time. As with any Xbox controller, XInput does not
  report the Home (Xbox) button; Windows keeps it for the Game Bar.
- **Steam recognizes it without setup.** SDL names it "Xbox Series X Controller" with its built-in
  mapping, and reads every control, including Home and analog triggers.
- **DirectInput works in the foreground.** `joy.cpl` shows every control, with the layout every
  Xbox controller has there: five axes, both triggers sharing one (Z) in opposite directions, so
  DirectInput games cannot tell both pulled from neither. A background program reading winmm got
  no input while XInput did, so test with the window in front.
- **Leaving Gametap keeps the gamepad.** It stayed connected and present in Windows through
  60 s in the background, and an A press right after coming back reached XInput.
- **Windows reconnects by itself while Gametap's services stay up.** After **Stop** → **Start**
  (when Gametap still had Stop),
  Windows connected 0.2 s after advertising began; after the PC's Bluetooth went off and on, it
  connected 7.5 s after the radio returned. An A press reached XInput after each (each case tried
  once). Two things made this work:
  - **Windows does not turn notifications on again when it reconnects**, as the Bluetooth spec
    allows for a bonded device. Gametap used to wait for that, so Windows did reconnect, but
    Gametap kept showing "Waiting for a host" and sent nothing. Gametap now saves the setting for
    each bonded host and restores it 1 s after that host connects, unless it pairs again (see
    [Linux hosts](#linux-hosts)); unpairing on the phone, or pairing again, forgets it. With
    nothing saved for a bonded host it counts as on: after Gametap was reinstalled while Windows
    stayed paired, Windows showed a connected controller that got no input until this, and then
    an A press got through. The 1 s delay held up with Windows: reconnects after its radio came
    back, after Back and reopening, and a fresh pairing all ended with input reaching XInput.
- **A congested link could leave the gamepad silent for good.** Gametap sends one report, then
  waits for Android to confirm it before the next. Android's GATT service holds those
  confirmations back while a server is congested and releases them when congestion clears. Three
  times, the link to Windows became congested about 1 s before Windows dropped it, the clear never
  came, and no confirmation reached Gametap again, across every later reconnect: it showed
  connected but sent nothing. Gametap now stops waiting after 3 confirmations are missing for
  100 ms each, and waits again if one arrives. In that mode it sent about 57 reports a second with
  none refused, and in 30 rounds of Back and reopening (Windows dropped the link in 4), 28 got
  every A press through; the other 2 lost presses while Windows was dropping the link.
- **The phone's 2.4 GHz Wi-Fi made the link drop.** The drops above were all supervision
  timeouts: neither side heard the other for 5 s. With the Redmi Pad SE on a 2.4 GHz network
  (802.11n, −64 dBm), each 10 minutes untouched with Gametap in front had 6, 9 and 4 of them,
  also with Gametap sending only every 500 ms and with the tablet moved closer. With the tablet's
  Wi-Fi off, and with it on a 5 GHz iPhone hotspot (802.11ac, 5745 MHz), there were none in 10
  minutes each. The PC stayed on 2.4 GHz Wi-Fi throughout. Phones share one radio between Wi-Fi
  and Bluetooth, and an app cannot change how it is shared, so Gametap says so instead: after a
  link drops while the phone is on 2.4 GHz Wi-Fi, a banner suggests a 5 GHz network or Wi-Fi off,
  until dismissed or until the phone is off 2.4 GHz at the next connection.
  - **Stop kept the services** (Gametap has no Stop any more, see the next points). It dropped the
    link and stopped advertising, and the link went down 1.1 s after **Disconnect**. Closing Gametap's GATT server removes the services, Windows then
    deletes the gamepad device, and with it gone Windows does not reconnect, even while it sees
    the phone advertising.
- **Windows does not reconnect after Gametap's process ends while connected, or after the
  phone's Bluetooth restarts.** Both close the GATT server under a connected host, so Windows
  deletes the gamepad device as above. In 45 to 90 s afterwards, no connection attempt reached
  the phone, also with a scan running on the PC. Windows Settings has no Connect button for a
  paired Low Energy device; a program can connect it, see below. The foreground service makes the
  process ending rare. Ending while no host is connected can do the same: once, **Stop** then
  Force stop then **Start gamepad** (now opening Gametap) reconnected in 0.56 s, but when Windows connected during the
  gap (see the next point), it found no HID service, removed the gamepad and stopped connecting.
  Bringing it back with the program below then gave a generic HID game controller, not an XInput
  one. A user's force stop also needed pairing again.
- **Gametap says when Windows has probably forgotten it.** It saves the host while a link is up
  and clears it when the link drops. If Gametap starts with a host still saved, or Bluetooth goes
  off under one, the banner says the PC has probably forgotten the controller and to remove the
  phone in Windows and add it again; this showed after the process was killed under Windows, and
  Windows had indeed removed the gamepad. If the last paired host has not reconnected after 60 s
  of advertising, a milder banner says it may have; Windows once took 38 s to reconnect after its
  radio came back.
- **Microsoft Swift Pair did not work on the test PC, so Gametap does not use it.** It would
  make adding the phone again one click. Twice, with Swift Pair notifications on and the phone
  removed in Windows, no notification appeared, also with the tablet next to the PC. The second
  try followed Microsoft's guideline as far as an app can: manufacturer data for company 0x0006,
  `03 00 80` then the phone's name, in the advertisement itself (Windows does not scan actively),
  connectable, general discoverable, every 100 ms, from a fresh address. A passive scan on the PC
  received exactly that, at −60 to −63 dBm. Microsoft says Swift Pair also needs support in the
  PC's Bluetooth radio; the PC was on a Windows Insider build (26300).
- **Windows kept reconnecting while the gamepad was stopped**, which is why Gametap has no Stop
  any more. It connected every 10 to 30 s although the phone was not advertising for Gametap,
  probably through the phone's other connectable advertising. Gametap dropped each link, but the
  link stayed up 2.3 to 2.5 s (5 s before Gametap waited 300 ms between Android's connect and
  cancel calls), and Windows loads the gamepad 0.7 s after the link comes up, so Steam showed it
  connect and disconnect each time. Removing the services while stopped would end this, but makes
  Windows delete the gamepad as above.
- **A program can reconnect it without pairing again.** Asking Windows for the phone's GATT
  services (`BluetoothLEDevice.GetGattServicesAsync`) connects it, and the gamepad comes back.
  Windows connects to the last address it saw for the phone, so this needs the phone to keep its
  private address: Gametap keeps one advertising set for as long as Bluetooth stays on, and the
  Redmi Pad SE then kept its address for at least 22 minutes across 11 **Stop** → **Start**
  cycles, with 10 of 11 connections succeeding without a scan. When Gametap still started a new
  advertisement each time, 14 attempts in a row failed with "Unreachable" until a 3 s scan showed
  Windows the new address. Android rotates the address every 7 to 15 minutes by default, and
  after Bluetooth off and on it is new. After such a connection, Windows turned notifications on
  itself 7 s later, and Gametap saved the setting from then on.
- **Some connections fail at the radio.** In one attempt the link came up, the PC's controller
  never answered the phone's feature request, and the link dropped after the 40 s link-layer
  timeout.

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
| Redmi Pad SE | Low Energy, Windows host | 30 | 52 to 71 ms | Mostly within ±15 ms; one press per run 36 to 55 ms late. Measured differently, see below |

**Windows.** The Windows run injected presses through a virtual touchscreen (`uinput`) on a fixed
700 ms schedule and compared each report's arrival on the PC with that schedule, so the spread
also includes any jitter in the injection. Every press arrived.

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
with it the p90 was 18 ms above the fastest press (90 presses), without it 28 ms (45 presses). Away
from the gamepad screen it repeats every 500 ms instead, see [Using it](#using-it). Both intervals count from when a send starts. Android allows one
notification in flight, so a newer report replaces one still waiting to go out.

## Axis mapping

Input report 1 is the Xbox Wireless Controller's, 16 bytes after the report ID, little-endian. Do
not change it: hosts' Xbox drivers read it by offset, see [Xbox identity](#xbox-identity).

| Bytes | Field | HID usage | Values |
|-------|-------|-----------|--------|
| 0-3 | Left stick X, Y | X, Y | 0 to 65535, center 32768 |
| 4-7 | Right stick X, Y | Z, Rz | 0 to 65535, center 32768 |
| 8-9 | Left trigger | Brake | 0 to 1023 |
| 10-11 | Right trigger | Accelerator | 0 to 1023 |
| 12 | D-pad | Hat switch | 1 to 8 clockwise from up, 0 released |
| 13-14 | Buttons | Buttons 1 to 15 | bits below |
| 15 | Share | Consumer Record | always 0 |

| Bit | 0 | 1 | 3 | 4 | 6 | 7 | 10 | 11 | 12 | 13 | 14 |
|-----|---|---|---|---|---|---|----|----|----|----|----|
| Button | A | B | X | Y | LB | RB | Select (View) | Start (Menu) | Home (Xbox) | L3 | R3 |

Output report 3 carries rumble (four magnitudes, 0 to 100). Gametap acknowledges and ignores it.

## iOS risks

iOS has no public HID device role, so `IosHogpTransport` builds the HID GATT service by hand,
with the same layout as Android. What has been seen on an iPhone so far:

- Apple has reportedly reserved the HID service for the system. With full 128-bit UUIDs, iOS
  published it with the gamepad descriptor, and Linux read it and created a gamepad device.
- `CBMutableDescriptor` is documented to accept only two descriptor types. The Report Reference
  descriptor (`0x2908`) is neither, but creating it did not throw and the service was published.
- CoreBluetooth advertises only a name and service UUIDs, so gamepad appearance (`0x03C4`) cannot
  be advertised. Linux, macOS and Windows paired anyway, and Windows created the gamepad device;
  its controls were not checked on Windows. All of this was before the Xbox identity, which adds
  a second Report characteristic (the output report); iOS has not run it yet.
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
