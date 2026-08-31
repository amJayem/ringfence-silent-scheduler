package com.ringfence.silentscheduler.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * The explainer screen required before requesting ACCESS_NOTIFICATION_POLICY (DND
 * access) — CLAUDE.md requires this screen to exist before any permission-request
 * code, since it's a sensitive permission Google reviews closely.
 */
@Composable
fun DndAccessScreen(
    onAllowClick: () -> Unit,
    onNotNowClick: () -> Unit,
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
            text = stringResource(R.string.onboarding_headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        OnboardingBullet(
            title = stringResource(R.string.onboarding_bullet_dnd_title),
            body = stringResource(R.string.onboarding_bullet_dnd_body)
        )
        OnboardingBullet(
            title = stringResource(R.string.onboarding_bullet_schedule_title),
            body = stringResource(R.string.onboarding_bullet_schedule_body)
        )
        OnboardingBullet(
            title = stringResource(R.string.onboarding_bullet_revert_title),
            body = stringResource(R.string.onboarding_bullet_revert_body)
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.onboarding_allow_cta),
            onClick = onAllowClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onNotNowClick) {
            Text(stringResource(R.string.onboarding_not_now))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingBullet(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.padding(start = 6.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DndAccessScreenPreview() {
    RingfenceTheme {
        DndAccessScreen(onAllowClick = {}, onNotNowClick = {})
    }
}

@Preview(showBackground = true, uiMode = 0x20)
@Composable
private fun DndAccessScreenDarkPreview() {
    RingfenceTheme(useDarkTheme = true) {
        DndAccessScreen(onAllowClick = {}, onNotNowClick = {})
    }
}
