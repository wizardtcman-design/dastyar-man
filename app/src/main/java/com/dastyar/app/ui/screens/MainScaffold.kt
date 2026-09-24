package com.dastyar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.MainViewModel

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScaffold(vm: MainViewModel) {
    var tab by remember { mutableStateOf("home") }
    val todayCheckIn by vm.todayCheckIn.collectAsState()

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
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.height(68.dp)
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
