package com.example.gametap.platform

import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/** The current activity, for platform services that need one. Held weakly to avoid leaks. */
class ActivityProvider {
    private var reference = WeakReference<ComponentActivity>(null)

    val activity: ComponentActivity? get() = reference.get()

    fun set(activity: ComponentActivity) {
        reference = WeakReference(activity)
    }
}
