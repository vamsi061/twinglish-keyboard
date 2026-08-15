package com.twinglish.keyboard

import android.app.Application
import com.twinglish.keyboard.data.SettingsRepository
import com.twinglish.keyboard.engine.TwinglishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide singletons: the Twinglish engine (shared, cached) and the
 * DataStore-backed settings repository. Kept intentionally small — no DI
 * framework needed for a keyboard.
 */
class TwinglishApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val twinglishEngine: TwinglishEngine by lazy { TwinglishEngine() }

    companion object {
        fun from(context: android.content.Context): TwinglishApplication =
            context.applicationContext as TwinglishApplication
    }
}
