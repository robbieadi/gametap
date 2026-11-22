package io.github.robbieadi.gametap.hid

/**
 * HID report descriptor, published as the HOGP Report Map on Android and iOS.
 *
 * It is the Report Map of the Xbox Wireless Controller (model 1914, 045E:0B13) over Bluetooth Low
 * Energy, so that hosts treat Gametap as that controller: Windows gives it XInput through
 * xinputhid, and SDL, Linux and macOS read it with their Xbox drivers. Those drivers parse the
 * report by fixed offsets, so the layout must stay byte for byte the same. Taken from
 * ESP32-BLE-CompositeHID (MIT), XboxDescriptors.h, XboxOneS_1914_HIDDescriptor; the offsets match
 * SDL's HIDAPI_DriverXboxOneBluetooth_HandleStatePacket.
 */
object HidDescriptor {
    const val REPORT_ID: Int = 1

    /** Rumble from the host. Accepted and ignored. */
    const val OUTPUT_REPORT_ID: Int = 3

    /** Payload of the output report, without its ID: actuators, four magnitudes, duration, delay, loops. */
    const val OUTPUT_REPORT_SIZE: Int = 8

    private val values = intArrayOf(
        0x05, 0x01,                     // Usage Page (Generic Desktop)
        0x09, 0x05,                     // Usage (Game Pad)
        0xA1, 0x01,                     // Collection (Application)
        0x85, REPORT_ID,                //   Report ID (1)

        // Bytes 0-3: left stick X, Y, 16 bits each, centered at 0x8000
        0x09, 0x01,                     //   Usage (Pointer)
        0xA1, 0x00,                     //   Collection (Physical)
        0x09, 0x30,                     //     Usage (X)
        0x09, 0x31,                     //     Usage (Y)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x27, 0xFF, 0xFF, 0x00, 0x00,   //     Logical Maximum (65535)
        0x95, 0x02,                     //     Report Count (2)
        0x75, 0x10,                     //     Report Size (16)
        0x81, 0x02,                     //     Input (Data, Var, Abs)
        0xC0,                           //   End Collection

        // Bytes 4-7: right stick on Z, Rz
        0x09, 0x01,                     //   Usage (Pointer)
        0xA1, 0x00,                     //   Collection (Physical)
        0x09, 0x32,                     //     Usage (Z)
        0x09, 0x35,                     //     Usage (Rz)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x27, 0xFF, 0xFF, 0x00, 0x00,   //     Logical Maximum (65535)
        0x95, 0x02,                     //     Report Count (2)
        0x75, 0x10,                     //     Report Size (16)
        0x81, 0x02,                     //     Input (Data, Var, Abs)
        0xC0,                           //   End Collection

        // Bytes 8-9: left trigger, 10 bits + 6 bits of padding
        0x05, 0x02,                     //   Usage Page (Simulation Controls)
        0x09, 0xC5,                     //   Usage (Brake)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x26, 0xFF, 0x03,               //   Logical Maximum (1023)
        0x95, 0x01,                     //   Report Count (1)
        0x75, 0x0A,                     //   Report Size (10)
        0x81, 0x02,                     //   Input (Data, Var, Abs)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x00,                     //   Logical Maximum (0)
        0x75, 0x06,                     //   Report Size (6)
        0x95, 0x01,                     //   Report Count (1)
        0x81, 0x03,                     //   Input (Const, Var, Abs)

        // Bytes 10-11: right trigger, 10 bits + 6 bits of padding
        0x05, 0x02,                     //   Usage Page (Simulation Controls)
        0x09, 0xC4,                     //   Usage (Accelerator)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x26, 0xFF, 0x03,               //   Logical Maximum (1023)
        0x95, 0x01,                     //   Report Count (1)
        0x75, 0x0A,                     //   Report Size (10)
        0x81, 0x02,                     //   Input (Data, Var, Abs)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x00,                     //   Logical Maximum (0)
        0x75, 0x06,                     //   Report Size (6)
        0x95, 0x01,                     //   Report Count (1)
        0x81, 0x03,                     //   Input (Const, Var, Abs)

        // Byte 12: hat switch, 1-8 clockwise from north, 0 for none, + 4 bits of padding
        0x05, 0x01,                     //   Usage Page (Generic Desktop)
        0x09, 0x39,                     //   Usage (Hat Switch)
        0x15, 0x01,                     //   Logical Minimum (1)
        0x25, 0x08,                     //   Logical Maximum (8)
        0x35, 0x00,                     //   Physical Minimum (0)
        0x46, 0x3B, 0x01,               //   Physical Maximum (315)
        0x66, 0x14, 0x00,               //   Unit (Degrees)
        0x75, 0x04,                     //   Report Size (4)
        0x95, 0x01,                     //   Report Count (1)
        0x81, 0x42,                     //   Input (Data, Var, Abs, Null State)
        0x75, 0x04,                     //   Report Size (4)
        0x95, 0x01,                     //   Report Count (1)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x00,                     //   Logical Maximum (0)
        0x35, 0x00,                     //   Physical Minimum (0)
        0x45, 0x00,                     //   Physical Maximum (0)
        0x65, 0x00,                     //   Unit (None)
        0x81, 0x03,                     //   Input (Const, Var, Abs)

        // Bytes 13-14: 15 buttons + 1 bit of padding
        0x05, 0x09,                     //   Usage Page (Button)
        0x19, 0x01,                     //   Usage Minimum (1)
        0x29, 0x0F,                     //   Usage Maximum (15)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x01,                     //   Logical Maximum (1)
        0x75, 0x01,                     //   Report Size (1)
        0x95, 0x0F,                     //   Report Count (15)
        0x81, 0x02,                     //   Input (Data, Var, Abs)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x00,                     //   Logical Maximum (0)
        0x75, 0x01,                     //   Report Size (1)
        0x95, 0x01,                     //   Report Count (1)
        0x81, 0x03,                     //   Input (Const, Var, Abs)

        // Byte 15: Share (Consumer Record) + 7 bits of padding
        0x05, 0x0C,                     //   Usage Page (Consumer)
        0x0A, 0xB2, 0x00,               //   Usage (Record)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x01,                     //   Logical Maximum (1)
        0x95, 0x01,                     //   Report Count (1)
        0x75, 0x01,                     //   Report Size (1)
        0x81, 0x02,                     //   Input (Data, Var, Abs)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x00,                     //   Logical Maximum (0)
        0x75, 0x07,                     //   Report Size (7)
        0x95, 0x01,                     //   Report Count (1)
        0x81, 0x03,                     //   Input (Const, Var, Abs)

        // Output report 3: rumble
        0x05, 0x0F,                     //   Usage Page (Physical Interface Device)
        0x09, 0x21,                     //   Usage (Set Effect Report)
        0x85, OUTPUT_REPORT_ID,         //   Report ID (3)
        0xA1, 0x02,                     //   Collection (Logical)
        0x09, 0x97,                     //     Usage (DC Enable Actuators)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x25, 0x01,                     //     Logical Maximum (1)
        0x75, 0x04,                     //     Report Size (4)
        0x95, 0x01,                     //     Report Count (1)
        0x91, 0x02,                     //     Output (Data, Var, Abs)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x25, 0x00,                     //     Logical Maximum (0)
        0x75, 0x04,                     //     Report Size (4)
        0x95, 0x01,                     //     Report Count (1)
        0x91, 0x03,                     //     Output (Const, Var, Abs)
        0x09, 0x70,                     //     Usage (Magnitude)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x25, 0x64,                     //     Logical Maximum (100)
        0x75, 0x08,                     //     Report Size (8)
        0x95, 0x04,                     //     Report Count (4)
        0x91, 0x02,                     //     Output (Data, Var, Abs)
        0x09, 0x50,                     //     Usage (Duration)
        0x66, 0x01, 0x10,               //     Unit (Seconds)
        0x55, 0x0E,                     //     Unit Exponent (-2)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x26, 0xFF, 0x00,               //     Logical Maximum (255)
        0x75, 0x08,                     //     Report Size (8)
        0x95, 0x01,                     //     Report Count (1)
        0x91, 0x02,                     //     Output (Data, Var, Abs)
        0x09, 0xA7,                     //     Usage (Start Delay)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x26, 0xFF, 0x00,               //     Logical Maximum (255)
        0x75, 0x08,                     //     Report Size (8)
        0x95, 0x01,                     //     Report Count (1)
        0x91, 0x02,                     //     Output (Data, Var, Abs)
        0x65, 0x00,                     //     Unit (None)
        0x55, 0x00,                     //     Unit Exponent (0)
        0x09, 0x7C,                     //     Usage (Loop Count)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x26, 0xFF, 0x00,               //     Logical Maximum (255)
        0x75, 0x08,                     //     Report Size (8)
        0x95, 0x01,                     //     Report Count (1)
        0x91, 0x02,                     //     Output (Data, Var, Abs)
        0xC0,                           //   End Collection
        0xC0,                           // End Collection
    )

    /** Returns a fresh copy, so callers cannot alter the shared descriptor. */
    val bytes: ByteArray get() = ByteArray(values.size) { values[it].toByte() }
}
