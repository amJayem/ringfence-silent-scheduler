package com.ringfence.silentscheduler.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.theme.RingfenceTheme
import com.ringfence.silentscheduler.core.ui.PrimaryButton

/**
 * The onboarding tour's second and final step: rather than just describing Quick
 * Silence, "Try it" starts a real, brief session and hands off straight to the
 * Dashboard — which already renders a live active-session countdown, so the demo
 * needs no bespoke UI of its own beyond this explainer.
 */
@Composable
fun QuickSilenceTourScreen(
    onTryIt: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_do_not_disturb),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_quick_silence_headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_quick_silence_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.onboarding_quick_silence_try_cta),
            onClick = onTryIt,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onFinish) {
            Text(stringResource(R.string.onboarding_quick_silence_finish))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickSilenceTourScreenPreview() {
    RingfenceTheme {
        QuickSilenceTourScreen(onTryIt = {}, onFinish = {})
    }
}

@Preview(showBackground = true, uiMode = 0x20)
@Composable
private fun QuickSilenceTourScreenDarkPreview() {
    RingfenceTheme(useDarkTheme = true) {
        QuickSilenceTourScreen(onTryIt = {}, onFinish = {})
    }
}
