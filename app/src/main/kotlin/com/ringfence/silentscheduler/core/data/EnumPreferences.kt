package com.ringfence.silentscheduler.core.data

/** Preferences DataStore stores enums by name; an unknown or missing value falls back to [default]. */
inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { stored -> runCatching { enumValueOf<T>(stored) }.getOrNull() } ?: default
