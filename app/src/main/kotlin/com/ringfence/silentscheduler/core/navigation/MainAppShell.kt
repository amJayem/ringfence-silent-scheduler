package com.ringfence.silentscheduler.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ringfence.silentscheduler.R
import com.ringfence.silentscheduler.quicksilence.ui.SilentNowSheet
import com.ringfence.silentscheduler.schedule.domain.Schedule
import com.ringfence.silentscheduler.schedule.ui.DashboardScreen
import com.ringfence.silentscheduler.schedule.ui.ScheduleEditScreen
import com.ringfence.silentscheduler.schedule.ui.ScheduleFormViewModel
import com.ringfence.silentscheduler.settings.ui.SettingsScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val SCHEDULE_ADD = "schedule_add"
    const val SCHEDULE_EDIT = "schedule_edit/{scheduleId}"
    fun scheduleEdit(id: String) = "schedule_edit/$id"
}

private val bottomNavRoutes = setOf(Routes.DASHBOARD, Routes.SETTINGS)

/**
 * The 3-tab shell (Schedules / Silent now / Settings) shown once onboarding
 * permissions are granted. Add/Edit Schedule are pushed on top without the bottom
 * bar, matching the design's full-screen editor pattern. "Silent now" isn't a
 * fourth route: per the design it's a bottom sheet reachable from here or from the
 * Dashboard's own status card, not a separate screen (see DESIGN_NOTES.md) — using
 * a nav destination for it previously produced two different UIs for the same action.
 */
@Composable
fun MainAppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var showSilentNowSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar {
                    PillNavItem(
                        selected = currentRoute == Routes.DASHBOARD && !showSilentNowSheet,
                        onClick = { navController.navigateToTab(Routes.DASHBOARD) },
                        icon = painterResource(R.drawable.ic_schedule),
                        label = stringResource(R.string.nav_schedules)
                    )
                    PillNavItem(
                        selected = showSilentNowSheet,
                        onClick = {
                            navController.navigateToTab(Routes.DASHBOARD)
                            showSilentNowSheet = true
                        },
                        icon = painterResource(R.drawable.ic_do_not_disturb),
                        label = stringResource(R.string.nav_silent_now)
                    )
                    PillNavItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateToTab(Routes.SETTINGS) },
                        icon = painterResource(R.drawable.ic_tune),
                        label = stringResource(R.string.nav_settings)
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(contentPadding)
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onAddSchedule = { navController.navigate(Routes.SCHEDULE_ADD) },
                    onEditSchedule = { id -> navController.navigate(Routes.scheduleEdit(id)) },
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                    onSilentNow = { showSilentNowSheet = true }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.navigateToTab(Routes.DASHBOARD) })
            }
            composable(Routes.SCHEDULE_ADD) {
                val formViewModel: ScheduleFormViewModel = hiltViewModel()
                ScheduleEditScreen(
                    initial = null,
                    onSave = { schedule ->
                        formViewModel.save(schedule)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(
                Routes.SCHEDULE_EDIT,
                arguments = listOf(navArgument("scheduleId") { type = NavType.StringType })
            ) { backStackEntry ->
                val scheduleId = backStackEntry.arguments?.getString("scheduleId")
                val formViewModel: ScheduleFormViewModel = hiltViewModel()
                val schedules by formViewModel.schedules.collectAsState()
                val existing: Schedule? = schedules.find { it.id == scheduleId }
                ScheduleEditScreen(
                    initial = existing,
                    onSave = { schedule ->
                        formViewModel.save(schedule)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                    onDelete = scheduleId?.let { id ->
                        {
                            formViewModel.delete(id)
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }

    if (showSilentNowSheet) {
        SilentNowSheet(onDismiss = { showSilentNowSheet = false })
    }
}

/**
 * Matches the design's tab styling, which the stock M3 [androidx.compose.material3.NavigationBarItem]
 * can't reproduce: there, the selected indicator pill sizes only to the icon, and the
 * label keeps the default (non-accent) text color. Here the pill wraps icon+label as
 * one unit and both turn the accent color together when selected.
 */
@Composable
private fun RowScope.PillNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Painter,
    label: String
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val pillColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pillColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
