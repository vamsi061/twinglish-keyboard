package com.twinglish.keyboard.engine

import com.twinglish.keyboard.engine.translation.RomanizationStyle
import com.twinglish.keyboard.engine.translation.TranslationStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinglishEngineTest {

    private val engine = TwinglishEngine()

    private fun t(text: String, style: TranslationStyle = TranslationStyle.CASUAL): String =
        runBlocking { engine.translate(text, style)?.twinglish ?: "" }

    // ---- Spec §41 minimum test cases ----

    @Test
    fun `what are you doing`() {
        assertEquals("em chestunnav?", t("What are you doing?"))
    }

    @Test
    fun `how are you`() {
        assertEquals("ela unnav?", t("How are you?"))
    }

    @Test
    fun `where are you going`() {
        assertEquals("ekkadiki velthunnav?", t("Where are you going?"))
    }

    @Test
    fun `did you eat`() {
        assertEquals("tinnava?", t("Did you eat?"))
    }

    @Test
    fun `i am coming tomorrow`() {
        assertEquals("nenu repu vastanu", t("I am coming tomorrow"))
    }

    @Test
    fun `i am going home`() {
        assertEquals("nenu intiki velthunnanu", t("I am going home"))
    }

    @Test
    fun `why are you late`() {
        assertEquals("enduku late ayyav?", t("Why are you late?"))
    }

    @Test
    fun `call me later`() {
        assertEquals("tarvata naaku call cheyyi", t("Call me later"))
    }

    // ---- Product examples ----

    @Test
    fun `what are you doing today`() {
        assertEquals("em chestunnav ivala?", t("What are you doing today?"))
    }

    @Test
    fun `i will come tomorrow`() {
        assertEquals("nenu repu vastanu", t("I will come tomorrow"))
    }

    @Test
    fun `i am coming with emoji`() {
        assertEquals("nenu vastunna \uD83D\uDE02", t("I am coming \uD83D\uDE02"))
    }

    @Test
    fun `preserves proper noun`() {
        assertEquals("nenu repu Hyderabad ki velthunnanu", t("I am going to Hyderabad tomorrow"))
    }

    @Test
    fun `preserves exclamation`() {
        assertTrue(t("Where are you!").endsWith("!"))
    }

    @Test
    fun `really`() {
        assertEquals("nijanga?", t("Really?"))
    }

    @Test
    fun `filler prefix kept`() {
        assertEquals("Hey ra, em chestunnav?", t("Hey ra, what are you doing?"))
    }

    // ---- Bug-fix: complete phrase translation, never token-by-token ----

    @Test
    fun `which movie you want translates as a whole phrase`() {
        assertEquals("e sinima kavali?", t("which movie you want"))
    }

    @Test
    fun `which movie you want with punctuation`() {
        assertEquals("e sinima kavali?", t("Which movie you want?"))
    }

    @Test
    fun `which movie are you watching`() {
        assertEquals("e sinima chustunnav?", t("Which movie are you watching"))
    }

    @Test
    fun `i will call you later`() {
        assertEquals("tarvata neeku call chesta", t("I will call you later"))
    }

    @Test
    fun `all caps question normalizes to lowercase output`() {
        assertEquals("em chestunnav?", t("WHAT ARE YOU DOING"))
    }

    @Test
    fun `mixed caps question normalizes to lowercase output`() {
        assertEquals("em chestunnav?", t("WhAt ArE YoU dOiNg?"))
    }

    @Test
    fun `all caps which movie you want`() {
        assertEquals("e sinima kavali?", t("WHICH MOVIE YOU WANT"))
    }

    @Test
    fun `how are you without question mark still suggests question`() {
        assertEquals("ela unnav?", t("how are you"))
    }

    @Test
    fun `what about you`() {
        assertEquals("nuvvu ela unnav?", t("What about you?"))
    }

    @Test
    fun `do you want coffee`() {
        assertEquals("neeku coffee kavala?", t("Do you want coffee?"))
    }

    @Test
    fun `unknown sentence returns nothing instead of hybrid garbage`() {
        // Must NEVER produce "which sinima nuvvu want"-style output.
        assertEquals("", t("the quick brown fox jumps"))
    }

    // ---- Style variants ----

    @Test
    fun `polite style`() {
        assertEquals("meeru em chestunnaru?", t("What are you doing?", TranslationStyle.POLITE))
    }

    @Test
    fun `formal style`() {
        assertEquals("meeru emi chestunnaru?", t("What are you doing?", TranslationStyle.FORMAL))
    }

    // ---- Strict romanization ----

    @Test
    fun `strict romanization`() {
        val res = runBlocking {
            engine.translate("What are you doing?", TranslationStyle.CASUAL, RomanizationStyle.STRICT)
        }
        assertEquals("eem cheestunnaavu?", res?.twinglish)
    }

    // ---- Code switching ----

    @Test
    fun `code switching leaves twinglish alone`() {
        assertEquals("nenu office ki velthunna", t("nenu office ki velthunna"))
    }

    // ---- Empty / edge input ----

    @Test
    fun `blank input returns empty`() {
        assertEquals("", t("   "))
    }
}
