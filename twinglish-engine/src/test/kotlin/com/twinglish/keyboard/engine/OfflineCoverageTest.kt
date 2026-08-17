package com.twinglish.keyboard.engine

import com.twinglish.keyboard.engine.translation.GoogleTranslationProvider
import com.twinglish.keyboard.engine.translation.OfflineTranslationProvider
import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frequently used conversational sentences must be answered by the local
 * phrase bank — no network call, no error notice. These are the sentences
 * a Telugu speaker types every day; they should work even with Online
 * translation switched off and with zero connectivity.
 */
class OfflineCoverageTest {

    private val engine = TwinglishEngine()

    private fun t(text: String): String =
        runBlocking { engine.translate(text, TranslationStyle.CASUAL)?.twinglish ?: "" }

    // input → expected local Twinglish
    private val common = listOf(
        // greetings / small talk
        "whats up" to "emundi?",
        "what's up" to "emundi?",
        "how have you been" to "innallu ela unnav?",
        "how was your day" to "nee roju ela undi?",
        "how is everyone" to "andaru ela unnaru?",
        "nice to meet you" to "ninnu kalavadam santosham",
        "see you" to "kaluddam",
        "bye" to "bye",
        // introductions
        "where are you from" to "nuvvu ekkada nunchi vachav?",
        "i am from Hyderabad" to "nenu Hyderabad nunchi vachanu",
        "i am working" to "nenu pani chestunnanu",
        "how old are you" to "nee vayasu enta?",
        // food
        "i have eaten" to "nenu tinnanu",
        "i haven't eaten" to "nenu tinaledu",
        "i had lunch" to "nenu lunch chesanu",
        "let's eat" to "tinudam",
        "what is for lunch" to "lunch enti?",
        "the food is tasty" to "food bagundi",
        "i am full" to "naaku kadupu nindindi",
        "i want to eat" to "naaku tinalani undi",
        // time / plans
        "what time is it" to "ippudu enta samayam?",
        "are you busy" to "nuvvu busy ga unnava?",
        "i don't have time" to "naaku samayam ledu",
        "i am late" to "nenu late ayanu",
        "when will you be free" to "neeku epudu time untundi?",
        // travel
        "where are you now" to "nuvvu ippudu ekkada unnav?",
        "i missed the bus" to "naaku bus miss ayindi",
        "how do i go to the market" to "nenu market ki ela vellali?",
        "let's go to the market" to "market ki veldam",
        "i reached home" to "nenu intiki cherukunnanu",
        "i am home" to "nenu intlo unna",
        "i am lost" to "nenu daari tappanu",
        // shopping / money
        "how much does it cost" to "deeni dhara enta?",
        "it is expensive" to "deeni dhara chala ekkuva",
        "i will buy this" to "nenu deenni kontanu",
        "do you have money" to "nee daggara dabbu unda?",
        // work / study
        "i am going to office" to "nenu office ki velthunnanu",
        "i am in a meeting" to "nenu meeting lo unna",
        "when is the meeting" to "meeting epudu?",
        "how was work" to "pani ela undi?",
        "i have an exam" to "naaku exam undi",
        "i finished my work" to "naa pani ayipoyindi",
        // feelings / health
        "how are you feeling" to "neeku ela anipistondi?",
        "i am not feeling well" to "naaku baga ledu",
        "i have a fever" to "naaku jvaram vachindi",
        "i have a headache" to "naaku tala noppi vastondi",
        "i am sleepy" to "naaku nidra vastondi",
        "get well soon" to "tvaraga bagupadu",
        // phone / communication
        "i called you" to "nenu neeku call chesanu",
        "did you call me" to "naaku call chesava?",
        "i missed your call" to "nee call miss ayindi",
        "message me" to "naaku message pampu",
        "i sent you a message" to "neeku message pampanu",
        "are you on whatsapp" to "nuvvu whatsapp lo unnava?",
        "send me the photo" to "naaku photo pampu",
        // opinions
        "i agree" to "nenu agree avutunna",
        "i love it" to "naaku chala ishtam",
        "i don't like it" to "naaku ishtam ledu",
        "that is good" to "adi bagundi",
        "sounds great" to "chala bagundi",
        "got it" to "arthamaindi",
        "maybe" to "bahusha",
        "sure" to "tappakunda",
        // requests / help
        "i need help" to "naaku sahayam kavali",
        "do you need a book" to "neeku pustakam kavala?",
        // questions
        "what do you mean" to "em antunnav?",
        "what do you think" to "neeku em anipistundi?",
        "who is this" to "idi evaru?",
        "how do you know" to "neeku ela telusu?",
        "how far" to "enta dooram?",
        "how many people" to "enta mandi?",
        "where is the bathroom" to "bathroom ekkada?",
        "which one do you want" to "neeku edi kavali?",
        "which is better" to "edi bagundi?",
        "is that true" to "adi nijama?",
        "what did you say" to "emannav?",
        "what do you do" to "nuvvu em chestav?",
        // possession
        "i have a car" to "naaku car undi",
        "do you have a car" to "neeku car unda?",
        "i don't have a car" to "naaku car ledu",
        // weather
        "how is the weather" to "weather ela undi?",
        "it is raining" to "varsham padutondi",
        "the weather is nice today" to "ee roju weather chala bagundi",
        // waiting / listening
        "i am waiting for you" to "nenu nee kosam aagutunna",
        "are you there" to "nuvvu unnava?",
        "are you listening" to "nuvvu vintunnava?",
        "speak slowly" to "nemmadiga matladu",
        "say it again" to "malli cheppu",
        "one more time" to "marosari",
        // memory / reassurance
        "i forgot" to "nenu marchipoyanu",
        "i remember" to "naaku gurtundi",
        "don't worry" to "chintinchaku",
        "have fun" to "enjoy cheyyi",
        "be careful" to "jagrattaga undu",
        // contractions reach the same rules as spelled-out forms
        "i'm going home" to "nenu intiki velthunnanu",
        "i'm coming now" to "nenu ippudu vastunna",
        "i'm on the bus" to "nenu bus lo unna",
        "i've eaten" to "nenu tinnanu",
        "let's go" to "veldam",
        "it's raining" to "varsham padutondi",
    )

    @Test
    fun `everyday sentences translate locally with natural twinglish`() {
        for ((input, expected) in common) {
            assertEquals("offline: $input", expected, t(input))
        }
    }

    @Test
    fun `everyday sentences are answered without ever touching the network`() = runBlocking {
        var fetches = 0
        val provider = GoogleTranslationProvider(
            offline = OfflineTranslationProvider(),
            onlineEnabled = { true },
            fetcher = {
                fetches++
                null // if the network is ever called the test fails
            },
        )
        for ((input, _) in common) {
            val result = provider.translateEnglishToTelugu(input, TranslationStyle.CASUAL)
            assertNotNull("local result for: $input", result)
            assertTrue("no error for: $input (${result?.error})", result!!.error == null)
        }
        assertEquals(0, fetches)
    }

    @Test
    fun `polite and formal styles still work for the new bank`() = runBlocking {
        val polite = runBlocking {
            engine.translate("where are you from", TranslationStyle.POLITE)?.twinglish
        }
        assertEquals("meeru ekkada nunchi vacharu?", polite)

        val formal = runBlocking {
            engine.translate("where are you from", TranslationStyle.FORMAL)?.twinglish
        }
        assertEquals("meeru ekkada nunchi vacharu?", formal)
    }
}
