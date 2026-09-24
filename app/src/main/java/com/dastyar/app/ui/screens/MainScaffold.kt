package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.MainViewModel

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScaffold(vm: MainViewModel) {
    var tab by remember { mutableStateOf("home") }
    var checkedIn by remember { mutableStateOf(false) }
    val todayCheckIn by vm.todayCheckIn.collectAsState()

    // Automatic daily check-in: if today has no check-in row yet, ask once.
    LaunchedEffect(vm.profile.value, todayCheckIn) {
        checkedIn = true
    }
    val needsCheckIn = todayCheckIn == null && vm.profile.value != null

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
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(tonalElevation = 8.dp) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.route,
                        onClick = { tab = t.route },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = {
                            Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
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
