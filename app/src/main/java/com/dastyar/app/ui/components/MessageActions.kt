package com.dastyar.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Copies the whole message text to the system clipboard and confirms with a
 * short «کپی شد». The text is taken verbatim, so Persian, right-to-left text,
 * lists, paragraphs and multi-line content survive untouched.
 */
fun copyMessageText(context: Context, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("دستیار من", text))
    Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
}

/**
 * Wraps a chat bubble so a long-press opens that message's own menu.
 *
 * The menu always offers «کپی متن», which copies the entire stored message. A
 * message that has no text (an image or other non-text content) shows the action
 * disabled instead of pretending something was copied. [onDelete], when given,
 * adds a «حذف پیام» item; the caller owns the confirmation dialog.
 *
 * The bubble's look (shape, background, border and padding) is passed through
 * [modifier] so wrapping a bubble never changes how it renders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageActionBox(
    text: String,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val hasText = text.isNotBlank()
    Box {
        Box(
            modifier.combinedClickable(
                onClick = {},
                onLongClick = { open = true }
            )
        ) { content() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                enabled = hasText,
                text = { Text("کپی متن", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    open = false
                    copyMessageText(context, text)
                }
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("حذف پیام", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        open = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/** A small copy button for content that is laid out as a card, not a bubble. */
@Composable
fun CopyMessageButton(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasText = text.isNotBlank()
    Box(
        modifier
            .size(32.dp)
            .combinedClickable(enabled = hasText, onClick = { copyMessageText(context, text) }),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = "کپی متن",
            modifier = Modifier.size(17.dp)
        )
    }
}
