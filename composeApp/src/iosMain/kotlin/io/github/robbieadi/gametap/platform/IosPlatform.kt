package io.github.robbieadi.gametap.platform

import platform.CoreHaptics.CHHapticEngine
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

class IosHaptics : Haptics {
    private val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)

    /** iPads and older iPhones have no Taptic Engine. */
    override val isAvailable: Boolean = CHHapticEngine.capabilitiesForHardware().supportsHaptics

    override fun tick() = generator.impactOccurred()
}

/** The app is landscape-only through Info.plist, so only the idle timer needs managing. */
class IosScreenControl : ScreenControl {
    override fun setGamepadMode(enabled: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}

/** CoreBluetooth prompts for access itself when the peripheral manager is created. */
class IosBluetoothAccess : BluetoothAccess {
    override val needsDiscoverability: Boolean = false

    override suspend fun prepare(): Boolean = true

    override fun requestDiscoverable() = Unit
}
