package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dastyar.app.ui.MainViewModel

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScaffold(vm: MainViewModel) {
    var tab by remember { mutableStateOf("home") }
    val todayCheckIn by vm.todayCheckIn.collectAsState()

    // Detect a new day while the app stays open or is brought back from the
    // background: on every resume, reload today's check-in. If the date rolled
    // over, today's row is missing again and the daily questions re-appear.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A notification tap deep-links straight into the check-in screen.
    val openCheckIn by vm.openCheckIn.collectAsState()
    LaunchedEffect(openCheckIn) {
        if (openCheckIn > 0) tab = "checkin"
    }

    // A task reminder tap opens the tasks tab on that exact task.
    val openTask by vm.openTask.collectAsState()
    LaunchedEffect(openTask) {
        if (openTask > 0L) tab = "tasks"
    }

    // The daily check-in is only offered once the user finished onboarding and
    // only when today's row does not exist yet. On the onboarding day the
    // questionnaire already became the day-one record, so it is never asked twice.
    val needsCheckIn = vm.shouldAskCheckIn(vm.profile.value, todayCheckIn)

    val tabs = listOf(
        TabItem("home", "خانه", Icons.Filled.Home),
        TabItem("assistant", "دستیار", Icons.Filled.SmartToy),
        TabItem("image", "تصویر AI", Icons.Filled.Palette),
        TabItem("cooking", "آشپزی", Icons.Filled.Restaurant),
        TabItem("tasks", "کارها", Icons.Filled.CheckCircle)
    )

    val toast by vm.toast.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    Scaffold(
        // Edge-to-edge: we place the system-bar insets ourselves below, so the
        // Scaffold must not also reserve them (that would double-pad the top and
        // push the first card under the status bar).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                // Edge-to-edge: keep the tab bar above the system navigation bar.
                // Height is a minimum so the inset can be added on top of it.
                modifier = Modifier
                    .navigationBarsPadding()
                    .heightIn(min = 68.dp)
            ) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.route,
                        onClick = { tab = t.route },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = {
                            Text(
                                t.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { pad ->
        // Edge-to-edge is on, so the Scaffold no longer reserves the system bars.
        // Apply the status-bar inset at the top; the bottom bar inset is handled
        // by the NavigationBar itself. Screens that host a chat composer add the
        // IME inset on their own, so the keyboard never covers the input.
        Box(
            Modifier
                .padding(pad)
                // Top + left/right safe insets (status bar, display cutout). The
                // bottom is handled by the NavigationBar, and chat screens add
                // the IME inset themselves.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    )
                )
        ) {
            when (tab) {
                "home" -> HomeScreen(
                    vm = vm,
                    onOpenCheckIn = { tab = "checkin" },
                    needsCheckIn = needsCheckIn
                )
                "assistant" -> AssistantScreen(vm)
                "image" -> ImageScreen(vm)
                "cooking" -> CookingScreen(vm)
                "tasks" -> TasksScreen(vm)
                "checkin" -> CheckInScreen(vm, onDone = { tab = "home" })
            }
        }
    }

    // When the app opens a new day, jump straight into the short check-in.
    LaunchedEffect(needsCheckIn) {
        if (needsCheckIn) tab = "checkin"
    }
}
