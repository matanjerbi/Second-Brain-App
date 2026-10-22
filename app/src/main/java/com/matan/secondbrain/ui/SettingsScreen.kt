@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.matan.secondbrain.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.settings.SettingsStore
import com.matan.secondbrain.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: Repository, onBack: () -> Unit) {
    val s = repo.settings
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(s.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(s.model) }
    var auto by remember { mutableStateOf(s.autoEnrich) }
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Repository.ImportResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val enrich by repo.enrichState.collectAsStateWithLifecycle()
    val pending by repo.pendingCount.collectAsStateWithLifecycle(initialValue = 0)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                importResult = repo.importFile(uri)
                importing = false
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { message = "יוצאו ${repo.exportTo(uri)} פריטים" }
    }

    Scaffold(containerColor = Palette.Ground) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                Text("הגדרות", style = MaterialTheme.typography.titleLarge)
            }

            Section("ייבוא") {
                Text(
                    "בחר את קובץ הייצוא של וואטסאפ (ZIP או TXT), או קובץ JSON שהוכן מראש. כפילויות מדולגות, ופרטים רגישים עוברים לכספת.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                Button(
                    onClick = { importer.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
                ) { Text(if (importing) "מייבא…" else "ייבוא מוואטסאפ") }
                OutlinedButton(onClick = { exporter.launch("second-brain-backup.json") }) { Text("גיבוי לקובץ") }
            }

            Section("AI") {
                Text(
                    "מפתח API של Anthropic מאפשר תשובות במשפט, חיפוש לפי משמעות וקריאת טקסט בעברית מצילומי מסך. המפתח נשמר מוצפן בטלפון בלבד. פריטים מהכספת לא נשלחים אף פעם.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("מפתח API") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "הצג מפתח")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                )
                Text("מודל", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsStore.MODEL_CHOICES.forEach { m ->
                        TagChip(if (m.contains("haiku")) "Haiku · מהיר וזול" else "Sonnet · חכם יותר", selected = model == m) { model = m }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("העשרה אוטומטית של פריטים חדשים", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = auto, onCheckedChange = { auto = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Lime, checkedThumbColor = Palette.Ground))
                }
                Button(
                    onClick = {
                        s.apiKey = key
                        s.model = model
                        s.autoEnrich = auto
                        message = "נשמר"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
                ) { Text("שמור הגדרות") }

                if (enrich.running) {
                    Text("מעשיר ${enrich.done}/${enrich.total}", style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(
                        progress = { if (enrich.total == 0) 0f else enrich.done.toFloat() / enrich.total },
                        modifier = Modifier.fillMaxWidth(), color = Palette.Lime, trackColor = Palette.Line,
                    )
                } else {
                    OutlinedButton(
                        onClick = { scope.launch { repo.enrichPending() } },
                        enabled = key.isNotBlank() && pending > 0,
                    ) { Text("העשר עכשיו ($pending ממתינים)") }
                }
                enrich.error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Red) }
            }
        }
    }

    importResult?.let { r ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text(if (r.error == null) "הייבוא הושלם" else "הייבוא נכשל") },
            text = {
                Text(
                    r.error ?: buildString {
                        append("נוספו ${r.added} פריטים.")
                        if (r.duplicates > 0) append("\n${r.duplicates} כבר היו קיימים ודולגו.")
                        if (r.vault > 0) append("\n${r.vault} פריטים רגישים הועברו לכספת.")
                        if (r.media > 0) append("\n${r.media} הודעות מדיה לא נכללו בייצוא.")
                        if (s.hasKey.value && s.autoEnrich) append("\nההעשרה עם AI רצה ברקע.")
                    }
                )
            },
            confirmButton = { TextButton(onClick = { importResult = null }) { Text("סגור") } },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("סגור") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
