package com.odin.desktop.dashboard

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Each collector starts a new visit; slow samples finish before another is requested. */
internal fun dashboardRefreshes(): Flow<Unit> = flow {
    emit(Unit)
    repeat(3) {
        delay(6_000L)
        emit(Unit)
    }
    while (currentCoroutineContext().isActive) {
        delay(30_000L)
        emit(Unit)
    }
}
