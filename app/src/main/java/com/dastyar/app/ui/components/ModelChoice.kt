package com.dastyar.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dastyar.app.ai.AiProvider
import com.dastyar.app.ai.ServiceKeys

/**
 * The tiny model chooser shown inside the chat input box (a small caption under
 * the field, like professional chat clients).
 *
 * The caption always names the model the next message will use. Tapping it opens
 * a menu that lists every model of every text provider the user has actually
 * configured, grouped under a provider heading (1xAI, Groq, ...). Choosing one
 * applies to this conversation only; it is remembered per channel and the next
 * message is really sent to that provider + model, without opening Settings.
 *
 * When no provider is ready yet, the app's current provider is shown so the
 * caption still tells the truth.
 */
@Composable
fun ChatModelCaption(
    channel: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }

    // Re-reads after a choice; provider readiness is read fresh each composition.
    val groups: List<Pair<AiProvider, List<Pair<String, String>>>> = remember(version) {
        val ready = ServiceKeys.readyTextProviders()
            .ifEmpty { listOf(ServiceKeys.textProvider()) }
        ready.map { it to it.pickerModels }
    }
    val choice = remember(version) { ServiceKeys.conversationChoice(channel) }
    val activeProvider = ServiceKeys.textProvider()
    val currentProvider = choice?.first ?: activeProvider
    val currentModel = choice?.second
        ?: ServiceKeys.modelFor(activeProvider).ifBlank { activeProvider.textModel }
    val label = groups.asSequence()
        .flatMap { it.second.asSequence() }
        .firstOrNull { it.first == currentModel }
        ?.second
        ?: currentModel

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "مدل:",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            Row(
                Modifier
                    .clickable { open = true }
                    .padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
                Text("⌄", fontSize = 10.sp, color = accent)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.widthIn(min = 230.dp, max = 320.dp)
            ) {
                groups.forEachIndexed { gi, (provider, models) ->
                    if (gi > 0) HorizontalDivider()
                    DropdownMenuItem(
                        enabled = false,
                        text = {
                            Text(
                                provider.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {}
                    )
                    models.forEach { (id, text) ->
                        val isCurrent = provider.id == currentProvider.id && id == currentModel
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        (if (isCurrent) "✓ " else "") + text,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                ServiceKeys.setConversationChoice(ctx, channel, provider, id)
                                version++
                                open = false
                            }
                        )
                    }
                }
            }
        }
    }
}
