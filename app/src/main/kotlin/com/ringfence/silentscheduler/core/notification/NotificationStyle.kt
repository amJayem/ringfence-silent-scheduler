package com.ringfence.silentscheduler.core.notification

/** User-facing choice from Settings ("NOTIFICATION STYLE") for silence start/end alerts. */
enum class NotificationStyle {
    /** Heads-up alert with sound when silence starts and ends. */
    BANNER,

    /** Posted at low priority — no alert, visible in the notification shade/history only. */
    SILENT_LOG,

    /** Never posts a notification for silence start/end. */
    NONE
}
