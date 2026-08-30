package com.ringfence.silentscheduler.settings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.core.notification.NotificationStyle
import com.ringfence.silentscheduler.core.ringer.RevertPolicy
import com.ringfence.silentscheduler.core.ringer.SilenceStyle
import com.ringfence.silentscheduler.core.theme.BackgroundDark
import com.ringfence.silentscheduler.core.theme.BackgroundLight
import com.ringfence.silentscheduler.core.theme.ThemeOverride
import com.ringfence.silentscheduler.core.ui.RadioOptionRow
import com.ringfence.silentscheduler.core.ui.SegmentedControl
import com.ringfence.silentscheduler.settings.domain.AVAILABLE_DURATION_MINUTES

/**
 * Build-order step 8. Matches the dark-mode design in DESIGN_NOTES.md — light-mode
 * Settings wasn't in the source images, but layout is identical across themes there
 * (only color tokens swap), so the same composable covers both.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val isDndAccessGranted by viewModel.isDndAccessGranted.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDndStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No follow-up needed: SilenceNotifier checks the permission again before every post. */ }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))

        SettingsSection(
            title = stringResource(R.string.settings_section_duration),
            caption = stringResource(R.string.settings_duration_caption)
        ) {
            SegmentedControl(
                options = AVAILABLE_DURATION_MINUTES,
                selected = settings.defaultDurationMinutes,
                labelFor = { minutes -> durationLabel(minutes) },
                onSelect = viewModel::setDefaultDurationMinutes
            )
        }

        Spacer(Modifier.height(20.dp))

        SettingsSection(title = stringResource(R.string.settings_section_silence_style)) {
            SegmentedControl(
                options = listOf(SilenceStyle.FULL_SILENT, SilenceStyle.VIBRATE_ONLY),
                selected = settings.silenceStyle,
                labelFor = { style ->
                    if (style == SilenceStyle.FULL_SILENT) {
                        stringResource(R.string.settings_silence_style_full)
                    } else {
                        stringResource(R.string.settings_silence_style_vibrate)
                    }
                },
                onSelect = viewModel::setSilenceStyle
            )
        }

        Spacer(Modifier.height(20.dp))

        SettingsSection(
            title = stringResource(R.string.settings_section_revert),
            caption = stringResource(R.string.settings_revert_caption)
        ) {
            RadioOptionRow(
                title = stringResource(R.string.settings_revert_restore_title),
                subtitle = stringResource(R.string.settings_revert_restore_subtitle),
                selected = settings.revertPolicy == RevertPolicy.RESTORE,
                onClick = { viewModel.setRevertPolicy(RevertPolicy.RESTORE) }
            )
            RadioOptionRow(
                title = stringResource(R.string.settings_revert_sound_title),
                subtitle = stringResource(R.string.settings_revert_sound_subtitle),
                selected = settings.revertPolicy == RevertPolicy.SOUND,
                onClick = { viewModel.setRevertPolicy(RevertPolicy.SOUND) }
            )
        }

        Spacer(Modifier.height(20.dp))

        SettingsSection(title = stringResource(R.string.settings_section_notification_style)) {
            RadioOptionRow(
                title = stringResource(R.string.settings_notification_banner_title),
                subtitle = stringResource(R.string.settings_notification_banner_subtitle),
                selected = settings.notificationStyle == NotificationStyle.BANNER,
                onClick = {
                    viewModel.setNotificationStyle(NotificationStyle.BANNER)
                    requestNotificationPermissionIfNeeded()
                }
            )
            RadioOptionRow(
                title = stringResource(R.string.settings_notification_silent_log_title),
                subtitle = stringResource(R.string.settings_notification_silent_log_subtitle),
                selected = settings.notificationStyle == NotificationStyle.SILENT_LOG,
                onClick = {
                    viewModel.setNotificationStyle(NotificationStyle.SILENT_LOG)
                    requestNotificationPermissionIfNeeded()
                }
            )
            RadioOptionRow(
                title = stringResource(R.string.settings_notification_none_title),
                subtitle = stringResource(R.string.settings_notification_none_subtitle),
                selected = settings.notificationStyle == NotificationStyle.NONE,
                onClick = { viewModel.setNotificationStyle(NotificationStyle.NONE) }
            )
        }

        Spacer(Modifier.height(20.dp))

        val systemInDarkTheme = isSystemInDarkTheme()
        SettingsSection(
            title = stringResource(R.string.settings_section_appearance),
            caption = if (systemInDarkTheme) {
                stringResource(R.string.settings_appearance_caption_system_dark)
            } else {
                stringResource(R.string.settings_appearance_caption_system_light)
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AppearanceOption(
                    label = stringResource(R.string.settings_appearance_light),
                    selected = settings.themeOverride == ThemeOverride.LIGHT,
                    swatch = { Spacer(it.background(BackgroundLight)) },
                    onClick = { viewModel.setThemeOverride(ThemeOverride.LIGHT) }
                )
                AppearanceOption(
                    label = stringResource(R.string.settings_appearance_dark),
                    selected = settings.themeOverride == ThemeOverride.DARK,
                    swatch = { Spacer(it.background(BackgroundDark)) },
                    onClick = { viewModel.setThemeOverride(ThemeOverride.DARK) }
                )
                AppearanceOption(
                    label = stringResource(R.string.settings_appearance_system),
                    selected = settings.themeOverride == ThemeOverride.SYSTEM,
                    swatch = { Spacer(it.background(Brush.linearGradient(listOf(BackgroundLight, BackgroundDark)))) },
                    onClick = { viewModel.setThemeOverride(ThemeOverride.SYSTEM) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_dnd_status_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                if (isDndAccessGranted) {
                    Text(
                        text = stringResource(R.string.settings_dnd_status_granted),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }) {
                        Text(stringResource(R.string.settings_dnd_open_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun durationLabel(minutes: Int): String = when (minutes) {
    15 -> stringResource(R.string.settings_duration_15m)
    30 -> stringResource(R.string.settings_duration_30m)
    60 -> stringResource(R.string.settings_duration_1h)
    else -> stringResource(R.string.settings_duration_2h)
}

@Composable
private fun SettingsSection(
    title: String,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
        if (caption != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    swatch: @Composable (Modifier) -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        swatch(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
