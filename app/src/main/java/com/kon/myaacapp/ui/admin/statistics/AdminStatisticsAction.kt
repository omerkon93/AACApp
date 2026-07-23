package com.kon.myaacapp.ui.admin.statistics

import com.kon.myaacapp.domain.model.AnalyticsTimeFilter

sealed interface AdminStatisticsAction {

    data class SelectFilter(
        val filter: AnalyticsTimeFilter,
    ) : AdminStatisticsAction
}
