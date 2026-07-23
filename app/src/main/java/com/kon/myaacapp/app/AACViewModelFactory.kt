package com.kon.myaacapp.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kon.myaacapp.AACViewModel

class AACViewModelFactory(
    private val application: Application,
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T {
        if (
            modelClass.isAssignableFrom(
                AACViewModel::class.java
            )
        ) {
            @Suppress("UNCHECKED_CAST")
            return AACViewModel(
                application = application,
                appContainer = appContainer,
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}