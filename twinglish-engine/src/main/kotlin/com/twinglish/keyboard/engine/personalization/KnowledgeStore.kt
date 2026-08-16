package com.twinglish.keyboard.engine.personalization

import com.twinglish.keyboard.engine.translation.TranslationSanitizer
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/**
 * Local knowledge store: the bounded translation cache plus every piece of
 * learned personalization (preferences, phrases, vocabulary, corrections,
 * style statistics). All personalization lives on-device; nothing here ever
 * leaves the device.
 *
 * Implementations must be safe to call from any thread (the IME touches
 * them from background translation coroutines and the main thread).
 */
interface KnowledgeStore {

    // ---- translation cache ----

    fun getCache(normalizedSource: String): TranslationCacheEntry?
    fun putCache(entry: TranslationCacheEntry)
    fun allCache(): List<TranslationCacheEntry>
    fun evictCache(maxEntries: Int)
    fun clearCache()

    // ---- learned preferences ----

    fun getPreference(from: String, to: String): LearnedPreference?
    fun putPreference(preference: LearnedPreference)
    fun allPreferences(): List<LearnedPreference>
    fun clearPreferences()

    // ---- phrases ----

    fun putPhrase(phrase: LearnedPhrase)
    fun allPhrases(): List<LearnedPhrase>
    fun clearPhrases()

    // ---- vocabulary ----

    fun putVocabulary(word: VocabularyWord)
    fun allVocabulary(): List<VocabularyWord>
    fun clearVocabulary()

    // ---- corrections ----

    fun addCorrection(event: CorrectionEvent)
    fun allCorrections(): List<CorrectionEvent>
    fun clearCorrections()

    // ---- style profile ----

    fun stats(): StyleStats
    fun setStats(stats: StyleStats)

    /** Wipe everything the user may want to clear. */
    fun clearAll()
}

/**
 * Thread-safe in-memory knowledge store with optional durable persistence.
 *
 * With a [path], every mutation is written through to a local file (atomic
 * rename, base64-encoded lines) so learned data survives restarts. With a
 * null path it behaves as a pure in-memory store (used by tests).
 *
 * The cache is bounded: on insert it evicts the least-recently-used entries,
 * always keeping user-approved and user-modified rows unless every entry is
 * protected.
 */
class LocalKnowledgeStore(
    path: String? = null,
    private val maxCache: Int = 5000,
    private val maxPhrases: Int = 2000,
) : KnowledgeStore {

    private val lock = Any()

    private val cache = LinkedHashMap<String, TranslationCacheEntry>()
    private val preferences = LinkedHashMap<String, LearnedPreference>()
    private val phrases = LinkedHashMap<String, LearnedPhrase>()
    private val vocabulary = LinkedHashMap<String, VocabularyWord>()
    private val corrections = ArrayDeque<CorrectionEvent>()
    private var styleStats = StyleStats()

    private val file: File? = path?.let { File(it) }

    init {
        file?.let { load(it) }
    }

    // ---- cache ----

    override fun getCache(normalizedSource: String): TranslationCacheEntry? = synchronized(lock) {
        cache[normalizedSource]
    }

    override fun putCache(entry: TranslationCacheEntry) = synchronized(lock) {
        // Never persist Google metadata tokens that may have leaked through a
        // translation (defense in depth — see TranslationSanitizer).
        val cleaned = entry.copy(
            teluguText = entry.teluguText?.let { TranslationSanitizer.clean(it) }?.ifBlank { null },
            twinglishText = TranslationSanitizer.clean(entry.twinglishText),
        )
        if (cleaned.twinglishText.isBlank()) return@synchronized
        val existing = cache[cleaned.normalizedSource]
        cache[cleaned.normalizedSource] = if (existing != null && !cleaned.userApproved) {
            // A generated entry overwritten by later usage keeps its history.
            cleaned.copy(usageCount = existing.usageCount + 1, lastUsedAt = cleaned.lastUsedAt)
        } else {
            cleaned
        }
        evictCacheLocked()
        persistLocked()
    }

    override fun allCache(): List<TranslationCacheEntry> = synchronized(lock) { cache.values.toList() }

    override fun evictCache(maxEntries: Int) = synchronized(lock) {
        if (cache.size > maxEntries) {
            // Drop unprotected (generated) entries first, LRU order.
            val unprotected = cache.values
                .filter { !it.userApproved && !it.userModified }
                .sortedBy { it.lastUsedAt }
            for (e in unprotected) {
                if (cache.size <= maxEntries) break
                cache.remove(e.normalizedSource)
            }
            // Then, if still over the limit, drop protected entries LRU.
            val all = cache.values.sortedBy { it.lastUsedAt }
            for (e in all) {
                if (cache.size <= maxEntries) break
                cache.remove(e.normalizedSource)
            }
        }
        persistLocked()
    }

    override fun clearCache() = synchronized(lock) {
        cache.clear()
        persistLocked()
    }

    // ---- preferences ----

    override fun getPreference(from: String, to: String): LearnedPreference? = synchronized(lock) {
        preferences["$from\u0001$to"]
    }

    override fun putPreference(preference: LearnedPreference) = synchronized(lock) {
        preferences["${preference.from}\u0001${preference.to}"] = preference
        persistLocked()
    }

    override fun allPreferences(): List<LearnedPreference> = synchronized(lock) { preferences.values.toList() }

    override fun clearPreferences() = synchronized(lock) {
        preferences.clear()
        persistLocked()
    }

    // ---- phrases ----

    override fun putPhrase(phrase: LearnedPhrase) = synchronized(lock) {
        val cleanPhrase = TranslationSanitizer.clean(phrase.phrase)
        if (cleanPhrase.isBlank()) return@synchronized
        val cleaned = phrase.copy(
            phrase = cleanPhrase,
            sourceSentence = TranslationSanitizer.clean(phrase.sourceSentence),
        )
        val existing = phrases[cleaned.phrase]
        val merged = if (existing != null) {
            cleaned.copy(
                usageCount = existing.usageCount + 1,
                sourceSentence = if (existing.sourceSentence.isBlank()) cleaned.sourceSentence else existing.sourceSentence,
            )
        } else cleaned
        phrases[merged.phrase] = merged
        if (phrases.size > maxPhrases) {
            val oldest = phrases.values.sortedBy { it.lastUsedAt }
            for (p in oldest) {
                if (phrases.size <= maxPhrases) break
                phrases.remove(p.phrase)
            }
        }
        persistLocked()
    }

    override fun allPhrases(): List<LearnedPhrase> = synchronized(lock) { phrases.values.toList() }

    override fun clearPhrases() = synchronized(lock) {
        phrases.clear()
        persistLocked()
    }

    // ---- vocabulary ----

    override fun putVocabulary(word: VocabularyWord) = synchronized(lock) {
        val existing = vocabulary[word.word]
        val merged = if (existing != null) {
            word.copy(usageCount = existing.usageCount + 1)
        } else word
        vocabulary[merged.word] = merged
        persistLocked()
    }

    override fun allVocabulary(): List<VocabularyWord> = synchronized(lock) { vocabulary.values.toList() }

    override fun clearVocabulary() = synchronized(lock) {
        vocabulary.clear()
        persistLocked()
    }

    // ---- corrections ----

    override fun addCorrection(event: CorrectionEvent) = synchronized(lock) {
        corrections.addLast(event)
        // Bounded, then aggregated away — raw events are only kept for the
        // explainable-learning view and are capped.
        while (corrections.size > 200) corrections.removeFirst()
        persistLocked()
    }

    override fun allCorrections(): List<CorrectionEvent> = synchronized(lock) { corrections.toList() }

    override fun clearCorrections() = synchronized(lock) {
        corrections.clear()
        persistLocked()
    }

    // ---- style profile ----

    override fun stats(): StyleStats = synchronized(lock) { styleStats }

    override fun setStats(stats: StyleStats) = synchronized(lock) {
        styleStats = stats
        persistLocked()
    }

    override fun clearAll() = synchronized(lock) {
        cache.clear()
        preferences.clear()
        phrases.clear()
        vocabulary.clear()
        corrections.clear()
        styleStats = StyleStats()
        persistLocked()
    }

    // ------------------------------------------------------------------
    // persistence (base64 line format, atomic write)
    // ------------------------------------------------------------------

    private fun persistLocked() {
        val f = file ?: return
        runCatching {
            val sb = StringBuilder()
            sb.append("v1\n")
            cache.values.forEach { e ->
                sb.append("C|").append(b64(e.normalizedSource))
                    .append('|').append(b64(e.teluguText ?: ""))
                    .append('|').append(b64(e.twinglishText))
                    .append('|').append(b64(e.provider))
                    .append('|').append(e.createdAt)
                    .append('|').append(e.lastUsedAt)
                    .append('|').append(e.usageCount)
                    .append('|').append(e.confidence)
                    .append('|').append(if (e.userApproved) 1 else 0)
                    .append('|').append(if (e.userModified) 1 else 0)
                    .append('|').append(b64(e.style))
                    .append('|').append(b64(e.locale))
                    .append('\n')
            }
            preferences.values.forEach { p ->
                sb.append("P|").append(b64(p.from))
                    .append('|').append(b64(p.to))
                    .append('|').append(b64(p.context))
                    .append('|').append(p.confidence)
                    .append('|').append(p.usageCount)
                    .append('|').append(p.lastUsedAt)
                    .append('\n')
            }
            phrases.values.forEach { p ->
                sb.append("H|").append(b64(p.phrase))
                    .append('|').append(b64(p.sourceSentence))
                    .append('|').append(p.usageCount)
                    .append('|').append(p.lastUsedAt)
                    .append('\n')
            }
            vocabulary.values.forEach { v ->
                sb.append("V|").append(b64(v.word))
                    .append('|').append(if (v.keepEnglish) 1 else 0)
                    .append('|').append(v.confidence)
                    .append('|').append(v.usageCount)
                    .append('\n')
            }
            corrections.forEach { c ->
                sb.append("E|").append(b64(c.sourceContext))
                    .append('|').append(b64(c.generated))
                    .append('|').append(b64(c.userVersion))
                    .append('|').append(c.createdAt)
                    .append('\n')
            }
            val s = styleStats
            sb.append("S|").append(b64(serializeStats(s))).append('\n')

            val tmp = File(f.absolutePath + ".tmp")
            tmp.writeText(sb.toString(), StandardCharsets.UTF_8)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        }
    }

    private fun load(f: File) {
        synchronized(lock) {
            runCatching {
                if (!f.exists()) return
                val lines = f.readLines(StandardCharsets.UTF_8)
                if (lines.isEmpty() || lines[0] != "v1") return
            for (line in lines.drop(1)) {
                if (line.isBlank()) continue
                val parts = line.split('|')
                when (parts[0]) {
                    "C" -> if (parts.size >= 13) {
                        val e = TranslationCacheEntry(
                            normalizedSource = unb64(parts[1]),
                            teluguText = unb64(parts[2]).ifBlank { null },
                            twinglishText = unb64(parts[3]),
                            provider = unb64(parts[4]),
                            createdAt = parts[5].toLong(),
                            lastUsedAt = parts[6].toLong(),
                            usageCount = parts[7].toInt(),
                            confidence = parts[8].toFloat(),
                            userApproved = parts[9] == "1",
                            userModified = parts[10] == "1",
                            style = unb64(parts[11]),
                            locale = unb64(parts[12]),
                        )
                        // Drop entries polluted by Google metadata tokens
                        // (written by older builds) so stale garbage can
                        // never surface from the persistent cache.
                        if (!TranslationSanitizer.hasGarbage(e.twinglishText)) {
                            cache[e.normalizedSource] = e
                        }
                    }
                    "P" -> if (parts.size >= 7) {
                        val p = LearnedPreference(
                            from = unb64(parts[1]),
                            to = unb64(parts[2]),
                            context = unb64(parts[3]),
                            confidence = parts[4].toFloat(),
                            usageCount = parts[5].toInt(),
                            lastUsedAt = parts[6].toLong(),
                        )
                        preferences["${p.from}\u0001${p.to}"] = p
                    }
                    "H" -> if (parts.size >= 5) {
                        val p = LearnedPhrase(
                            phrase = unb64(parts[1]),
                            sourceSentence = unb64(parts[2]),
                            usageCount = parts[3].toInt(),
                            lastUsedAt = parts[4].toLong(),
                        )
                        if (!TranslationSanitizer.hasGarbage(p.phrase)) {
                            phrases[p.phrase] = p
                        }
                    }
                    "V" -> if (parts.size >= 5) {
                        val v = VocabularyWord(
                            word = unb64(parts[1]),
                            keepEnglish = parts[2] == "1",
                            confidence = parts[3].toFloat(),
                            usageCount = parts[4].toInt(),
                        )
                        vocabulary[v.word] = v
                    }
                    "E" -> if (parts.size >= 5) {
                        corrections.addLast(
                            CorrectionEvent(
                                sourceContext = unb64(parts[1]),
                                generated = unb64(parts[2]),
                                userVersion = unb64(parts[3]),
                                createdAt = parts[4].toLong(),
                            )
                        )
                    }
                    "S" -> if (parts.size >= 2) styleStats = deserializeStats(unb64(parts[1]))
                }
            }
            evictCacheLocked()
        }
        }
    }

    private fun evictCacheLocked() {
        if (cache.size > maxCache) {
            val unprotected = cache.values
                .filter { !it.userApproved && !it.userModified }
                .sortedBy { it.lastUsedAt }
            for (e in unprotected) {
                if (cache.size <= maxCache) break
                cache.remove(e.normalizedSource)
            }
            val all = cache.values.sortedBy { it.lastUsedAt }
            for (e in all) {
                if (cache.size <= maxCache) break
                cache.remove(e.normalizedSource)
            }
        }
    }

    private fun serializeStats(s: StyleStats): String {
        val parts = mutableListOf<String>()
        s.acceptedByStyle.forEach { (k, v) -> parts.add("a:$k=$v") }
        s.rejectedByStyle.forEach { (k, v) -> parts.add("r:$k=$v") }
        parts.add("q=${s.questionMarkCount}")
        parts.add("x=${s.exclamationCount}")
        return parts.joinToString(",")
    }

    private fun deserializeStats(raw: String): StyleStats {
        var qm = 0
        var ex = 0
        val accepted = mutableMapOf<String, Int>()
        val rejected = mutableMapOf<String, Int>()
        for (part in raw.split(',')) {
            if (part.isEmpty()) continue
            when {
                part.startsWith("a:") -> part.substring(2).split('=').let { (k, v) ->
                    accepted[k] = v.toIntOrNull() ?: 0
                }
                part.startsWith("r:") -> part.substring(2).split('=').let { (k, v) ->
                    rejected[k] = v.toIntOrNull() ?: 0
                }
                part.startsWith("q=") -> qm = part.substring(2).toIntOrNull() ?: 0
                part.startsWith("x=") -> ex = part.substring(2).toIntOrNull() ?: 0
            }
        }
        return StyleStats(accepted, rejected, qm, ex)
    }

    private fun b64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String =
        Base64.getDecoder().decode(value).toString(StandardCharsets.UTF_8)
}
