package com.twinglish.keyboard.engine

import com.twinglish.keyboard.engine.personalization.Candidate
import com.twinglish.keyboard.engine.personalization.LearningEngine
import com.twinglish.keyboard.engine.personalization.LearningFlags
import com.twinglish.keyboard.engine.personalization.LocalKnowledgeStore
import com.twinglish.keyboard.engine.personalization.PersonalizationEngine
import com.twinglish.keyboard.engine.personalization.PersonalizedRanker
import com.twinglish.keyboard.engine.personalization.TranslationCacheEntry
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationProvider
import com.twinglish.keyboard.engine.translation.TranslationResult
import com.twinglish.keyboard.engine.translation.TranslationStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationEngineTest {

    /** Wraps a provider and counts translation calls (for cache-hit tests). */
    private class CountingProvider(
        val delegate: TranslationProvider,
        val calls: MutableList<String> = mutableListOf(),
    ) : TranslationProvider {
        override val id: String = "counting"
        override val isOnline: Boolean = false
        override suspend fun translateEnglishToTelugu(text: String, style: TranslationStyle): TranslationResult? {
            calls += text
            return delegate.translateEnglishToTelugu(text, style)
        }
        override fun romanizeTelugu(teluguText: String, style: RomanizationStyle): String =
            delegate.romanizeTelugu(teluguText, style)
    }

    /** Provider that always fails — used to exercise the error path. */
    private class FailingProvider : TranslationProvider {
        override val id: String = "failing"
        override val isOnline: Boolean = false
        override suspend fun translateEnglishToTelugu(text: String, style: TranslationStyle): TranslationResult? =
            TranslationResult(
                input = text,
                telugu = null,
                twinglish = text, // original English — safe fallback
                confidence = 0f,
                style = style,
                error = "Translation failed — check your connection",
            )
        override fun romanizeTelugu(teluguText: String, style: RomanizationStyle): String = teluguText
    }

    // ---- total failure: error surfaced, english fallback, never cached ----

    @Test
    fun `total failure surfaces an error with english fallback and never caches it`() = runBlocking {
        val store = LocalKnowledgeStore(path = null)
        val learning = LearningEngine(store)
        val personal = PersonalizationEngine(
            TwinglishEngine(provider = FailingProvider()),
            store,
            learning,
            PersonalizedRanker(),
        )

        val result = personal.translateAndRank(
            "how is the movie today",
            TranslationStyle.CASUAL,
            RomanizationStyle.CASUAL,
        )
        assertTrue(result != null)
        // Original English offered as a tappable fallback…
        assertEquals("how is the movie today", result!!.candidates.first().text)
        // …with the reason carried for the UI to show.
        assertTrue(result.error != null)
        // A failure must never poison the cache with a non-translation.
        assertTrue(store.allCache().isEmpty())
    }

    private fun setup(
        flags: LearningFlags = LearningFlags(),
    ): Triple<PersonalizationEngine, CountingProvider, LocalKnowledgeStore> {
        val store = LocalKnowledgeStore(path = null)
        val learning = LearningEngine(store, flagsProvider = { flags })
        val ranker = PersonalizedRanker()
        val counting = CountingProvider(com.twinglish.keyboard.engine.translation.OfflineTranslationProvider())
        val engine = TwinglishEngine(provider = counting)
        val personal = PersonalizationEngine(engine, store, learning, ranker, flagsProvider = { flags })
        return Triple(personal, counting, store)
    }

    // ---- cache: same sentence twice, provider called once ----

    @Test
    fun `same sentence twice hits the cache without calling the provider again`() = runBlocking {
        val (personal, counting, _) = setup()
        val first = personal.translateAndRank("How are you?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(first != null && !first.cacheHit)
        assertEquals(3, counting.calls.size) // one per generated candidate style

        val second = personal.translateAndRank("How are you?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(second != null && second.cacheHit)
        assertEquals("ela unnav?", second!!.candidates.first().text)
        assertEquals(3, counting.calls.size) // NOT called again
    }

    @Test
    fun `punctuation variants share one cache entry and skip the provider`() = runBlocking {
        val (personal, counting, _) = setup()
        // First request (with "?"): generated and cached under the
        // punctuation-insensitive key.
        val first = personal.translateAndRank("how are you?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(first != null && !first.cacheHit)
        assertEquals(3, counting.calls.size)

        // Same sentence without punctuation: served from the local cache —
        // the provider (and therefore the network) is never touched again.
        val second = personal.translateAndRank("How are you", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(second != null && second.cacheHit)
        assertEquals("ela unnav?", second!!.candidates.first().text)
        assertEquals(3, counting.calls.size) // no new provider calls
    }

    @Test
    fun `cache key normalization folds case and whitespace`() = runBlocking {
        val (personal, counting, _) = setup()
        personal.translateAndRank("How   are YOU?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        val second = personal.translateAndRank("HOW ARE YOU?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(second!!.cacheHit)
        assertEquals(3, counting.calls.size) // no extra provider calls for the normalized key
    }

    @Test
    fun `normalized different keys do not collide`() = runBlocking {
        val (personal, counting, _) = setup()
        personal.translateAndRank("I am going home", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        personal.translateAndRank("Where are you going", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertEquals(6, counting.calls.size) // 3 candidate styles x 2 distinct sentences
    }

    // ---- correction learning: movie -> sinima ----

    @Test
    fun `user correction is cached as the preferred translation`() = runBlocking {
        val (personal, _, _) = setup()
        // First pass: the slot substitution already prefers "sinima".
        val first = personal.translateAndRank("i want movie", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(first!!.candidates.first().text.contains("sinima"))

        // This user prefers the English "movie" — correcting teaches that.
        personal.recordCorrected("i want movie", "naaku sinima kavali", "naaku movie kavali")

        // Exact personalized match now wins instantly.
        val second = personal.translateAndRank("i want movie", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertTrue(second!!.cacheHit)
        assertEquals("naaku movie kavali", second.candidates.first().text)
    }

    @Test
    fun `repeated correction raises confidence and applies to similar phrases`() {
        val (personal, _, _) = setup()
        // Two confirmations push the preference to the apply threshold.
        personal.recordCorrected("i want movie", "naaku sinima kavali", "naaku movie kavali")
        personal.recordCorrected("i want movie", "naaku sinima kavali", "naaku movie kavali")

        val applied = personal.applyPreferences("naaku sinima kavali", "i want movie")
        assertEquals("naaku movie kavali", applied)
    }

    @Test
    fun `single correction is too weak to apply automatically`() {
        val (personal, _, _) = setup()
        personal.recordCorrected("i want movie", "naaku sinima kavali", "naaku movie kavali")
        val applied = personal.applyPreferences("naaku sinima kavali", "i want movie")
        assertEquals("naaku sinima kavali", applied) // unchanged, confidence 0.2 < 0.4
    }

    @Test
    fun `learned preference re-ranks future candidates`() {
        val (personal, _, _) = setup()
        personal.recordCorrected("which movie you want", "e sinima kavali?", "e movie kavali?")
        personal.recordCorrected("which movie you want", "e sinima kavali?", "e movie kavali?")

        val ranker = PersonalizedRanker()
        val ranked = ranker.rank(
            candidates = listOf(
                Candidate(text = "e sinima kavali?", quality = 0.92f, style = TranslationStyle.CASUAL),
                Candidate(text = "e movie kavali?", quality = 0.92f, style = TranslationStyle.CASUAL),
            ),
            sourceNormalized = "which movie should we watch",
            store = personal.knowledgeStore,
            learning = personal.learningEngine,
        )
        assertEquals("e movie kavali?", ranked.first().text)
    }

    // ---- rejection: casual over formal ----

    @Test
    fun `repeatedly rejecting formal makes casual rank first`() = runBlocking {
        val (personal, _, _) = setup()
        // User consistently passes over the polite form and accepts casual.
        personal.recordRejected("meeru ela unnaru?", TranslationStyle.POLITE)
        personal.recordRejected("meeru ela unnaru?", TranslationStyle.POLITE)
        personal.recordRejected("meeru ela unnaru?", TranslationStyle.POLITE)
        personal.recordAccepted("how are you", "ela unnav?", TranslationStyle.CASUAL)

        // A fresh, similar sentence that generates both variants.
        val result = personal.translateAndRank("how are you doing", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        val texts = result!!.candidates.map { it.text }
        assertTrue(texts.contains("ela unnav?"))
        assertTrue(texts.contains("meeru ela unnaru?"))
        // The casual form must rank above the rejected formal form.
        assertTrue(texts.indexOf("ela unnav?") < texts.indexOf("meeru ela unnaru?"))
    }

    // ---- romanization preference ----

    @Test
    fun `romanization preference is learned from corrections`() {
        val (personal, _, _) = setup()
        personal.recordCorrected(
            "what are you doing",
            "nuvvu em chestunnavu?",
            "nuvvu em chestunnav?",
        )
        personal.recordCorrected(
            "what are you doing",
            "nuvvu em chestunnavu?",
            "nuvvu em chestunnav?",
        )
        assertEquals(
            "nuvvu em chestunnav?",
            personal.applyPreferences("nuvvu em chestunnavu?", "what are you doing"),
        )
    }

    // ---- phrase memory + autocomplete ----

    @Test
    fun `accepted phrases are available for autocomplete`() {
        val (personal, _, _) = setup()
        personal.recordAccepted("what are you doing", "em chestunnav?", TranslationStyle.CASUAL)
        personal.recordAccepted("what are you doing", "em chestunnav?", TranslationStyle.CASUAL)

        val candidates = personal.phraseCandidates("em che")
        assertTrue(candidates.contains("em chestunnav?"))
    }

    // ---- privacy: disabled flags record nothing ----

    @Test
    fun `disabled personalization records nothing`() {
        val flags = LearningFlags(
            enabled = false,
            corrections = false,
            vocabulary = false,
            personalizedSuggestions = false,
        )
        val (personal, _, store) = setup(flags)
        personal.recordAccepted("how are you", "ela unnav?", TranslationStyle.CASUAL)
        personal.recordCorrected("i want movie", "naaku movie kavali", "naaku sinima kavali")
        personal.recordRejected("meeru ela unnaru?", TranslationStyle.POLITE)

        assertTrue(store.allCache().isEmpty())
        assertTrue(store.allPreferences().isEmpty())
        assertTrue(store.allPhrases().isEmpty())
        assertTrue(store.allVocabulary().isEmpty())
        assertTrue(store.allCorrections().isEmpty())
        assertTrue(store.stats().acceptedByStyle.isEmpty())
        assertTrue(store.stats().rejectedByStyle.isEmpty())
    }

    @Test
    fun `disabled personalization still translates normally`() = runBlocking {
        val flags = LearningFlags(enabled = false, personalizedSuggestions = false)
        val (personal, _, _) = setup(flags)
        val result = personal.translateAndRank("What are you doing?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        assertEquals("em chestunnav?", result!!.candidates.first().text)
    }

    // ---- Google metadata tokens must never survive in the cache ----

    @Test
    fun `garbage text is cleaned before entering the cache`() {
        val store = LocalKnowledgeStore(path = null)
        store.putCache(
            TranslationCacheEntry(
                normalizedSource = "how is the movie",
                teluguText = null,
                twinglishText = "sinima ela undiee29150929b269c38979323546d85c49",
                createdAt = 1000L,
                lastUsedAt = 1000L,
                style = "casual",
            )
        )
        assertEquals("sinima ela undi", store.getCache("how is the movie")!!.twinglishText)
    }

    @Test
    fun `polluted entries persisted by older builds are purged on reload`() {
        val tmp = java.io.File.createTempFile("twinglish_polluted", ".data")
        tmp.deleteOnExit()
        val b64 = { s: String ->
            java.util.Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
        }
        // Hand-written "v1" line exactly as the old build would have written
        // it when the Google model hash leaked into a cached translation.
        val corruptLine = "C|" + b64("how is the movie") + "|" + b64("") + "|" +
            b64("sinima ela undiee29150929b269c38979323546d85c49") + "|" + b64("google+offline") +
            "|1000|1000|1|0.95|1|1|" + b64("casual") + "|" + b64("en")
        val cleanLine = "C|" + b64("how are you") + "|" + b64("") + "|" +
            b64("ela unnav?") + "|" + b64("offline") +
            "|1000|1000|1|0.95|0|0|" + b64("casual") + "|" + b64("en")
        tmp.writeText("v1\n$corruptLine\n$cleanLine\n")

        val store = LocalKnowledgeStore(path = tmp.absolutePath)
        // Corrupt entry gone…
        assertEquals(null, store.getCache("how is the movie"))
        // …while the clean entry survives.
        assertEquals("ela unnav?", store.getCache("how are you")!!.twinglishText)
        tmp.delete()
        Unit
    }

    // ---- persistence round-trip ----

    @Test
    fun `knowledge survives a store reload`() = runBlocking {
        val tmp = java.io.File.createTempFile("twinglish_knowledge", ".data")
        tmp.deleteOnExit()
        val store1 = LocalKnowledgeStore(path = tmp.absolutePath)
        val learning1 = LearningEngine(store1)
        val personal1 = PersonalizationEngine(
            TwinglishEngine(),
            store1,
            learning1,
            PersonalizedRanker(),
        )
        personal1.translateAndRank("How are you?", TranslationStyle.CASUAL, RomanizationStyle.CASUAL)
        personal1.recordAccepted("how are you", "ela unnav?", TranslationStyle.CASUAL)
        personal1.recordCorrected("i want movie", "naaku sinima kavali", "naaku movie kavali")

        // Fresh store reading the same file.
        val store2 = LocalKnowledgeStore(path = tmp.absolutePath)
        val hit = store2.getCache("how are you")
        assertTrue(hit != null && hit.userApproved)
        assertTrue(hit!!.twinglishText == "ela unnav?")
        assertTrue(store2.allPreferences().any { it.from == "sinima" && it.to == "movie" })
        tmp.delete()
        Unit
    }
}
