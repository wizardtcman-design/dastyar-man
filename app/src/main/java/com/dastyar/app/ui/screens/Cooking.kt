package com.dastyar.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.Prompts
import com.dastyar.app.data.ChatMessage
import com.dastyar.app.data.SavedRecipe
import com.dastyar.app.ui.MainViewModel
import com.dastyar.app.ui.components.*
import com.dastyar.app.ui.theme.Amber
import com.dastyar.app.ui.theme.Cyan
import com.dastyar.app.ui.theme.Green
import com.dastyar.app.ui.theme.Purple
import com.dastyar.app.ui.theme.Rose

/**
 * The cooking tab.
 *
 * The home screen stays clean: a compact header, two cards that open the
 * dedicated lunch and dinner chats, and the "what's in my kitchen" suggestion
 * whose results appear inline here. The lunch and dinner pages are two separate
 * specialist chats — each with its own history, its own "new suggestion"
 * button and a chat composer — so a suggestion never clutters the home screen.
 *
 * Everything runs on the existing chat API ([AiClient.chat] via `vm.chat`); no
 * provider, key or endpoint is added.
 */
@Composable
fun CookingScreen(vm: MainViewModel) {
    var page by remember { mutableStateOf("home") }   // home / lunch / dinner

    when (page) {
        "lunch" -> MealChatScreen(vm = vm, meal = Meal.LUNCH, onBack = { page = "home" })
        "dinner" -> MealChatScreen(vm = vm, meal = Meal.DINNER, onBack = { page = "home" })
        else -> CookingHome(
            vm = vm,
            onOpenLunch = { page = "lunch" },
            onOpenDinner = { page = "dinner" }
        )
    }
}

/** The two meal chats, each with its own identity, colour and chat channel. */
private enum class Meal(
    val channel: String,
    val emoji: String,
    val title: String,
    val accent: Color,
    val newLabel: String
) {
    LUNCH("lunch", "🍲", "پیشنهاد ناهار", Amber, "🔄 پیشنهاد ناهار جدید"),
    DINNER("dinner", "🌙", "پیشنهاد شام", Purple, "🔄 پیشنهاد شام جدید")
}

// ------------------------------------------------------------------- home

@Composable
private fun CookingHome(
    vm: MainViewModel,
    onOpenLunch: () -> Unit,
    onOpenDinner: () -> Unit
) {
    val saved by vm.savedRecipes.collectAsState()

    var servings by remember { mutableIntStateOf(4) }
    var pantryInput by remember { mutableStateOf("") }

    val pantryText by vm.pantryText.collectAsState()
    val busy by vm.pantryLoading.collectAsState()
    val vmError by vm.pantryError.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }

    val pantryResult = remember(pantryText) {
        pantryText?.let { parseRecipes(it) } ?: emptyList()
    }
    val error = localError ?: vmError

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        CookingHeader()

        Spacer(Modifier.height(10.dp))

        // servings — applies to the pantry suggestion on this screen
        Text("تعداد نفرات", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(1, 2, 3, 4, 5, 6).forEach { n ->
                SelectChip(
                    label = if (n == 6) "۶+" else com.dastyar.app.data.Dates.fa(n),
                    selected = servings == n,
                    accent = Cyan,
                    compact = true
                ) { servings = n }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- the two entry cards ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryCard(
                emoji = Meal.LUNCH.emoji,
                title = "پیشنهاد ناهار",
                accent = Meal.LUNCH.accent,
                modifier = Modifier.weight(1f),
                onClick = onOpenLunch
            )
            EntryCard(
                emoji = Meal.DINNER.emoji,
                title = "پیشنهاد شام",
                accent = Meal.DINNER.accent,
                modifier = Modifier.weight(1f),
                onClick = onOpenDinner
            )
        }

        Spacer(Modifier.height(18.dp))

        // ---- pantry: input + inline results ----
        SectionTitle("با چیزهایی که در خانه دارم", "🧺")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pantryInput,
            onValueChange = { pantryInput = it },
            placeholder = { Text("مثلاً: سیب‌زمینی، تخم‌مرغ، پیاز و گوجه", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            minLines = 3,
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green,
                unfocusedBorderColor = Green.copy(alpha = .35f)
            )
        )
        Spacer(Modifier.height(10.dp))
        GradientButton("🍳 پیشنهاد غذا", enabled = !busy) {
            val text = pantryInput.trim()
            if (text.isEmpty()) {
                localError = "اول مواد موجود در خانه را بنویس."
            } else if (!busy) {
                localError = null
                vm.suggestFromPantry(text, servings)
            }
        }

        if (busy) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("در حال پیدا کردن غذا برای تو…", fontSize = 13.sp)
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.error) {
                Text("⚠️ $it", fontSize = 13.sp)
            }
        }

        if (pantryResult.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            pantryResult.forEach { d ->
                RecipeCard(
                    r = d,
                    accent = Green,
                    busy = busy,
                    onSave = { vm.saveRecipe(d.name, "خانگی", d.raw) }
                )
                Spacer(Modifier.height(10.dp))
            }
        } else if (pantryText != null && !busy && error == null) {
            Spacer(Modifier.height(12.dp))
            DastyarCard(accent = MaterialTheme.colorScheme.error) {
                Text("⚠️ پیشنهادی پیدا نشد؛ دوباره امتحان کن.", fontSize = 13.sp)
            }
        }

        // ---- saved recipes ----
        if (saved.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("غذاهای ذخیره‌شده", "♡")
            Spacer(Modifier.height(10.dp))
            saved.forEach { r ->
                SavedRecipeCard(r, onDelete = { vm.deleteRecipe(r.id) })
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CookingHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Amber, Rose)))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = .22f)),
            contentAlignment = Alignment.Center
        ) { Text("🍳", fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("آشپزی", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(
                "امروز چی بپزم؟",
                color = Color.White.copy(alpha = .85f),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One large, equal entry card that opens a dedicated meal chat. */
@Composable
private fun EntryCard(
    emoji: String,
    title: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = .4f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 2)
        Text(
            "پیشنهاد",
            fontSize = 10.5.sp,
            color = accent,
            maxLines = 1
        )
    }
}

// ------------------------------------------------------------ meal chat

/**
 * A dedicated, self-contained suggestion chat for one meal. It keeps its own
 * history (its own chat channel), so lunch and dinner never mix, and the
 * history survives leaving and re-entering the page. "پیشنهاد ... جدید" is just
 * another message in the same conversation, so the user can press it many times
 * and also type their own questions.
 */
@Composable
private fun MealChatScreen(vm: MainViewModel, meal: Meal, onBack: () -> Unit) {
    val messages by vm.chatFlow(meal.channel).collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        busy = true
        vm.chat(meal.channel, t)
    }

    // Clear busy as soon as the assistant's reply reaches the history.
    LaunchedEffect(messages.size) { if (busy) busy = false }

    // The first time this meal page is opened with no history, ask for a
    // suggestion automatically so the user sees a dish without pressing the
    // button. It runs once per empty conversation: the flag is kept per channel,
    // and an existing history is left untouched.
    var autoAsked by rememberSaveable(meal.channel) { mutableStateOf(false) }
    LaunchedEffect(meal.channel, messages.isEmpty()) {
        if (!autoAsked && messages.isEmpty() && !busy) {
            autoAsked = true
            send(Prompts.mealSuggestPrompt(meal.title.removePrefix("پیشنهاد "), 4, emptyList()))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // fixed back header with the same overflow menu as the other chats
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(meal.accent.copy(alpha = .26f), Rose.copy(alpha = .18f))
                    )
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "بازگشت", modifier = Modifier.size(20.dp))
            }
            Text(
                meal.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(meal.emoji, fontSize = 17.sp)
            ChatOverflowMenu(onClear = { confirmClear = true })
            Spacer(Modifier.width(2.dp))
        }

        // message list — the only part that shrinks for the keyboard
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item(key = "hint") {
                Text(
                    "این‌جا می‌توانی با دکمهٔ «${meal.newLabel}» پیشنهاد بگیری یا خودت سؤالت را بنویسی. " +
                            "مثلاً: «یک غذای بدون گوشت پیشنهاد بده» یا «برای ۴ نفر بگو».",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(messages, key = { it.id }) { m ->
                if (m.role == "user") {
                    UserBubble(m)
                } else {
                    // In the meal chats only the structured recipe is ever
                    // rendered. A model reply that is not a parseable recipe
                    // (prose, an echo of the instructions, English reasoning) is
                    // NEVER shown as text: the user sees a retry notice instead.
                    val recipes = remember(m.id, m.content) { parseRecipes(m.content) }
                    if (recipes.isNotEmpty()) {
                        recipes.forEach { d ->
                            RecipeCard(
                                r = d,
                                accent = meal.accent,
                                busy = busy,
                                onSave = { vm.saveRecipe(d.name, meal.title, d.raw) }
                            )
                        }
                    } else {
                        Card {
                            Text(
                                "⚠️ پیشنهاد غذا کامل دریافت نشد؛ «${meal.newLabel}» را دوباره بزن.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            if (busy) {
                item(key = "busy") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("در حال پیدا کردن غذا برای تو…", fontSize = 13.sp)
                    }
                }
            }
        }

        // new-suggestion button, fixed above the composer
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            GradientButton(meal.newLabel, enabled = !busy) {
                send(Prompts.mealSuggestPrompt(meal.title.removePrefix("پیشنهاد "), 4, emptyList()))
            }
        }

        // composer — pinned above the keyboard
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("سؤالت را بنویس…", fontSize = 13.sp) },
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (input.isNotBlank() && !busy)
                            Brush.linearGradient(listOf(Purple, meal.accent))
                        else
                            Brush.linearGradient(
                                listOf(Color.Gray.copy(alpha = .35f), Color.Gray.copy(alpha = .35f))
                            )
                    )
                    .clickable(enabled = input.isNotBlank() && !busy) {
                        val t = input.trim()
                        input = ""
                        send(t)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Send, contentDescription = "ارسال", tint = Color.White)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("پاک کردن گفتگو", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("آیا مطمئنی می‌خواهی گفتگوی «${meal.title}» پاک شود؟", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(meal.channel); confirmClear = false }) {
                    Text("پاک کردن", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun ChatOverflowMenu(onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "گزینه‌ها")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("پاک کردن گفتگو", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = { open = false; onClear() }
            )
        }
    }
}

@Composable
private fun UserBubble(m: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Purple.copy(alpha = .14f))
                .border(1.dp, Purple.copy(alpha = .35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(m.content, fontSize = 13.sp)
        }
    }
}


// ---------------------------------------------------------------- cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(
    r: Recipe,
    accent: Color,
    busy: Boolean,
    onSave: () -> Unit
) {
    DastyarCard(accent = accent) {
        Text("🍲 ${r.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 3)

        if (r.intro.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                r.intro,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (r.servings.isNotBlank()) FactPill("👥", r.servings)
            if (r.prep.isNotBlank()) FactPill("⏱", "آماده‌سازی ${r.prep}")
            if (r.cook.isNotBlank()) FactPill("🔥", "پخت ${r.cook}")
        }

        if (r.ingredients.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("🧺 مواد لازم", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Spacer(Modifier.height(6.dp))
            r.ingredients.forEach { (name, amount) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("• $name", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (amount.isNotBlank()) {
                        Text(amount, fontSize = 13.sp, color = accent, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (r.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("👩‍🍳 طرز تهیه", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            Spacer(Modifier.height(6.dp))
            r.steps.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("${com.dastyar.app.data.Dates.fa(i + 1)}.", fontSize = 13.sp, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text(s, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        r.shortage?.let {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber.copy(alpha = .12f))
                    .border(1.dp, Amber.copy(alpha = .35f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text("🧂 کمبود: $it", fontSize = 12.sp)
            }
        }

        if (r.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("💡 نکات مهم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(r.notes, fontSize = 13.sp)
        }

        if (r.alternatives.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("🔁 جایگزین مواد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(r.alternatives, fontSize = 13.sp)
        }

        // A recipe that lost its ingredients or steps is never shown as if it
        // were whole: the user is told and can ask for it again.
        if (!r.isComplete) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f))
                    .padding(10.dp)
            ) {
                Text(
                    "⚠️ این پیشنهاد کامل دریافت نشد. لطفاً «پیشنهاد جدید» را بزن.",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("♡ ذخیره", fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun FactPill(emoji: String, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SavedRecipeCard(r: SavedRecipe, onDelete: () -> Unit) {
    val parsed = remember(r.body) { parseRecipes(r.body).firstOrNull() }
    DastyarCard(accent = Purple) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text(r.meal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف", modifier = Modifier.size(17.dp))
            }
        }

        val d = parsed
        if (d != null) {
            if (d.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "مواد: " + d.ingredients.joinToString("، ") { "${it.first} ${it.second}".trim() },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (d.steps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                d.steps.forEachIndexed { i, s ->
                    Text(
                        "${com.dastyar.app.data.Dates.fa(i + 1)}. $s",
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        } else if (r.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(r.body, fontSize = 12.5.sp)
        }
    }
}

// --------------------------------------------------------------- parsing

/** One parsed recipe block from the model's structured answer. */
private data class Recipe(
    val name: String,
    val intro: String = "",
    val servings: String = "",
    val prep: String = "",
    val cook: String = "",
    val ingredients: List<Pair<String, String>> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String = "",
    val alternatives: String = "",
    val shortage: String? = null,
    val raw: String = ""
) {
    /** A recipe is shown as complete only when it has ingredients and steps. */
    val isComplete: Boolean get() = ingredients.isNotEmpty() && steps.isNotEmpty()
}

/**
 * Parses the marker-based answer into recipe blocks. It is tolerant: anything
 * that does not match the expected keys is kept as-is in [Recipe.raw] so the
 * user still sees the model's text instead of an empty card.
 */
private fun parseRecipes(text: String): List<Recipe> {
    val blocks = text.split("###")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val out = mutableListOf<Recipe>()
    blocks.forEach { block ->
        var name = ""
        var intro = ""
        var servings = ""
        var prep = ""
        var cook = ""
        val ings = mutableListOf<Pair<String, String>>()
        val steps = mutableListOf<String>()
        var notes = ""
        var alternatives = ""
        var shortage: String? = null
        var section = ""   // "ing" while reading ingredients, "steps" while reading steps
        var sawName = false

        block.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("نام:") || line.startsWith("نام :") -> {
                    name = line.substringAfter(":").trim(); sawName = true
                }
                line.startsWith("معرفی:") -> intro = line.substringAfter(":").trim()
                line.startsWith("نفرات:") -> servings = line.substringAfter(":").trim()
                line.startsWith("آماده‌سازی:") || line.startsWith("آماده سازی:") ->
                    prep = line.substringAfter(":").trim()
                line.startsWith("پخت:") -> cook = line.substringAfter(":").trim()
                line.startsWith("کمبود:") -> shortage = line.substringAfter(":").trim()
                line.startsWith("نکات:") || line.startsWith("نکته:") ->
                    notes = line.substringAfter(":").trim()
                line.startsWith("جایگزین:") -> alternatives = line.substringAfter(":").trim()
                line.startsWith("مواد:") -> section = "ing"
                line.startsWith("مراحل:") -> section = "steps"
                else -> {
                    val body = line.removePrefix("-").removePrefix("•").trim()
                    if (body.isNotEmpty()) {
                        when {
                            section == "ing" -> {
                                val sep = body.indexOfFirst { it == ':' || it == '：' }
                                if (sep > 0) {
                                    ings += body.substring(0, sep).trim() to
                                            body.substring(sep + 1).trim()
                                } else {
                                    ings += body to ""
                                }
                            }
                            section == "steps" -> {
                                val clean = body.replace(Regex("^[۰-۹0-9]+[.٫)]\\s*"), "").trim()
                                if (clean.isNotEmpty()) steps += clean
                            }
                            else -> {
                                // No section marker yet. This is only trustworthy
                                // when the block opened with an explicit «نام:».
                                // Otherwise it is model prose (an echoed prompt or
                                // an "I am a chef..." preamble) and must be
                                // ignored, never turned into a dish name.
                                if (name.isBlank() && sawName) name = body.removePrefix("🍽").trim()
                            }
                        }
                    }
                }
            }
        }

        // Only a well-formed recipe is accepted: it must name the dish AND list
        // either ingredients or steps. Placeholder echoes such as «نام: ...»
        // from the model repeating the template are rejected too, so no prompt
        // text can ever reach the UI.
        val placeholder = name.replace(".", "").replace("…", "").trim().length < 2 ||
                name == "..." || name == "…"
        if (sawName && name.isNotBlank() && !placeholder && (ings.isNotEmpty() || steps.isNotEmpty())) {
            out += Recipe(
                name = name.ifBlank { "غذای پیشنهادی" },
                intro = intro,
                servings = servings,
                prep = prep,
                cook = cook,
                ingredients = ings,
                steps = steps,
                notes = notes,
                alternatives = alternatives,
                shortage = shortage,
                raw = block
            )
        }
    }
    return out
}

