package com.twinglish.keyboard.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationStyle

private val Context.dataStore by preferencesDataStore(name = "twinglish_settings")

/**
 * Persists [Settings] in DataStore preferences and exposes them as a
 * [StateFlow]. All writes go through [update].
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            // A DataStore read/write failure must never take the IME process down.
            Log.e("TwinglishSettings", "DataStore failure", throwable)
        }
    )

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val KEY_HEIGHT = intPreferencesKey("keyboard_height_percent")
        val KEY_BORDERS = booleanPreferencesKey("key_borders")
        val KEY_SOUND = booleanPreferencesKey("key_press_sound")
        val HAPTIC = stringPreferencesKey("haptic_mode")
        val POPUP_PREVIEW = booleanPreferencesKey("popup_preview")
        val AUTO_CAP = booleanPreferencesKey("auto_capitalization")
        val KEY_PREVIEW = booleanPreferencesKey("key_preview")
        val TWINGLISH_ENABLED = booleanPreferencesKey("twinglish_enabled")
        val AUTO_SUGGEST = booleanPreferencesKey("auto_suggest_twinglish")
        val STYLE = stringPreferencesKey("translation_style")
        val ROMAN_STYLE = stringPreferencesKey("romanization_style")
        val ONLINE = booleanPreferencesKey("online_translation")
        val NETWORK_USAGE = booleanPreferencesKey("network_usage")
    }

    private val settingsState: kotlinx.coroutines.flow.StateFlow<Settings> = dataStore.data.map { p ->
        Settings(
            theme = KeyboardTheme.fromId(p[Keys.THEME]),
            keyboardHeightPercent = p[Keys.KEY_HEIGHT] ?: 100,
            keyBorders = p[Keys.KEY_BORDERS] ?: true,
            keyPressSound = p[Keys.KEY_SOUND] ?: false,
            hapticMode = HapticMode.fromId(p[Keys.HAPTIC]),
            popupPreview = p[Keys.POPUP_PREVIEW] ?: true,
            autoCapitalization = p[Keys.AUTO_CAP] ?: true,
            keyPreviewOn = p[Keys.KEY_PREVIEW] ?: true,
            twinglishEnabled = p[Keys.TWINGLISH_ENABLED] ?: true,
            autoSuggestTwinglish = p[Keys.AUTO_SUGGEST] ?: true,
            translationStyle = TranslationStyle.fromId(p[Keys.STYLE]),
            romanizationStyle = RomanizationStyle.fromId(p[Keys.ROMAN_STYLE]),
            onlineTranslationEnabled = p[Keys.ONLINE] ?: false,
            networkUsage = p[Keys.NETWORK_USAGE] ?: false,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    val settings: Flow<Settings> get() = settingsState

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { p ->
            val s = transform(settingsState.value)
            p[Keys.THEME] = s.theme.id
            p[Keys.KEY_HEIGHT] = s.keyboardHeightPercent
            p[Keys.KEY_BORDERS] = s.keyBorders
            p[Keys.KEY_SOUND] = s.keyPressSound
            p[Keys.HAPTIC] = s.hapticMode.id
            p[Keys.POPUP_PREVIEW] = s.popupPreview
            p[Keys.AUTO_CAP] = s.autoCapitalization
            p[Keys.KEY_PREVIEW] = s.keyPreviewOn
            p[Keys.TWINGLISH_ENABLED] = s.twinglishEnabled
            p[Keys.AUTO_SUGGEST] = s.autoSuggestTwinglish
            p[Keys.STYLE] = s.translationStyle.id
            p[Keys.ROMAN_STYLE] = s.romanizationStyle.id
            p[Keys.ONLINE] = s.onlineTranslationEnabled
            p[Keys.NETWORK_USAGE] = s.networkUsage
        }
    }

    fun updateAsync(transform: (Settings) -> Settings) {
        scope.launch { update(transform) }
    }
}
