package com.kon.myaacapp.ui.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CommunicationSessionController {

    private val _resetVersion =
        MutableStateFlow(0)

    val resetVersion: StateFlow<Int> =
        _resetVersion.asStateFlow()

    fun requestReset() {
        _resetVersion.update { version ->
            version + 1
        }
    }
}