package com.twinglish.keyboard.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.twinglish.keyboard.TwinglishApplication
import com.twinglish.keyboard.data.HapticMode
import com.twinglish.keyboard.data.KeyboardTheme
import com.twinglish.keyboard.data.Settings
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationStyle

class SettingsActivity : ComponentActivity() {

    private val repository by lazy { TwinglishApplication.from(this).settingsRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { SettingsAppBar() },
                ) { padding ->
                    SettingsScreen(
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsAppBar() {
        TopAppBar(title = { Text("Twinglish Settings") })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(modifier: Modifier = Modifier) {
        val settings by repository.settings.collectAsState(initial = Settings())
        val update = repository::updateAsync
        var learnedRefresh by remember { mutableStateOf(0) }

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("Keyboard")
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(12.dp)) {
                    LabeledSwitch(
                        title = "Key borders",
                        subtitle = "Show separators between keys",
                        checked = settings.keyBorders,
                        onCheckedChange = { value -> update { it.copy(keyBorders = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Key press sound",
                        subtitle = "Play system click sound (respects system volume)",
                        checked = settings.keyPressSound,
                        onCheckedChange = { value -> update { it.copy(keyPressSound = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Popup preview",
                        subtitle = "Show enlarged key preview while typing",
                        checked = settings.popupPreview,
                        onCheckedChange = { value -> update { it.copy(popupPreview = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Auto-capitalization",
                        subtitle = "Capitalize the first letter of sentences",
                        checked = settings.autoCapitalization,
                        onCheckedChange = { value -> update { it.copy(autoCapitalization = value) } },
                    )
                    HorizontalDivider()
                    ChoiceRow(
                        title = "Theme",
                        options = KeyboardTheme.entries.map { it.id },
                        labels = mapOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                        selected = settings.theme.id,
                        onSelect = { value -> update { it.copy(theme = KeyboardTheme.fromId(value)) } },
                    )
                    HorizontalDivider()
                    ChoiceRow(
                        title = "Haptics",
                        options = HapticMode.entries.map { it.id },
                        labels = mapOf("off" to "Off", "light" to "Light", "medium" to "Medium"),
                        selected = settings.hapticMode.id,
                        onSelect = { value -> update { it.copy(hapticMode = HapticMode.fromId(value)) } },
                    )
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Keyboard height", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${settings.keyboardHeightPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Slider(
                            value = settings.keyboardHeightPercent.toFloat(),
                            onValueChange = { value -> update { it.copy(keyboardHeightPercent = value.toInt()) } },
                            valueRange = 50f..150f,
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Twinglish")
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(12.dp)) {
                    LabeledSwitch(
                        title = "Enable Twinglish",
                        subtitle = "Translate English to Telugu Twinglish",
                        checked = settings.twinglishEnabled,
                        onCheckedChange = { value -> update { it.copy(twinglishEnabled = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Auto suggest Twinglish",
                        subtitle = "Show Twinglish suggestions while typing (you still tap to apply)",
                        checked = settings.autoSuggestTwinglish,
                        onCheckedChange = { value -> update { it.copy(autoSuggestTwinglish = value) } },
                    )
                    HorizontalDivider()
                    ChoiceRow(
                        title = "Translation style",
                        options = TranslationStyle.entries.map { it.id },
                        labels = TranslationStyle.entries.associate { it.id to it.label },
                        selected = settings.translationStyle.id,
                        onSelect = { value -> update { it.copy(translationStyle = TranslationStyle.fromId(value)) } },
                    )
                    HorizontalDivider()
                    ChoiceRow(
                        title = "Romanization style",
                        options = RomanizationStyle.entries.map { it.id },
                        labels = RomanizationStyle.entries.associate { it.id to it.label },
                        selected = settings.romanizationStyle.id,
                        onSelect = { value -> update { it.copy(romanizationStyle = RomanizationStyle.fromId(value)) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Online translation",
                        subtitle = "Use a network translation provider when available (offline rules are used otherwise)",
                        checked = settings.onlineTranslationEnabled,
                        onCheckedChange = { value -> update { it.copy(onlineTranslationEnabled = value) } },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Personalization")
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Everything below is learned and stored only on this device. " +
                            "Nothing is uploaded, and nothing is ever learned from passwords or " +
                            "other secure fields.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LabeledSwitch(
                        title = "Learn my Twinglish preferences",
                        subtitle = "Remember accepted translations, corrections and style over time",
                        checked = settings.personalizationEnabled,
                        onCheckedChange = { value -> update { it.copy(personalizationEnabled = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Learn corrections",
                        subtitle = "\"movie\" → \"sinima\" style edits, after you make them repeatedly",
                        checked = settings.learnCorrections,
                        onCheckedChange = { value -> update { it.copy(learnCorrections = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Personalized suggestions",
                        subtitle = "Re-rank suggestions using what was learned",
                        checked = settings.personalizedSuggestions,
                        onCheckedChange = { value -> update { it.copy(personalizedSuggestions = value) } },
                    )
                    HorizontalDivider()
                    LabeledSwitch(
                        title = "Learn vocabulary",
                        subtitle = "Remember English words you keep (office, call, bro …)",
                        checked = settings.learnVocabulary,
                        onCheckedChange = { value -> update { it.copy(learnVocabulary = value) } },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    WhatTwinglishLearned(refreshKey = learnedRefresh)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            TwinglishApplication.from(this@SettingsActivity).personalizationEngine.clearAllData()
                            learnedRefresh++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear Learned Data")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            TwinglishApplication.from(this@SettingsActivity).personalizationEngine.resetPreferences()
                            learnedRefresh++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset Twinglish Preferences")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Privacy")
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Your typing stays on this device. Twinglish translation runs offline with the " +
                            "built-in rule engine; nothing you type is sent to a server. Passwords and other " +
                            "secure fields are never processed. No analytics, no ads, no telemetry.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(
                        "Network usage: ${if (settings.networkUsage) "in use" else "none"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("About")
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(12.dp)) {
                    val version = runCatching {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    }.getOrNull() ?: "1.0.0"
                    Text("Twinglish Keyboard", style = MaterialTheme.typography.bodyLarge)
                    Text("Version $version", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "English → natural Telugu → Twinglish. Open source, built for Telugu speakers.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    /** Explainable learning: what the keyboard has learned about this user. */
    @Composable
    private fun WhatTwinglishLearned(refreshKey: Int) {
        var lines by remember { mutableStateOf<List<String>>(emptyList()) }
        LaunchedEffect(refreshKey) {
            lines = runCatching {
                TwinglishApplication.from(this@SettingsActivity).personalizationEngine.learnedInfo()
            }.getOrDefault(emptyList())
        }
        Text(
            text = "What Twinglish learned",
            style = MaterialTheme.typography.titleSmall,
        )
        if (lines.isEmpty()) {
            Text(
                "Nothing learned yet — the more you use Twinglish, the more it " +
                    "adapts to your wording.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            lines.forEach { line ->
                Text(
                    "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    @Composable
    private fun LabeledSwitch(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    private fun ChoiceRow(
        title: String,
        options: List<String>,
        labels: Map<String, String>,
        selected: String,
        onSelect: (String) -> Unit,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                        Text(labels[option] ?: option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
