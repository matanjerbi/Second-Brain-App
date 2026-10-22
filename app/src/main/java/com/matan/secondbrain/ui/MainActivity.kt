package com.matan.secondbrain.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matan.secondbrain.SecondBrainApp
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.ui.theme.Palette
import com.matan.secondbrain.ui.theme.SbpTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as SecondBrainApp).repo
        setContent {
            SbpTheme { AppNav(repo, this) }
        }
    }
}

@Composable
private fun AppNav(repo: Repository, activity: FragmentActivity) {
    val nav = rememberNavController()
    val openItem: (Long) -> Unit = { nav.navigate("item/$it") }
    val search: (String) -> Unit = { nav.navigate("results?q=${Uri.encode(it)}") }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repo = repo,
                onSearch = search,
                onOpen = openItem,
                onCapture = { nav.navigate("capture") },
                onSettings = { nav.navigate("settings") },
                onVault = { nav.navigate("vault") },
            )
        }
        composable(
            "results?q={q}",
            arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            ResultsScreen(repo, entry.arguments?.getString("q").orEmpty(), onBack = { nav.popBackStack() }, onOpen = openItem)
        }
        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ItemScreen(
                repo = repo,
                id = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onOpen = openItem,
                onTag = { search("#$it") },
            )
        }
        composable("capture") { CaptureScreen(repo, onDone = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(repo, onBack = { nav.popBackStack() }) }
        composable("vault") { VaultScreen(repo, activity, onBack = { nav.popBackStack() }, onOpen = openItem) }
    }
}

@Composable
private fun VaultScreen(repo: Repository, activity: FragmentActivity, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val items by repo.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val vault = remember(items) { items.filter { it.sensitive } }

    fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock on the device: nothing to unlock with, so open directly.
            unlocked = true
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { failed = errString.toString() }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("פתיחת הכספת")
                .setSubtitle("פרטים רגישים: כרטיסים, תעודות וקודים")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    LaunchedEffect(Unit) { authenticate() }

    Scaffold(containerColor = Palette.Ground) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה") }
                    Column {
                        Text("כספת", style = MaterialTheme.typography.titleLarge)
                        Text("נשמר רק בטלפון ולא נשלח ל-AI", style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                    }
                }
            }
            if (!unlocked) {
                item {
                    Column(Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(failed ?: "הכספת נעולה", style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { failed = null; authenticate() },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
                        ) { Text("פתח") }
                    }
                }
            } else {
                items(vault, key = { it.id }) { item ->
                    // Unlocked: show the real title instead of the locked placeholder.
                    ItemCard(item.copy(sensitive = false), onClick = { onOpen(item.id) })
                }
            }
        }
    }
}
