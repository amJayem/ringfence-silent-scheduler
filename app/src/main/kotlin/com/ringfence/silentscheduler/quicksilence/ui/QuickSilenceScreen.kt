package com.ringfence.silentscheduler.quicksilence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.time.formatCountdownClock
import kotlinx.coroutines.delay

/**
 * Build-order step 3: proves the silence/un-silence mechanism works end to end.
 * Not yet styled to the Claude Design source — that screen wasn't in the PDF pages
 * provided. Revisit against the real design once available (see DESIGN_NOTES.md).
 */
@Composable
fun QuickSilenceScreen(
    modifier: Modifier = Modifier,
    viewModel: QuickSilenceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var remainingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.isActive, state.endTimeMillis) {
        // Keeps recomputing (pinned at 0) instead of stopping once the countdown
        // reaches zero, so the display doesn't visually freeze during the gap before
        // the alarm actually fires and flips state.isActive to false.
        while (state.isActive) {
            remainingSeconds = ((state.endTimeMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.isActive) {
            Text(
                text = stringResource(R.string.quick_silence_active_label, formatCountdownClock(remainingSeconds)),
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { viewModel.cancelEarly() }) {
                Text(stringResource(R.string.quick_silence_unsilence_cta))
            }
        } else {
            Text(
                text = stringResource(R.string.quick_silence_idle_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.startSilence() },
                shape = RoundedCornerShape(percent = 50)
            ) {
                Text(stringResource(R.string.quick_silence_start_cta))
            }
        }
    }
}
