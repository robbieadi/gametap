package io.github.robbieadi.gametap.hid

/** HID report descriptor shared by Classic Bluetooth HID (Android) and HOGP (iOS). */
object HidDescriptor {
    const val REPORT_ID: Int = 1

    private val values = intArrayOf(
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x05,             // Usage (Gamepad)
        0xA1, 0x01,             // Collection (Application)
        0x85, REPORT_ID,        //   Report ID

        // Bytes 0-1: 16 buttons
        0x05, 0x09,             //   Usage Page (Button)
        0x19, 0x01,             //   Usage Minimum (1)
        0x29, 0x10,             //   Usage Maximum (16)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95, 0x10,             //   Report Count (16)
        0x81, 0x02,             //   Input (Data, Var, Abs)

        // Byte 2: hat switch + 4 bits of padding
        0x05, 0x01,             //   Usage Page (Generic Desktop)
        0x09, 0x39,             //   Usage (Hat Switch)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x07,             //   Logical Maximum (7)
        0x35, 0x00,             //   Physical Minimum (0)
        0x46, 0x3B, 0x01,       //   Physical Maximum (315)
        0x65, 0x14,             //   Unit (Degrees)
        0x75, 0x04,             //   Report Size (4)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x42,             //   Input (Data, Var, Abs, Null State)
        0x65, 0x00,             //   Unit (None)
        0x45, 0x00,             //   Physical Maximum (0: same as logical)
        0x75, 0x04,             //   Report Size (4)
        0x95, 0x01,             //   Report Count (1)
        0x81, 0x03,             //   Input (Const, Var, Abs)

        // Bytes 3-6: sticks
        0x09, 0x30,             //   Usage (X)
        0x09, 0x31,             //   Usage (Y)
        0x09, 0x32,             //   Usage (Z)
        0x09, 0x35,             //   Usage (Rz)
        0x15, 0x00,             //   Logical Minimum (0)
        0x26, 0xFF, 0x00,       //   Logical Maximum (255)
        0x75, 0x08,             //   Report Size (8)
        0x95, 0x04,             //   Report Count (4)
        0x81, 0x02,             //   Input (Data, Var, Abs)

        // Bytes 7-8: triggers. These must not be Rx/Ry: SDL (and so Steam) on Linux reads Rx/Ry
        // as the right stick whenever they exist, and then reads Z/Rz as the triggers.
        0x05, 0x02,             //   Usage Page (Simulation Controls)
        0x09, 0xC5,             //   Usage (Brake): left trigger
        0x09, 0xC4,             //   Usage (Accelerator): right trigger
        0x95, 0x02,             //   Report Count (2)
        0x81, 0x02,             //   Input (Data, Var, Abs)

        0xC0,                   // End Collection
    )

    /** Returns a fresh copy, so callers cannot alter the shared descriptor. */
    val bytes: ByteArray get() = ByteArray(values.size) { values[it].toByte() }
}
