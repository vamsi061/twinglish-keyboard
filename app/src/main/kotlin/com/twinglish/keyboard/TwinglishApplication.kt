package com.twinglish.keyboard

import android.app.Application
import android.util.Log
import com.twinglish.keyboard.data.SettingsRepository
import com.twinglish.keyboard.engine.TwinglishEngine
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

    val twinglishEngine: TwinglishEngine by lazy { TwinglishEngine() }

    override fun onCreate() {
        super.onCreate()
        installCrashLogging()
    }

    /**
     * Last-resort crash capture: writes any uncaught exception to a file the
     * user can reach (external files dir → visible over USB) and to logcat
     * under the [TAG] tag, then hands it to the previous handler so the
     * platform crash reporting still behaves normally.
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
