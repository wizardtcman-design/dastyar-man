package com.dastyar.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    // Collected so the overlay re-evaluates the moment a check-in is completed
    // (or the day rolls over); the value itself is decided inside
    // shouldAskCheckIn, which reads the durable completion marker.
    val completedDay by vm.completedDay.collectAsState()

    // Detect a new day while the app stays open or is brought back from the
    // background: on every resume, reload today's check-in. If the date rolled
    // over, today's row is missing again and the daily questions re-appear.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onAppResumed()
                // Re-check GitHub on every return to the app, not just at
                // launch: a release published while the app sat in the
                // background is picked up as soon as the user comes back.
                vm.checkForUpdates()
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

    // An update notification tap opens the in-app update screen.
    var showUpdate by remember { mutableStateOf(false) }
    val openUpdate by vm.openUpdate.collectAsState()
    LaunchedEffect(openUpdate) {
        if (openUpdate > 0) showUpdate = true
    }

    // A newer release found by the launch/resume check. Shown as an in-app bar
    // so the update is visible even when notifications are turned off.
    val updateInfo by vm.updateBanner.collectAsState()

    // One-time "دستیار من به‌روز شد" note per version: if the running version
    // has not been announced to this user yet, fetch its release notes and show
    // the dialog once, then remember it so it never appears again.
    val whatsCtx = LocalContext.current
    var whatsVersion by remember { mutableStateOf<String?>(null) }
    var whatsNotes by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val current = com.dastyar.app.BuildConfig.VERSION_NAME
        if (com.dastyar.app.update.UpdatePrefs.whatsNewShownVersion(whatsCtx) != current) {
            val notes = com.dastyar.app.update.GitHubReleaseClient
                .fetchNotesForVersion(current)
            whatsNotes = notes.orEmpty()
            whatsVersion = current
        }
    }

    // The daily check-in is only offered once the user finished onboarding and
    // only when today's row does not exist yet. On the onboarding day the
    // questionnaire already became the day-one record, so it is never asked twice.
    val needsCheckIn = completedDay != vm.today &&
            vm.shouldAskCheckIn(vm.profile.value, todayCheckIn)

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

            // In-app "new version" bar. Tapping it opens the update screen the
            // notification would have opened, so the user always has a way in.
            updateInfo?.let { info ->
                UpdateBanner(
                    version = info.version,
                    onClick = { showUpdate = true },
                    onDismiss = { vm.dismissUpdateBanner() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    // ------------------------------------------------- update screen
    //
    // Opened from the "به‌روزرسانی" shortcut in Settings or from an update
    // notification tap. It is a full-screen overlay so it works from any tab.
    if (showUpdate) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        )
                    )
            ) {
                UpdateScreen(onClose = { showUpdate = false })
            }
        }
    }

    // ------------------------------------------------- what's new (once)
    whatsVersion?.let { v ->
        com.dastyar.app.ui.components.WhatsNewDialog(
            version = v,
            notes = whatsNotes,
            onDismiss = {
                com.dastyar.app.update.UpdatePrefs.setWhatsNewShownVersion(whatsCtx, v)
                whatsVersion = null
            }
        )
    }

    // ------------------------------------------------- welcome note
    //
    // On every app open a short loving note appears first (for exactly five
    // seconds, then removes itself). It is pure UI: it is never stored in the
    // chat history, the user memory or the check-in data. Plain `remember` (not
    // saveable) means a fresh open always shows it again, with a new random line.
    var welcomeDone by remember { mutableStateOf(false) }
    if (!welcomeDone) {
        com.dastyar.app.ui.components.WelcomeMessage(onFinished = { welcomeDone = true })
        return
    }

    // ------------------------------------------------- daily questionnaire
    //
    // The short daily questions appear on their own the first time the app is
    // opened on a new day, as a full-screen overlay the user answers and closes.
    // There is deliberately no permanent card or panel for it in the dashboard.
    // Once today's row is saved, `shouldAskCheckIn` is false, so it appears only
    // once per day no matter how many times the app is opened.
    // Remembered per calendar day, not per process: skipping or finishing today
    // hides it until tomorrow, when `vm.today` changes and it re-opens on its own.
    var dismissedDay by rememberSaveable { mutableStateOf<String?>(null) }
    if (needsCheckIn && dismissedDay != vm.today) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // A small, safe way out if the user is busy right now. It is part of
            // the scrolling content, not a floating button: the old bottom-
            // centred "بعداً" sat on top of "ذخیره" and stole its taps, which is
            // exactly how answers looked "saved" but were never written.
            Box(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                CheckInScreen(
                    vm,
                    onDone = { dismissedDay = vm.today },
                    onSkip = { dismissedDay = vm.today }
                )
            }
        }
    }
}

/**
 * The in-app "نسخه تازه" bar shown at the top of the screen when a newer release
 * exists. It is the fallback for users whose notifications are off, and it opens
 * the same update screen the notification would. Dismissing it only hides the
 * bar for this session; the next resume check can show it again.
 */
@Composable
private fun UpdateBanner(
    version: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier
                .clickable { onClick() }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "نسخه تازه $version",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "برای دیدن تغییرات و نصب، بزن.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "بستن",
                    tint = Color.White
                )
            }
        }
    }
}
