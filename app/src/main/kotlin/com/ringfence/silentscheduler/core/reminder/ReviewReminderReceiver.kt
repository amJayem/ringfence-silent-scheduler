package com.ringfence.silentscheduler.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ringfence.silentscheduler.settings.domain.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired when a "review your schedules" reminder is due. Posts the nudge, then
 * re-arms the next one from the user's current interval setting — read fresh here
 * rather than captured at the previous arm, so a mid-interval setting change takes
 * effect starting with the very next occurrence.
 */
@AndroidEntryPoint
class ReviewReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var notifier: ReviewReminderNotifier

    @Inject
    lateinit var scheduler: ReviewReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = settingsRepository.observeSettings().first()
                // Not a bug to guard against going off without a user toggle somehow
                // surviving a cancel() — just cheap insurance against ever posting a
                // nudge for a reminder the user has since turned off.
                if (!settings.reviewReminderEnabled) return@launch
                notifier.postReminder()
                scheduler.scheduleNext(settings.reviewReminderAmount, settings.reviewReminderUnit)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
