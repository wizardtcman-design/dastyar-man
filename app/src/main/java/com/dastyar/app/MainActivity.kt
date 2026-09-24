package com.dastyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dastyar.app.notifications.DailyReminder
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.screens.MainScaffold
import com.dastyar.app.ui.screens.OnboardingFlow
import com.dastyar.app.ui.theme.DastyarTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CHECKIN = "open_checkin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DailyReminder.reschedule(this)
        val openCheckIn = intent?.getBooleanExtra(EXTRA_OPEN_CHECKIN, false) == true

        setContent {
            DastyarTheme {
                val vm: MainViewModel = viewModel()
                val profile by vm.profile.collectAsState()

                androidx.compose.runtime.                androidx.compose.runtime.LaunchedEffect(openCheckIn) {
                    if (openCheckIn && profile?.onboardingDone == true) vm.requestOpenCheckIn()
                }


                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (profile?.onboardingDone == true) {
                        MainScaffold(vm)
                    } else {
                        OnboardingFlow(vm)
                    }
                }
            }
        }
    }
}
