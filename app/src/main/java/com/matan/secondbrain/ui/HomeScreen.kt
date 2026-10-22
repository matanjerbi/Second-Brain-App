@file:OptIn(ExperimentalMaterial3Api::class)

package com.matan.secondbrain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repo: Repository,
    onSearch: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onCapture: () -> Unit,
    onSettings: () -> Unit,
    onVault: () -> Unit,
) {
    val items by repo.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val pending by repo.pendingCount.collectAsStateWithLifecycle(initialValue = 0)
    val hasKey by repo.settings.hasKey.collectAsStateWithLifecycle()
    val enrich by repo.enrichState.collectAsStateWithLifecycle()
    val indexVersion by repo.indexVersion.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val voice = rememberVoiceInput { onSearch(it) }

    val visible = remember(items) { items.filter { !it.sensitive } }
    val tags = remember(indexVersion) { repo.engine.tagCounts().take(12) }
    val vaultCount = items.size - visible.size

    Scaffold(
        containerColor = Palette.Ground,
        floatingActionButton = {
            FloatingActionButton(onClick = onCapture, containerColor = Palette.Lime, contentColor = Palette.Ground, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "הוסף פריט")
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("הזיכרון שלי", style = MaterialTheme.typography.headlineMedium)
                        Text("${visible.size} פריטים", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    }
                    if (vaultCount > 0) {
                        IconButton(onClick = onVault) { Icon(Icons.Filled.Lock, contentDescription = "כספת", tint = Palette.Muted) }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "הגדרות", tint = Palette.Muted) }
                }
            }
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().border(1.dp, Palette.Line, RoundedCornerShape(16.dp)),
                    placeholder = { Text("שאל אותי כל דבר…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Lime) },
                    trailingIcon = {
                        IconButton(onClick = voice) { Icon(Icons.Filled.Mic, contentDescription = "חיפוש קולי", tint = Palette.Lime) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Palette.Surface,
                        unfocusedContainerColor = Palette.Surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Palette.Lime,
                    ),
                )
            }
            if (tags.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags) { (tag, count) -> TagChip("$tag · $count") { onSearch("#$tag") } }
                    }
                }
            }
            if (hasKey && (pending > 0 || enrich.running)) {
                item {
                    Banner(
                        text = if (enrich.running) "מעשיר פריטים לחיפוש לפי משמעות… ${enrich.done}/${enrich.total}"
                        else "$pending פריטים עוד לא עברו העשרה. הקש כדי להבין אותם לפי משמעות.",
                        onClick = if (enrich.running) null else {
                            { scope.launch { repo.enrichPending() }; Unit }
                        },
                    )
                }
            } else if (!hasKey && items.isNotEmpty()) {
                item {
                    Banner(
                        text = "הוסף מפתח API בהגדרות כדי לקבל תשובות במשפט ולחפש לפי משמעות.",
                        onClick = onSettings,
                    )
                }
            }
            if (visible.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("עוד אין כאן כלום", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "שתף לכאן הודעה, קישור או צילום מסך מכל אפליקציה, או ייבא את הצ'אט מוואטסאפ בהגדרות.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted,
                        )
                    }
                }
            } else {
                item { Text("נשמר לאחרונה", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp)) }
                items(visible.take(200), key = { it.id }) { item -> ItemCard(item, onClick = { onOpen(item.id) }) }
            }
        }
    }
}

@Composable
fun Banner(text: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.LimeDeep)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Palette.Lime)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
    }
}
