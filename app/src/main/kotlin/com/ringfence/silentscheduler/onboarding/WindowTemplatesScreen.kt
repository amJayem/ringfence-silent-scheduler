package com.ringfence.silentscheduler.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.theme.RingfenceTheme
import com.ringfence.silentscheduler.core.ui.PrimaryButton

/**
 * The onboarding tour's first step: lets a new user create one or more starter
 * schedules from a short list of common templates instead of facing a blank Dashboard.
 * Selection is optional either way — "Continue" saves whatever's checked (even none)
 * and moves to the Quick Silence demo step; "Skip" bypasses the whole tour.
 */
@Composable
fun WindowTemplatesScreen(
    onContinue: (Set<ScheduleTemplate>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(emptySet<ScheduleTemplate>()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            text = stringResource(R.string.onboarding_templates_headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_templates_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        ScheduleTemplate.entries.forEach { template ->
            TemplateCard(
                template = template,
                checked = template in selected,
                onToggle = {
                    selected = if (template in selected) selected - template else selected + template
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.onboarding_templates_continue_cta),
            onClick = { onContinue(selected) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_templates_skip))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TemplateCard(template: ScheduleTemplate, checked: Boolean, onToggle: () -> Unit) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Column {
                Text(stringResource(template.titleRes), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(template.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WindowTemplatesScreenPreview() {
    RingfenceTheme {
        WindowTemplatesScreen(onContinue = {}, onSkip = {})
    }
}

@Preview(showBackground = true, uiMode = 0x20)
@Composable
private fun WindowTemplatesScreenDarkPreview() {
    RingfenceTheme(useDarkTheme = true) {
        WindowTemplatesScreen(onContinue = {}, onSkip = {})
    }
}
