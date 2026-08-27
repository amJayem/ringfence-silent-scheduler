package com.ringfence.silentscheduler.onboarding

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _isDndAccessGranted =
        MutableStateFlow(notificationManager.isNotificationPolicyAccessGranted)
    val isDndAccessGranted: StateFlow<Boolean> = _isDndAccessGranted.asStateFlow()

    /**
     * DND access can only be granted or revoked from system Settings, never while this
     * screen is on-screen and focused — so callers re-check by calling this from
     * onResume (e.g. after returning from the Settings screen), not via a live listener.
     */
    fun refreshPermissionState() {
        _isDndAccessGranted.value = notificationManager.isNotificationPolicyAccessGranted
    }
}
