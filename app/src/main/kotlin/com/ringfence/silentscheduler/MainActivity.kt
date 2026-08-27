package com.ringfence.silentscheduler

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ringfence.silentscheduler.core.theme.RingfenceTheme
import com.ringfence.silentscheduler.onboarding.DndAccessScreen
import com.ringfence.silentscheduler.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RingfenceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RingfenceRoot()
                }
            }
        }
    }
}

/**
 * Step 2 of the build order: DND permission explainer + request flow only. The
 * "granted"/"declined" states below are temporary placeholders standing in for the
 * Home Dashboard (step 7), just enough to verify the permission is detected correctly
 * on-device.
 */
@Composable
private fun RingfenceRoot() {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val isDndAccessGranted by viewModel.isDndAccessGranted.collectAsState()
    var userDeclined by remember { mutableStateOf(false) }

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

    when {
        isDndAccessGranted -> PlaceholderScreen(stringRes = R.string.dnd_access_granted_placeholder)
        userDeclined -> PlaceholderScreen(stringRes = R.string.dnd_access_declined_placeholder)
        else -> DndAccessScreen(
            onAllowClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            },
            onNotNowClick = { userDeclined = true }
        )
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
