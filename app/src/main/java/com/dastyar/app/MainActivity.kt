package com.dastyar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.screens.MainScaffold
import com.dastyar.app.ui.screens.OnboardingFlow
import com.dastyar.app.ui.theme.DastyarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DastyarTheme {
                val vm: MainViewModel = viewModel()
                val profile by vm.profile.collectAsState()

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
