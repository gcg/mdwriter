package dev.mdwriter.util

import kotlinx.coroutines.CoroutineDispatcher

/** Injected so tests can pass a TestDispatcher (01-architecture §5). */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
