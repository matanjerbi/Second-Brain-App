@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.matan.secondbrain.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.matan.secondbrain.data.Item
import com.matan.secondbrain.data.ItemType
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.ui.theme.Palette
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ItemScreen(repo: Repository, id: Long, onBack: () -> Unit, onOpen: (Long) -> Unit, onTag: (String) -> Unit) {
    val item by repo.observe(id).collectAsStateWithLifecycle(initialValue = null)
    val hasKey by repo.settings.hasKey.collectAsStateWithLifecycle()
    val indexVersion by repo.indexVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var enriching by remember { mutableStateOf(false) }

    val current = item
    Scaffold(containerColor = Palette.Ground) { pad ->
        if (current == null) return@Scaffold
        val st = typeStyle(current)
        val related = remember(current.id, indexVersion) { repo.engine.related(current) }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, contentDescription = "עריכה", tint = Palette.Muted) }
                    IconButton(onClick = { copy(context, shareText(current)) }) { Icon(Icons.Filled.ContentCopy, contentDescription = "העתק", tint = Palette.Muted) }
                    IconButton(onClick = { share(context, current) }) { Icon(Icons.Filled.Share, contentDescription = "שתף", tint = Palette.Muted) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "מחק", tint = Palette.Muted) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOfNotNull(st.label, current.tagList().firstOrNull()).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = st.fg)
                Text(current.displayTitle(), style = MaterialTheme.typography.titleLarge)
                Text(
                    "נשמר " + SimpleDateFormat("d.M.yyyy HH:mm", Locale("he")).format(Date(current.createdAt)) +
                        if (current.source == "whatsapp") " · מוואטסאפ" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Muted,
                )
            }

            current.imagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = current.displayTitle(),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            current.audioPath?.let { AudioPlayer(it) }

            if (current.summary.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.LimeDeep).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Palette.Lime)
                    Text(current.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (current.text.isNotBlank()) {
                SelectionContainer {
                    Text(
                        current.text,
                        style = MaterialTheme.typography.bodyLarge.let {
                            if (current.type == ItemType.CODE) it.copy(fontFamily = FontFamily.Monospace, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr) else it
                        },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(14.dp),
                    )
                }
            }
            if (current.userNote.isNotBlank() && current.userNote != current.text) {
                Text("הערה: ${current.userNote}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.url?.let { url ->
                    Button(
                        onClick = { open(context, Uri.parse(url)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
                    ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null); Text("  פתח קישור") }
                }
                if (current.type == ItemType.PHONE) {
                    Button(
                        onClick = { open(context, Uri.parse("tel:" + current.text.filter { it.isDigit() || it == '+' })) },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
                    ) { Icon(Icons.Filled.Phone, contentDescription = null); Text("  התקשר") }
                }
            }

            if (current.ocrText.isNotBlank() && current.type == ItemType.IMAGE) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("טקסט שזוהה בתמונה", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    SelectionContainer {
                        Text(current.ocrText, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(14.dp))
                    }
                }
            }

            if (current.tagList().isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.tagList().forEach { t -> TagChip(t, selected = true) { onTag(t) } }
                }
            }

            if (hasKey && !current.sensitive && current.enrichedAt == null) {
                OutlinedButton(onClick = {
                    enriching = true
                    scope.launch {
                        runCatching { repo.enrichOne(current) }.onFailure {
                            Toast.makeText(context, it.message ?: "שגיאה", Toast.LENGTH_LONG).show()
                        }
                        enriching = false
                    }
                }, enabled = !enriching) { Text(if (enriching) "מעשיר…" else "העשר עם AI") }
            }
            if (current.sensitive) {
                Text("פריט רגיש: נשמר בכספת ולא נשלח ל-AI.", style = MaterialTheme.typography.labelMedium, color = Palette.Red)
            }

            if (related.isNotEmpty()) {
                Text("קשור לזה בזיכרון שלך", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                related.forEach { r -> ItemCard(r, onClick = { onOpen(r.id) }) }
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את הפריט?") },
            text = { Text("אי אפשר לשחזר אותו אחרי המחיקה.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { repo.delete(current); onBack() }
                }) { Text("מחק", color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
    if (editing && current != null) {
        EditDialog(current, onDismiss = { editing = false }) { updated ->
            editing = false
            scope.launch { repo.update(updated) }
        }
    }
}

@Composable
private fun EditDialog(item: Item, onDismiss: () -> Unit, onSave: (Item) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var text by remember { mutableStateOf(item.text) }
    var tags by remember { mutableStateOf(item.tagList().joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עריכה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("כותרת") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("טקסט") }, minLines = 3, maxLines = 8)
                OutlinedTextField(tags, { tags = it }, label = { Text("תגיות (מופרדות בפסיק)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(item.copy(title = title.trim(), text = text, tags = Item.joinList(tags.split(','))))
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun AudioPlayer(path: String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release(); player = null } }
    OutlinedButton(onClick = {
        val p = player
        if (p != null) {
            p.release(); player = null
        } else {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener { it.release(); player = null }
                    prepare(); start()
                }
            }.onSuccess { player = it }
        }
    }) {
        Icon(if (player != null) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
        Text(if (player != null) "  עצור" else "  נגן הקלטה")
    }
}

private fun shareText(item: Item): String = listOf(item.title, item.text, item.url ?: "").filter { it.isNotBlank() }.distinct().joinToString("\n")

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Second Brain", text))
    Toast.makeText(context, "הועתק", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, item: Item) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(item))
    context.startActivity(Intent.createChooser(intent, "שתף"))
}

private fun open(context: Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "אין אפליקציה שיכולה לפתוח את זה", Toast.LENGTH_SHORT).show() }
}

