package com.kon.myaacapp.ui.admin

sealed interface AdminDashboardEffect {

    data class ShowError(
        val message: String,
    ) : AdminDashboardEffect
}