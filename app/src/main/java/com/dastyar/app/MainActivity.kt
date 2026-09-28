package com.dastyar.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.dastyar.app.security.AppLock
import com.dastyar.app.security.LockScreen
import com.dastyar.app.security.showBiometricPrompt
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.screens.ConnectGate
import com.dastyar.app.ui.screens.MainScaffold
import com.dastyar.app.ui.screens.OnboardingFlow
import com.dastyar.app.ui.theme.DastyarTheme
import java.util.Locale

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_CHECKIN = "open_checkin"
        const val EXTRA_OPEN_TASK = "open_task"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force Persian + RTL for the whole app, no matter the phone language.
        applyPersianLocale()

        super.onCreate(savedInstanceState)

        // Explicit, deterministic edge-to-edge. On targetSdk 34 the system does
        // not reliably resize the window for the keyboard any more; with insets
        // handed to Compose, the chat screens consume the IME inset themselves
        // and the message list shrinks while the composer stays above the
        // keyboard. The status bar stays excluded so headers are not overlapped.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        DailyReminder.reschedule(this)
        val openCheckIn = intent?.getBooleanExtra(EXTRA_OPEN_CHECKIN, false) == true
        val openTask = intent?.getLongExtra(EXTRA_OPEN_TASK, 0L) ?: 0L

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
                    LaunchedEffect(openTask) {
                        if (openTask > 0L && profile?.onboardingDone == true) vm.requestOpenTask(openTask)
                    }

                    // First run: the app has no built-in key, so the user
                    // connects their own before anything else. The flag is held
                    // in state so a successful test moves straight on.
                    var keyReady by remember { mutableStateOf(ServiceKeys.anyConnected()) }

                    // App lock: when the user has set a PIN/pattern, nothing is
                    // shown until they unlock. "locked" is plain remember, so the
                    // lock re-arms every time the activity is created (cold start).
                    val ctx = LocalContext.current
                    var locked by remember { mutableStateOf(AppLock.isEnabled(ctx)) }
                    var lockError by remember { mutableStateOf<String?>(null) }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            locked -> {
                                LockScreen(
                                    title = "دستیار من قفل است",
                                    subtitle = "برای ورود، رمز یا الگوی خود را وارد کن",
                                    mode = AppLock.mode(ctx),
                                    allowBiometric = AppLock.biometricEnabled(ctx),
                                    error = lockError,
                                    onBiometricRequested = {
                                        showBiometricPrompt(
                                            activity = this@MainActivity,
                                            title = "ورود با اثر انگشت"
                                        ) { ok ->
                                            if (ok) { lockError = null; locked = false }
                                            else lockError = "اثر انگشت تأیید نشد"
                                        }
                                    },
                                    onCode = { code ->
                                        if (AppLock.verify(ctx, code)) {
                                            lockError = null
                                            locked = false
                                        } else {
                                            lockError = "رمز اشتباه است"
                                        }
                                    }
                                )
                            }
                            !keyReady -> {
                                // Edge-to-edge: keep the first-run gate clear of the
                                // status bar, navigation bar and any display cutout,
                                // so no card is ever half cut.
                                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    ConnectGate { keyReady = true }
                                }
                            }
                            profile?.onboardingDone == true -> MainScaffold(vm)
                            else -> {
                                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    OnboardingFlow(vm)
                                }
                            }
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
