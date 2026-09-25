package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.data.ChatMessage
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Pink
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

@Composable
fun AssistantScreen(vm: MainViewModel) {
    var channel by remember { mutableStateOf<String?>(null) }

    if (channel == null) {
        AssistantHome(onOpen = { channel = it })
    } else {
        ChatScreen(
            vm = vm,
            channel = channel!!,
            onBack = { channel = null }
        )
    }
}

@Composable
private fun AssistantHome(onOpen: (String) -> Unit) {
    val cards = listOf(
        Triple("period", "🩷", "مراقبت از پریود"),
        Triple("skin", "✨", "مراقبت از پوست"),
        Triple("fatigue", "⚡", "بی‌رمقی و انرژی")
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        ScreenHeader(
            emoji = "🤖",
            title = "دستیار من",
            subtitle = "هر موضوعی می‌خواهی بپرس، یا از بخش‌های تخصصی استفاده کن."
        )
        Spacer(Modifier.height(16.dp))

        DastyarCard(onClick = { onOpen("general") }, accent = Purple) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Purple.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center
                ) { Text("💬", fontSize = 23.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("گفت‌وگوی آزاد با دستیار", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "هر سؤالی داری بپرس — درباره وضعیت خودت هم می‌داند.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("←", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("بخش‌های تخصصی", "🌟")
        Spacer(Modifier.height(12.dp))

        cards.forEach { (key, emoji, title) ->
            val accent = when (key) {
                "period" -> Pink
                "skin" -> Amber
                else -> Rose
            }
            DastyarCard(onClick = { onOpen(key) }, accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(accent.copy(alpha = .16f)),
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 21.sp) }
                    Spacer(Modifier.width(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text("←", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(4.dp))
        DastyarCard {
            Text(
                "دستیار من تشخیص پزشکی نمی‌دهد و جایگزین پزشک نیست. " +
                        "در صورت وجود علائم هشدار، لطفاً به پزشک مراجعه کن.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatScreen(vm: MainViewModel, channel: String, onBack: () -> Unit) {
    val messages by vm.chatFlow(channel).collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val title = when (channel) {
        "period" -> "🩷 مراقبت از پریود"
        "skin" -> "✨ مراقبت از پوست"
        "fatigue" -> "⚡ بی‌رمقی و انرژی"
        else -> "💬 دستیار"
    }
    val starters = when (channel) {
        "period" -> listOf("امروز درد دارم، چیکار کنم؟", "چه غذایی خوبه؟", "چطور درد رو کم کنم؟")
        "skin" -> listOf("روتین ساده پوست چیه؟", "برای جوش‌هام چیکار کنم؟", "ضدآفتاب لازمه؟")
        "fatigue" -> listOf("خیلی خسته‌ام، چیکار کنم؟", "چطور خوابم رو بهتر کنم؟", "چه غذایی انرژی می‌ده؟")
        else -> listOf("امروز چه کاری انجام بدم؟", "یه برنامه سبک برای امروز بده", "چطور حالت رو بهتر کنم؟")
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(Purple.copy(alpha = .25f), Pink.copy(alpha = .18f)))
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت")
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.clearChat(channel) }) {
                Text("پاک کردن", fontSize = 12.sp)
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column {
                        Text(
                            "سلام! چه کمکی می‌تونم بکنم؟",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        starters.forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        vm.chat(channel, s)
                                        busy = true
                                    }
                            ) {
                                Text(s, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            items(messages) { m -> MessageBubble(m) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیامت رو بنویس…", fontSize = 13.sp) },
                shape = RoundedCornerShape(16.dp),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !busy) {
                        input = ""
                        busy = true
                        vm.chat(channel, text)
                    }
                },
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "ارسال")
            }
        }
    }

    // release the busy flag shortly after a reply arrives
    LaunchedEffect(messages.size) {
        if (busy) busy = false
    }
}

@Composable
private fun MessageBubble(m: ChatMessage) {
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (isUser) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    else Modifier.background(
                        Brush.linearGradient(listOf(Purple.copy(alpha = .85f), Pink.copy(alpha = .75f)))
                    )
                )
                .then(
                    if (isUser) Modifier.border(
                        1.dp,
                        Purple.copy(alpha = .40f),
                        RoundedCornerShape(16.dp)
                    ) else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                m.content,
                fontSize = 14.sp,
                color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
            )
        }
    }
}
