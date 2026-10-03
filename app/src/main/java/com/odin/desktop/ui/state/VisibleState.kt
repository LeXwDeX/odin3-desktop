package com.odin.desktop.ui.state

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Each mounted UI region observes its state only while the launcher is visible. */
@Composable
@SuppressLint("StateFlowValueCalledInComposition") // Only the initial snapshot; the effect observes all visible updates.
internal fun <T> StateFlow<T>.collectAsVisibleState(): State<T> {
    val source = this
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember(source, lifecycle) { mutableStateOf(source.value) }
    LaunchedEffect(source, lifecycle) {
        source.collectWhileVisible(lifecycle) { state.value = it }
    }
    return state
}

internal suspend fun <T> StateFlow<T>.collectWhileVisible(lifecycle: Lifecycle, onValue: (T) -> Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        collect { onValue(it) }
    }
}
