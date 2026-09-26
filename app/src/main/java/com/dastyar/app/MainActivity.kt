package com.dastyar.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dastyar.app.notifications.DailyReminder
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.screens.ConnectGate
import com.dastyar.app.ui.screens.MainScaffold
import com.dastyar.app.ui.screens.OnboardingFlow
import com.dastyar.app.ui.theme.DastyarTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CHECKIN = "open_checkin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Persian + RTL for the whole app, no matter the phone language.
        applyPersianLocale()

        super.onCreate(savedInstanceState)
        DailyReminder.reschedule(this)
        val openCheckIn = intent?.getBooleanExtra(EXTRA_OPEN_CHECKIN, false) == true

        setContent {
            DastyarTheme {
                // A hard RTL guarantee: every Row, Column, icon, padding edge and
                // gesture is mirrored, so the whole UI flows right-to-left.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val vm: MainViewModel = viewModel()
                    val profile by vm.profile.collectAsState()

                    LaunchedEffect(openCheckIn) {
                        if (openCheckIn && profile?.onboardingDone == true) vm.requestOpenCheckIn()
                    }

                    // First run: the app has no built-in key, so the user
                    // connects their own before anything else. The flag is held
                    // in state so a successful test moves straight on.
                    var keyReady by remember { mutableStateOf(ServiceKeys.anyConnected()) }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            !keyReady -> ConnectGate { keyReady = true }
                            profile?.onboardingDone == true -> MainScaffold(vm)
                            else -> OnboardingFlow(vm)
                        }
                    }
                }
            }
        }
    }

    /**
     * Pins the app to Persian so dates, numbers and system text are shown the
     * Persian way and the layout direction is RTL even on an English phone.
     */
    private fun applyPersianLocale() {
        val locale = Locale("fa", "IR")
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
