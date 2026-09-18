package com.ringfence.silentscheduler

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ringfence.silentscheduler.core.navigation.MainAppShell
import com.ringfence.silentscheduler.core.theme.RingfenceTheme
import com.ringfence.silentscheduler.onboarding.DndAccessScreen
import com.ringfence.silentscheduler.onboarding.ExactAlarmAccessScreen
import com.ringfence.silentscheduler.onboarding.OnboardingViewModel
import com.ringfence.silentscheduler.onboarding.QuickSilenceTourScreen
import com.ringfence.silentscheduler.onboarding.WindowTemplatesScreen
import com.ringfence.silentscheduler.settings.ui.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val useDarkTheme = settings.themeOverride.resolveIsDark(isSystemInDarkTheme())

            // enableEdgeToEdge() only sets status/nav bar icon contrast once, at launch,
            // based on the system theme — it never reacts to the in-app Light/Dark/System
            // override above, so switching to Light while the system is Dark left status
            // bar icons white-on-white. Re-apply whenever the resolved theme changes.
            LaunchedEffect(useDarkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            RingfenceTheme(useDarkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RingfenceRoot()
                }
            }
        }
    }
}

/** The onboarding tour's two steps, shown once (see [OnboardingViewModel.isTourCompleted]). */
private enum class TourStep { TEMPLATES, QUICK_SILENCE }

/**
 * Steps 2+3+7 of the build order: DND permission explainer, exact-alarm explainer,
 * the one-time predefined-window + Quick Silence tour, then the full 3-tab app shell
 * (Dashboard/Quick Silence/Settings). The "declined" placeholder below is a dead end
 * for now — CLAUDE.md's revoked-permission banner belongs on the Dashboard itself, not
 * handled here. Exact-alarm access can be skipped ("Not now") without blocking the
 * app — Quick Silence and recurring schedules both fall back to an inexact alarm, just
 * less precisely timed.
 */
@Composable
private fun RingfenceRoot() {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val isDndAccessGranted by viewModel.isDndAccessGranted.collectAsState()
    val isExactAlarmGranted by viewModel.isExactAlarmGranted.collectAsState()
    val isTourCompleted by viewModel.isTourCompleted.collectAsState()
    val isNotificationPermissionGranted by viewModel.isNotificationPermissionGranted.collectAsState()
    val hasRequestedNotificationPermissionOnce by viewModel.hasRequestedNotificationPermissionOnce.collectAsState()
    var userDeclinedDnd by remember { mutableStateOf(false) }
    var userDeclinedExactAlarm by remember { mutableStateOf(false) }
    var tourStep by remember { mutableStateOf(TourStep.TEMPLATES) }
    // Guards against a second launch() while waiting for hasRequestedNotificationPermissionOnce
    // to come back from the DataStore write below — that flag only flips after the
    // launcher's callback runs, and recomposition could otherwise fire this twice.
    var hasFiredNotificationPromptThisSession by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.markNotificationPermissionRequested() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestViewModel = rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                latestViewModel.value.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = LocalContext.current

    // Fires the system POST_NOTIFICATIONS prompt exactly once, as soon as the DND/
    // exact-alarm decisions are out of the way — the app's default notification style
    // is BANNER, but nothing was ever proactively requesting this Android 13+ runtime
    // permission (it was only ever requested if the user happened to open Settings
    // and re-tap an already-selected notification style radio button), so real
    // schedule-start/end alerts silently never appeared for most users. Non-blocking:
    // the tour proceeds either way, same as a declined exact-alarm request.
    LaunchedEffect(isDndAccessGranted, isExactAlarmGranted, userDeclinedExactAlarm, isNotificationPermissionGranted, hasRequestedNotificationPermissionOnce) {
        val permissionsSettled = isDndAccessGranted && (isExactAlarmGranted || userDeclinedExactAlarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            permissionsSettled &&
            !isNotificationPermissionGranted &&
            !hasRequestedNotificationPermissionOnce &&
            !hasFiredNotificationPromptThisSession
        ) {
            hasFiredNotificationPromptThisSession = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when {
        userDeclinedDnd -> PlaceholderScreen(stringRes = R.string.dnd_access_declined_placeholder)
        !isDndAccessGranted -> DndAccessScreen(
            onAllowClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
            onNotNowClick = { userDeclinedDnd = true }
        )
        !isExactAlarmGranted && !userDeclinedExactAlarm -> ExactAlarmAccessScreen(
            onAllowClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onNotNowClick = { userDeclinedExactAlarm = true }
        )
        !isTourCompleted && tourStep == TourStep.TEMPLATES -> WindowTemplatesScreen(
            onContinue = { templates ->
                viewModel.createTemplateSchedules(templates)
                tourStep = TourStep.QUICK_SILENCE
            },
            onSkip = { viewModel.completeTour() }
        )
        !isTourCompleted -> QuickSilenceTourScreen(
            onTryIt = {
                viewModel.startQuickSilenceDemo()
                viewModel.completeTour()
            },
            onFinish = { viewModel.completeTour() }
        )
        else -> MainAppShell()
    }
}

@Composable
private fun PlaceholderScreen(stringRes: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = stringResource(stringRes), textAlign = TextAlign.Center)
    }
}
