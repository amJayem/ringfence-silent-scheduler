package com.ringfence.silentscheduler.onboarding

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringfence.silentscheduler.onboarding.domain.OnboardingRepository
import com.ringfence.silentscheduler.quicksilence.domain.QuickSilenceRepository
import com.ringfence.silentscheduler.schedule.domain.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long the onboarding tour's "try it now" Quick Silence demo runs before reverting. */
const val ONBOARDING_QUICK_SILENCE_DEMO_MINUTES = 1

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onboardingRepository: OnboardingRepository,
    private val scheduleRepository: ScheduleRepository,
    private val quickSilenceRepository: QuickSilenceRepository
) : ViewModel() {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _isDndAccessGranted =
        MutableStateFlow(notificationManager.isNotificationPolicyAccessGranted)
    val isDndAccessGranted: StateFlow<Boolean> = _isDndAccessGranted.asStateFlow()

    // Exact alarms are unrestricted (and this API doesn't exist) below Android 12 (S).
    private val _isExactAlarmGranted = MutableStateFlow(
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    )
    val isExactAlarmGranted: StateFlow<Boolean> = _isExactAlarmGranted.asStateFlow()

    val isTourCompleted: StateFlow<Boolean> = onboardingRepository.observeIsTourCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Below Android 13 (TIRAMISU), POST_NOTIFICATIONS doesn't exist as a runtime
    // permission — notifications just work once the app-level toggle is on — so treat
    // it as already "granted" pre-13 rather than gating the tour on a check that would
    // never pass.
    private val _isNotificationPermissionGranted = MutableStateFlow(
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    )
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()

    val hasRequestedNotificationPermissionOnce: StateFlow<Boolean> =
        onboardingRepository.observeHasRequestedNotificationPermission()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // "Tour completed" is a preference key that didn't exist before this feature —
        // it defaults to false for every install, including ones that already have
        // real schedules from using the app long before this tour existed. Without
        // this check, an existing user would suddenly see "Pick your first window" on
        // their next launch. Treat "already has a schedule" as proof the tour would be
        // redundant and silently mark it done instead of ever showing it to them.
        viewModelScope.launch {
            if (!onboardingRepository.observeIsTourCompleted().first() &&
                scheduleRepository.observeSchedules().first().isNotEmpty()
            ) {
                onboardingRepository.markTourCompleted()
            }
        }
    }

    /**
     * Both of these permissions can only be granted or revoked from system Settings,
     * never while this screen is on-screen and focused — so callers re-check by
     * calling this from onResume (e.g. after returning from the Settings screen), not
     * via a live listener.
     */
    fun refreshPermissionState() {
        _isDndAccessGranted.value = notificationManager.isNotificationPolicyAccessGranted
        _isExactAlarmGranted.value =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        _isNotificationPermissionGranted.value =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Records that the one-time system POST_NOTIFICATIONS prompt has now been shown. */
    fun markNotificationPermissionRequested() {
        viewModelScope.launch { onboardingRepository.markNotificationPermissionRequested() }
        refreshPermissionState()
    }

    /** Saves a real, ready-to-use schedule for each template the user picked. */
    fun createTemplateSchedules(templates: Set<ScheduleTemplate>) {
        viewModelScope.launch {
            templates.flatMap { it.toSchedules() }.forEach { scheduleRepository.addOrUpdateSchedule(it) }
        }
    }

    /** Starts a real, brief Quick Silence session so the tour demonstrates actual behavior. */
    fun startQuickSilenceDemo() {
        viewModelScope.launch { quickSilenceRepository.startSilence(ONBOARDING_QUICK_SILENCE_DEMO_MINUTES) }
    }

    fun completeTour() {
        viewModelScope.launch { onboardingRepository.markTourCompleted() }
    }
}
