package com.twinglish.keyboard

import android.app.Application
import android.util.Log
import com.twinglish.keyboard.data.SettingsRepository
import com.twinglish.keyboard.engine.TwinglishEngine
import com.twinglish.keyboard.engine.personalization.LearningEngine
import com.twinglish.keyboard.engine.translation.GoogleTranslationProvider
import com.twinglish.keyboard.engine.translation.OfflineTranslationProvider
import com.twinglish.keyboard.engine.personalization.LearningFlags
import com.twinglish.keyboard.engine.personalization.LocalKnowledgeStore
import com.twinglish.keyboard.engine.personalization.PersonalizationEngine
import com.twinglish.keyboard.engine.personalization.PersonalizedRanker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide singletons: the Twinglish engine (shared, cached) and the
 * DataStore-backed settings repository. Kept intentionally small — no DI
 * framework needed for a keyboard.
 *
 * The scope uses [CoroutineExceptionHandler] so a failure inside any
 * background job (settings collection, translation) can never take the
 * whole IME process down silently — it is logged instead.
 */
class TwinglishApplication : Application() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Background coroutine failed", throwable)
    }

    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler
    )

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    /**
     * The translation engine. Offline-first: the curated phrase bank answers
     * instantly, and Google Translate (keyless endpoint) handles sentences
     * the bank can't translate — but only when the user enables "Online
     * translation" in Settings. Secure fields never reach it (the IME only
     * translates plain-text fields).
     */
    val twinglishEngine: TwinglishEngine by lazy {
        TwinglishEngine(
            provider = GoogleTranslationProvider(
                offline = OfflineTranslationProvider(),
                onlineEnabled = { settingsRepository.settings.value.onlineTranslationEnabled },
            )
        )
    }

    /**
     * Local, privacy-first knowledge store: the bounded translation cache
     * plus everything the keyboard has learned. Persisted as a file in the
     * app's private files dir — nothing ever leaves the device.
     */
    val knowledgeStore: LocalKnowledgeStore by lazy {
        LocalKnowledgeStore(path = File(filesDir, "twinglish_knowledge.data").absolutePath)
    }

    val personalizationEngine: PersonalizationEngine by lazy {
        PersonalizationEngine(
            engine = twinglishEngine,
            store = knowledgeStore,
            learning = LearningEngine(knowledgeStore, flagsProvider = { learningFlags }),
            ranker = PersonalizedRanker(),
            flagsProvider = { learningFlags },
        )
    }

    /** Live learning switches read from Settings. */
    private val learningFlags: LearningFlags
        get() {
            val s = settingsRepository.settings.value
            return LearningFlags(
                enabled = s.personalizationEnabled,
                corrections = s.learnCorrections,
                vocabulary = s.learnVocabulary,
                personalizedSuggestions = s.personalizedSuggestions,
            )
        }

    override fun onCreate() {
        super.onCreate()
        installCrashLogging()
    }

    /**
     * Last-resort crash capture: writes any uncaught exception to a file the
     * user can reach (external files dir → visible over USB), logs it under
     * the [TAG] tag, and — most importantly — opens [CrashReportActivity]
     * so the exact stack trace is visible on screen and copyable instead of
     * just a "keeps stopping" dialog. The previous handler still runs so
     * the platform crash reporting behaves normally.
     */
    private fun installCrashLogging() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = getExternalFilesDir(null) ?: filesDir
                val log = File(dir, "crash.log")
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                val entry = buildString {
                    append("=== ")
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine(" ===")
                    appendLine("Thread: ${thread.name}")
                    appendLine(trace)
                    appendLine()
                }
                log.appendText(entry)
                Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)

                // Surface the trace on screen so it can be copied to the chat.
                val intent = android.content.Intent(this, CrashReportActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(CrashReportActivity.EXTRA_TRACE, trace)
                startActivity(intent)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "TwinglishCrash"

        fun from(context: android.content.Context): TwinglishApplication =
            context.applicationContext as TwinglishApplication
    }
}
