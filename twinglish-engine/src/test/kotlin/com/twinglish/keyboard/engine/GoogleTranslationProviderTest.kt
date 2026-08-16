package com.twinglish.keyboard.engine.translation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleTranslationProviderTest {

    /** Controllable offline provider used as the composite's first stage. */
    private class FakeOffline(
        var result: TranslationResult?,
        var calls: Int = 0,
    ) : TranslationProvider {
        override val id: String = "fake-offline"
        override val isOnline: Boolean = false
        override suspend fun translateEnglishToTelugu(
            text: String,
            style: TranslationStyle,
        ): TranslationResult? {
            calls++
            return result
        }
        override fun romanizeTelugu(teluguText: String, style: RomanizationStyle): String =
            teluguText
    }

    private fun teluguResult(text: String, confidence: Float): TranslationResult =
        TranslationResult(
            input = text,
            telugu = "తెలుగు",
            twinglish = "telugu",
            confidence = confidence,
            style = TranslationStyle.CASUAL,
        )

    // ------------------------------------------------------------------
    // gtx payload parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses a single segment gtx payload`() {
        val body = """[[["మీరు ఎలా ఉన్నారు?","how are you?",null,null,10]],null,"en",null,null,[["en"],["te"]]]"""
        assertEquals("మీరు ఎలా ఉన్నారు?", GoogleTranslationProvider.parseGtxResponse(body))
    }

    @Test
    fun `concatenates multi segment payloads in order`() {
        val body = """[[["ఎలా","how",null,null,10],[" ఉన్నావు"," are you",null,null,10]],null,"en"]"""
        assertEquals("ఎలా ఉన్నావు", GoogleTranslationProvider.parseGtxResponse(body))
    }

    @Test
    fun `unescapes quoted text inside segments`() {
        val body = """[[["అతను \"అందంగా\" ఉన్నాడు","he is handsome",null,null,10]],null,"en"]"""
        assertEquals("అతను \"అందంగా\" ఉన్నాడు", GoogleTranslationProvider.parseGtxResponse(body))
    }

    @Test
    fun `google model metadata embedded in a segment is never appended`() {
        // Real gtx payload: Google embeds its internal model id + filename as a
        // nested array inside the segment. Only the actual translation may be
        // extracted — the model hash must not leak into the output.
        val body = """[[["హలో ఎలా ఉన్నావు","hlo how are you",null,null,3,null,null,[[]],[[["ee29150929b269c38979323546d85c49","tea_DravidianA_en2knmlsitate_2021q3.md"]]]]],null,"en",null,null,null,null,[]]"""
        assertEquals("హలో ఎలా ఉన్నావు", GoogleTranslationProvider.parseGtxResponse(body))
    }

    @Test
    fun `model metadata with capitalised input is also stripped`() {
        val body = """[[["హలో ఎలా ఉన్నారు","Hlo how are you",null,null,3,null,null,[[]],[[["ee29150929b269c38979323546d85c49","tea_DravidianA_en2knmlsitate_2021q3.md"]]]]],null,"en",null,null,null,null,[]]"""
        assertEquals("హలో ఎలా ఉన్నారు", GoogleTranslationProvider.parseGtxResponse(body))
    }

    @Test
    fun `returns null for garbage payloads`() {
        assertNull(GoogleTranslationProvider.parseGtxResponse("not json at all"))
        assertNull(GoogleTranslationProvider.parseGtxResponse("""{"error":"nope"}"""))
        assertNull(GoogleTranslationProvider.parseGtxResponse(""))
    }

    // ------------------------------------------------------------------
    // offline-first behavior
    // ------------------------------------------------------------------

    @Test
    fun `high confidence offline result wins and the network is never called`() = runBlocking {
        var fetches = 0
        val offline = FakeOffline(teluguResult("how are you", 0.92f))
        val provider = GoogleTranslationProvider(
            offline = offline,
            onlineEnabled = { true },
            fetcher = {
                fetches++
                "మీరు ఎలా ఉన్నారు?"
            },
        )

        val result = provider.translateEnglishToTelugu("How are you?", TranslationStyle.CASUAL)
        assertEquals("తెలుగు", result!!.telugu)
        assertEquals(1, offline.calls)
        assertEquals(0, fetches)
    }

    @Test
    fun `low confidence offline result defers to google`() = runBlocking {
        val offline = FakeOffline(teluguResult("i am going to market", 0.7f))
        val provider = GoogleTranslationProvider(
            offline = offline,
            onlineEnabled = { true },
            fetcher = { "నేను మార్కెట్ కి వెళ్తున్నాను" },
        )

        val result = provider.translateEnglishToTelugu("i am going to market", TranslationStyle.CASUAL)
        assertEquals("నేను మార్కెట్ కి వెళ్తున్నాను", result!!.telugu)
        assertEquals(1, offline.calls)
    }

    @Test
    fun `offline miss falls back to google and romanizes to twinglish`() = runBlocking {
        val offline = FakeOffline(null)
        val provider = GoogleTranslationProvider(
            offline = offline,
            onlineEnabled = { true },
            fetcher = { "మీరు ఎలా ఉన్నారు?" },
        )

        val result = provider.translateEnglishToTelugu("how are you today my friend", TranslationStyle.CASUAL)
        // Google's neutral Telugu is nudged to casual chat register…
        assertEquals("నువ్వు ఎలా ఉన్నావు?", result!!.telugu)
        // …and romanized to conversational Twinglish.
        assertEquals("nuvvu ela unnav?", result.twinglish)
        assertEquals(0.95f, result.confidence)
    }

    @Test
    fun `offline miss with online disabled keeps the offline result`() = runBlocking {
        var fetches = 0
        val offline = FakeOffline(null)
        val provider = GoogleTranslationProvider(
            offline = offline,
            onlineEnabled = { false },
            fetcher = {
                fetches++
                "మీరు ఎలా ఉన్నారు?"
            },
        )

        assertNull(provider.translateEnglishToTelugu("some sentence the bank misses", TranslationStyle.CASUAL))
        assertEquals(0, fetches)
    }

    @Test
    fun `google failure falls back to the offline result`() = runBlocking {
        val offline = FakeOffline(teluguResult("whatever", 0.7f))
        val provider = GoogleTranslationProvider(
            offline = offline,
            onlineEnabled = { true },
            fetcher = { null }, // network failure
        )

        val result = provider.translateEnglishToTelugu("i am going to market", TranslationStyle.CASUAL)
        assertEquals("తెలుగు", result!!.telugu)
    }

    // ------------------------------------------------------------------
    // fetch cache: the IME asks for several styles in quick succession
    // ------------------------------------------------------------------

    @Test
    fun `normalized duplicates share a single google fetch`() = runBlocking {
        var fetches = 0
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = {
                fetches++
                "నేను మార్కెట్ కి వెళ్తున్నాను"
            },
        )

        provider.translateEnglishToTelugu("I am going to the market", TranslationStyle.CASUAL)
        provider.translateEnglishToTelugu("i am going to the market", TranslationStyle.POLITE)
        assertEquals(1, fetches)
    }

    @Test
    fun `polite and formal styles keep the google telugu untouched`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "మీరు ఎలా ఉన్నారు?" },
        )

        val polite = provider.translateEnglishToTelugu("how are you", TranslationStyle.POLITE)
        assertEquals("మీరు ఎలా ఉన్నారు?", polite!!.telugu)
        assertEquals("meeru ela unnaru?", polite.twinglish)

        val formal = provider.translateEnglishToTelugu("how are you", TranslationStyle.FORMAL)
        assertEquals("మీరు ఎలా ఉన్నారు?", formal!!.telugu)
    }

    @Test
    fun `interrogative without typed punctuation still gets a question mark`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "సినిమా ఎలా ఉంది" }, // Google omits the "?" when input had none
        )

        val result = provider.translateEnglishToTelugu("how is the movie", TranslationStyle.CASUAL)
        assertEquals("సినిమా ఎలా ఉంది?", result!!.telugu)
        assertEquals("sinima ela undi?", result.twinglish)
    }

    @Test
    fun `statements never gain a spurious question mark`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "నేను మార్కెట్ కి వెళ్తున్నాను" },
        )

        val result = provider.translateEnglishToTelugu("i am going to the market", TranslationStyle.CASUAL)
        assertEquals("నేను మార్కెట్ కి వెళ్తున్నాను", result!!.telugu)
        assertTrue(result.twinglish.endsWith("anu") && !result.twinglish.endsWith("?"))
    }

    @Test
    fun `greeting followed by a question still gets a question mark`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "హలో ఎలా ఉన్నావు" }, // Google omits the "?" when input had none
        )

        val result = provider.translateEnglishToTelugu("hlo how are you", TranslationStyle.CASUAL)
        assertEquals("హలో ఎలా ఉన్నావు?", result!!.telugu)
        assertEquals("halo ela unnav?", result.twinglish)
    }

    @Test
    fun `literary google telugu is casualized into conversational twinglish`() = runBlocking {
        // Real gtx payload for "i created a new app that auto converts english
        // to twiglish": Google returns formal literary Telugu that romanizes
        // badly, transliterates the brand name, and inserts zero-width joiners.
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "నేను ఇంగ్లీషును స్వయంచాలకంగా ట్విగ్లిష్\u200cగా మార్చే కొత్త యాప్\u200cని సృష్టించాను" },
        )

        val result = provider.translateEnglishToTelugu(
            "i created a new app that auto converts english to twiglish",
            TranslationStyle.CASUAL,
        )
        // Literary words become chat forms / code-switched English, the brand
        // name is preserved, and the joiners are gone.
        assertEquals(
            "నేను English ని automatic గా Twinglish గా మార్చే కొత్త app ని తయారు చేశాను",
            result!!.telugu,
        )
        // …and it romanizes to something a Telugu speaker would actually type.
        assertEquals(
            "nenu English ni automatic ga Twinglish ga marche kotta app ni tayaaru chesanu",
            result.twinglish,
        )
    }

    @Test
    fun `brand name variants always stay twinglish`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "నేను ట్వింగ్లీష్ వాడతాను" },
        )

        val result = provider.translateEnglishToTelugu("i use twinglish", TranslationStyle.CASUAL)
        assertEquals("నేను Twinglish వాడతాను", result!!.telugu)
    }

    @Test
    fun `cheshanu romanizes to casual chat form`() {
        assertEquals("nenu chesanu", Romanizer.romanize("నేను చేశాను"))
        assertEquals("nuvvu chesav", Romanizer.romanize("నువ్వు చేశావు"))
        assertEquals("vaaru chesaru", Romanizer.romanize("వారు చేశారు"))
    }

    @Test
    fun `already punctuated google output is not doubled`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "సినిమా ఎలా ఉంది?" },
        )

        val result = provider.translateEnglishToTelugu("how is the movie", TranslationStyle.CASUAL)
        assertEquals("సినిమా ఎలా ఉంది?", result!!.telugu)
    }

    @Test
    fun `already casual telugu is unchanged by the casualizer`() = runBlocking {
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = { "నువ్వు ఏం చేస్తున్నావు?" },
        )

        val result = provider.translateEnglishToTelugu("what are you doing", TranslationStyle.CASUAL)
        assertEquals("నువ్వు ఏం చేస్తున్నావు?", result!!.telugu)
        assertEquals("nuvvu em chestunnav?", result.twinglish)
    }

    @Test
    fun `blank input never reaches the network`() = runBlocking {
        var fetches = 0
        val provider = GoogleTranslationProvider(
            offline = FakeOffline(null),
            onlineEnabled = { true },
            fetcher = {
                fetches++
                "x"
            },
        )
        assertNull(provider.translateEnglishToTelugu("   ", TranslationStyle.CASUAL))
        assertEquals(0, fetches)
    }

    // ------------------------------------------------------------------
    // the real provider still answers the phrase bank offline
    // ------------------------------------------------------------------

    @Test
    fun `real phrase bank sentences translate without touching the network`() = runBlocking {
        var fetches = 0
        val provider = GoogleTranslationProvider(
            offline = OfflineTranslationProvider(),
            onlineEnabled = { true },
            fetcher = {
                fetches++
                "మీరు ఎలా ఉన్నారు?"
            },
        )

        val result = provider.translateEnglishToTelugu("how are you", TranslationStyle.CASUAL)
        assertEquals("ఎలా ఉన్నావు?", result!!.telugu)
        assertEquals("ela unnav?", result.twinglish)
        assertEquals(0, fetches)
    }
}
