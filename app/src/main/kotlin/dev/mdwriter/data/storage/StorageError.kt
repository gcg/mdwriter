package dev.mdwriter.data.storage

import java.io.IOException

sealed interface StorageError {
    data object NotFound : StorageError

    data object PermissionLost : StorageError

    data object ReadOnly : StorageError

    data class TooLarge(
        val bytes: Long,
    ) : StorageError

    data object Encoding : StorageError

    data class ProviderFailure(
        val cause: Throwable,
    ) : StorageError
}

/** Stores signal every failure by throwing this. */
class StorageException(
    val error: StorageError,
    cause: Throwable? = null,
) : IOException(error.toString(), cause)

fun StorageError.userMessage(): String =
    when (this) {
        StorageError.NotFound -> "The file no longer exists"
        StorageError.PermissionLost -> "mdwriter lost access to this file"
        StorageError.ReadOnly -> "This file is read-only"
        is StorageError.TooLarge -> "Too large to open (%.1f MB)".format(bytes / (1024.0 * 1024.0))
        StorageError.Encoding -> "Not a text file"
        is StorageError.ProviderFailure -> "Couldn't save: ${cause.message ?: cause::class.simpleName}"
    }
