package com.matan.secondbrain.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matan.secondbrain.data.Item
import com.matan.secondbrain.data.ItemType
import com.matan.secondbrain.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TypeStyle(val icon: ImageVector, val fg: Color, val bg: Color, val label: String)

fun typeStyle(item: Item): TypeStyle = when {
    item.sensitive -> TypeStyle(Icons.Filled.Lock, Palette.Red, Color(0xFF2C1A1A), "כספת")
    else -> when (item.type) {
        ItemType.LINK -> TypeStyle(Icons.Filled.Link, Palette.Amber, Palette.AmberDeep, "קישור")
        ItemType.IMAGE -> TypeStyle(Icons.Filled.Image, Palette.Blue, Palette.BlueDeep, "צילום מסך")
        ItemType.AUDIO -> TypeStyle(Icons.Filled.GraphicEq, Palette.Pink, Palette.PinkDeep, "הקלטה")
        ItemType.PHONE -> TypeStyle(Icons.Filled.Phone, Palette.Lime, Palette.LimeChip, "טלפון")
        ItemType.CODE -> TypeStyle(Icons.Filled.Code, Palette.Blue, Palette.BlueDeep, "קוד")
        else -> TypeStyle(Icons.Filled.Description, Palette.Lime, Palette.LimeChip, "טקסט")
    }
}

fun relativeDate(at: Long): String {
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "היום"
        days == 1 -> "אתמול"
        days < 7 -> "לפני $days ימים"
        days < 14 -> "לפני שבוע"
        days < 31 -> "לפני ${days / 7} שבועות"
        else -> SimpleDateFormat("d.M.yyyy", Locale("he")).format(Date(at))
    }
}

@Composable
fun ItemCard(item: Item, onClick: () -> Unit, highlight: Boolean = false) {
    val st = typeStyle(item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) Palette.LimeDeep else Palette.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(st.bg),
            contentAlignment = Alignment.Center,
        ) { Icon(st.icon, contentDescription = null, tint = st.fg) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (item.sensitive) "פריט נעול" else item.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(st.label, item.tagList().firstOrNull(), relativeDate(item.createdAt)).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1)
        }
    }
}

@Composable
fun TagChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val m = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(if (selected) Palette.LimeChip else Palette.Surface)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 12.dp, vertical = 8.dp)
    Text(
        text,
        modifier = m,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Palette.Lime else Color(0xFFC9CED6),
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
    )
}

/** Launches the system speech recognizer in Hebrew and returns the transcript. */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) onText(text)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "דבר עכשיו")
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "זיהוי דיבור לא זמין במכשיר", Toast.LENGTH_SHORT).show()
        }
    }
}
