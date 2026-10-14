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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.ringfence.silentscheduler.core.locale.AppLanguage
import com.ringfence.silentscheduler.core.theme.RingfenceTheme
import com.ringfence.silentscheduler.core.ui.PrimaryButton
import com.ringfence.silentscheduler.core.ui.RadioOptionRow

/**
 * First onboarding step, shown before the DND explainer — picking the display
 * language before anything else is read aloud to the user matters more than any
 * other first-run choice here. Tapping an option applies it immediately (see
 * [OnboardingViewModel.selectLanguage]) so this screen itself becomes the preview;
 * "Continue" only records that the one-time picker has been seen.
 */
@Composable
fun LanguageSelectionScreen(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
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
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_language_headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_language_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                RadioOptionRow(
                    title = stringResource(R.string.language_option_english_title),
                    subtitle = stringResource(R.string.language_option_english_subtitle),
                    selected = selected == AppLanguage.ENGLISH,
                    onClick = { onSelect(AppLanguage.ENGLISH) }
                )
                RadioOptionRow(
                    title = stringResource(R.string.language_option_bengali_title),
                    subtitle = stringResource(R.string.language_option_bengali_subtitle),
                    selected = selected == AppLanguage.BENGALI,
                    onClick = { onSelect(AppLanguage.BENGALI) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = stringResource(R.string.onboarding_language_continue_cta),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageSelectionScreenPreview() {
    RingfenceTheme {
        LanguageSelectionScreen(selected = AppLanguage.ENGLISH, onSelect = {}, onContinue = {})
    }
}

@Preview(showBackground = true, uiMode = 0x20)
@Composable
private fun LanguageSelectionScreenDarkPreview() {
    RingfenceTheme(useDarkTheme = true) {
        LanguageSelectionScreen(selected = AppLanguage.BENGALI, onSelect = {}, onContinue = {})
    }
}
