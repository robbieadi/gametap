package com.example.gametap.input

import com.example.gametap.domain.GamepadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Latest input snapshot. Touch handlers write here directly; the UI only reads it to draw. */
class InputStateHolder {
    private val mutableState = MutableStateFlow(GamepadState())
    val state: StateFlow<GamepadState> = mutableState.asStateFlow()

    fun set(state: GamepadState) {
        mutableState.value = state
    }

    fun reset() = set(GamepadState())
}
