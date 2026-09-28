package com.dastyar.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ui.theme.Rose
import com.dastyar.app.ui.theme.Purple
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The fifty short loving welcome notes. One is picked at random every time the
 * app is opened; the text is fixed and must not be edited.
 */
private val WELCOME_MESSAGES = listOf(
    "m❤️f همیشه به یادت هستم.",
    "m❤️f تو دلیل لبخندمی.",
    "m❤️f دوست داشتنت قشنگ‌ترین حس دنیاست.",
    "m❤️f دلم همیشه با توئه.",
    "m❤️f برای تو ساخته شده، با عشق.",
    "m❤️f تو برای من خاص‌ترینی.",
    "m❤️f لبخندت دنیای منه.",
    "m❤️f همیشه یه گوشه قلبمی.",
    "m❤️f بودنت برام باارزشه.",
    "m❤️f دلم می‌خواد همیشه خوشحال باشی.",
    "m❤️f تو قشنگ‌ترین اتفاق زندگی منی.",
    "m❤️f هر روز بیشتر دوستت دارم.",
    "m❤️f حضورت دنیا رو قشنگ‌تر می‌کنه.",
    "m❤️f اینجا همیشه برای توئه.",
    "m❤️f تو ارزشمندتر از چیزی هستی که فکر می‌کنی.",
    "m❤️f امیدوارم امروز لبخند بزنی.",
    "m❤️f دلم به بودنت گرمه.",
    "m❤️f تو همیشه عزیز دلی.",
    "m❤️f برای لبخندت ساخته شد.",
    "m❤️f هیچ‌وقت یادت نره چقدر دوست‌داشتنی هستی.",
    "m❤️f تو یکی از قشنگ‌ترین دلایل خوشحالی منی.",
    "m❤️f هر جا باشی، به یادت هستم.",
    "m❤️f خوشحالی تو برای من مهمه.",
    "m❤️f تو همون حس خوب قلبمی.",
    "m❤️f دلم می‌خواد همیشه حالت خوب باشه.",
    "m❤️f اینجا یه تکه از عشق منه برای تو.",
    "m❤️f تو سزاوار قشنگ‌ترین لحظه‌هایی.",
    "m❤️f دوست داشتنت برای من تکراری نمی‌شه.",
    "m❤️f تو همیشه توی قلب منی.",
    "m❤️f امیدوارم این پیام لبخندت کنه.",
    "m❤️f هر روزت پر از حس خوب.",
    "m❤️f تو قشنگیِ ساده‌ی زندگی منی.",
    "m❤️f داشتن تو برای من باارزشه.",
    "m❤️f دلم می‌خواد امروز بهترین روزت باشه.",
    "m❤️f یه لبخند بزن، خیلی بهت میاد.",
    "m❤️f همیشه هواتو دارم.",
    "m❤️f تو دلیل خیلی از لبخندهای منی.",
    "m❤️f این برنامه یه یادگاری کوچیک از منه برای توئه.",
    "m❤️f امیدوارم هر روزت قشنگ‌تر از دیروز باشه.",
    "m❤️f قلبم با دیدن لبخندت آروم می‌گیره.",
    "m❤️f تو برای من یه آدم معمولی نیستی.",
    "m❤️f دوستت دارم، همین ساده و واقعی.",
    "m❤️f هر بار دیدنت هنوز برام قشنگه.",
    "m❤️f دلم می‌خواد همیشه کنار دلت آرامش باشه.",
    "m❤️f تو قشنگ‌ترین حس این روزهای منی.",
    "m❤️f اینجا رو با فکر تو ساختم.",
    "m❤️f امیدوارم همیشه دلت گرم باشه.",
    "m❤️f تو لایق تمام اتفاق‌های خوب دنیایی.",
    "m❤️f ممنون که هستی.",
    "m❤️f اینجا همیشه یه پیام از من برای تو هست."
)

/**
 * A short loving note shown on every app open, before anything else (including
 * the daily questionnaire). It appears with a soft fade, stays for exactly five
 * seconds and then removes itself with no user action. It is pure UI: nothing is
 * written to the chat history, the user memory or the check-in data, and a fresh
 * random note is drawn on every open.
 */
@Composable
fun WelcomeMessage(onFinished: () -> Unit) {
    // A new random line each time this composable enters composition (i.e. each
    // app open). `remember` without keys is fine: the parent shows it once per
    // open, and reopening the app creates a new composition.
    val message = remember { WELCOME_MESSAGES[Random.nextInt(WELCOME_MESSAGES.size)] }

    // Exact five-second lifetime, then a quick fade-out and auto-dismiss with no
    // tap required.
    var visible by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(5_000)
        visible = false
        delay(350)          // let the fade-out finish before the overlay unmounts
        onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Rose.copy(alpha = .22f),
                                    Purple.copy(alpha = .22f)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Text("❤️", fontSize = 34.sp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                    Text(
                        message,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}
