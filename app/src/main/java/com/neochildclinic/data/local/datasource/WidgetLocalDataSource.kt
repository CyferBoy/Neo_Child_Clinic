package com.neochildclinic.data.local.datasource

import com.neochildclinic.data.local.dao.WidgetDueDao
import com.neochildclinic.data.local.entity.WidgetDueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class WidgetLocalDataSource @Inject constructor(
    private val widgetDueDao: WidgetDueDao
) {
    open val dueItemsFlow: Flow<List<WidgetDueEntity>>
        get() = widgetDueDao.getDueItems()

    open suspend fun getDueItemsFirst(): List<WidgetDueEntity> = widgetDueDao.getDueItems().first()
}
