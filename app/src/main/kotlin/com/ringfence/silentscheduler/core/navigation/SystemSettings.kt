package com.ringfence.silentscheduler.core.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "SystemSettings"

/**
 * Opens one of the system's special-access settings screens (Do Not Disturb access,
 * Alarms & reminders, app notifications). Not every device ships every one of these
 * screens — some low-RAM and heavily customised builds leave them out — and a plain
 * startActivity() there throws ActivityNotFoundException, crashing the app on the
 * very button that is supposed to fix a missing permission. Falls back to this app's
 * own details page in system Settings, from which the same switches are reachable.
 */
fun Context.openSystemSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for ${intent.action}, falling back to app details", e)
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app details settings screen either", e)
        }
    }
}
