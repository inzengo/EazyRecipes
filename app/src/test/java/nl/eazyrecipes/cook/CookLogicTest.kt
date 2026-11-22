package nl.eazyrecipes.cook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CookLogicTest {

    private fun secs(text: String) = StepTimers.find(text).map { it.seconds }

    @Test
    fun `tijden in een stap`() {
        assertEquals(listOf(600), secs("Laat 10 minuten zachtjes pruttelen."))
        assertEquals(listOf(14 * 60), secs("Kook de sperziebonen gaar in ca. 14 min."))
        assertEquals("ondergrens van een bereik", listOf(8 * 60), secs("Bak 8-10 minuten tot goudbruin."))
        assertEquals(listOf(5 * 60), secs("5 à 7 minuten laten staan"))
        assertEquals(listOf(5400), secs("Stoof anderhalf uur op laag vuur."))
        assertEquals(listOf(1800), secs("Een half uur laten rijzen."))
        assertEquals(listOf(3600 + 15 * 60), secs("Zet 1 uur en 15 minuten in de oven."))
        assertEquals(listOf(5400), secs("Laat 1,5 uur garen."))
        assertEquals(listOf(30), secs("Blancheer 30 seconden."))
        assertEquals("twee tijden", listOf(300, 1200), secs("Fruit 5 minuten, voeg dan de rest toe en laat 20 minuten koken."))
        assertEquals("geen tijden", emptyList<Int>(), secs("Verwarm de oven voor op 200 graden en snijd 2 uien."))
        assertEquals("een moment is geen tijd", emptyList<Int>(), secs("Wacht een moment."))
        assertEquals("1/2 uur is geen 2 uur", listOf(1800), secs("Laat 1/2 uur rusten."))
        assertEquals(listOf(5400), secs("Laat 1½ uur garen."))
        assertEquals(listOf(1800), secs("Nog ½ uur in de oven."))
        assertEquals(listOf(900), secs("Laat een kwartier trekken."))
        assertEquals(listOf(2700), secs("Drie kwartier in de oven."))
        assertEquals(listOf(600), secs("Kook tien minuten."))
        assertEquals("'min of meer' is geen tijd", emptyList<Int>(), secs("Een min of meer gladde saus."))
        assertEquals("1 uur 15 min", StepTimers.label(4500))
        assertEquals("4:05", formatCountdown(245))
        assertEquals("1:02:05", formatCountdown(3725))
    }

    @Test
    fun `ingredienten bij een stap`() {
        val names = listOf("rijst", "kipfilet", "ui", "tomaten", "bakpoeder", "verse peterselie", "gehakt")
        assertEquals(listOf(0, 1, 2), ingredientsInStep("Kook de rijst. Snipper de ui en bak de kip.", names))
        assertEquals("meervoud", listOf(2, 3), ingredientsInStep("Snijd de uien en de tomaat.", names))
        assertEquals("'bak' is geen bakpoeder", listOf(1), ingredientsInStep("Bak de kip rondom bruin.", names))
        assertEquals(listOf(5, 6), ingredientsInStep("Rul het gehakt en garneer met peterselie.", names))
    }

    @Test
    fun `kookwekkers - starten, afgaan, wegtikken en bewaren`() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("timers-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        var now = 1_000_000L
        val k = KitchenTimers(prefs) { now }

        val a = k.start("r1", "Kip kerrie", step = 1, seconds = 600)
        val b = k.start("r1", "Kip kerrie", step = 2, seconds = 60)
        assertEquals(now + 60_000, k.nextEnd())
        assertEquals(600L, a.remainingSeconds(now))

        now += 60_000
        assertEquals(listOf(b.id), k.markDue().map { it.id })
        assertTrue(k.timers.value.first { it.id == b.id }.ringing)
        assertEquals("afgegaan meldt maar één keer", emptyList<String>(), k.markDue().map { it.id })
        assertEquals(a.endAt, k.nextEnd())

        // Na een herstart van de app staan ze er nog.
        assertEquals(k.timers.value, KitchenTimers(prefs) { now }.timers.value)

        assertEquals("dubbel tikken start er geen twee", a.id, k.start("r1", "Kip kerrie", step = 1, seconds = 600).id)

        // Een uur later (app was dicht): timers die > 30 min geleden afliepen (a en b) vallen weg bij laden, c loopt nog.
        val c = k.start("r1", "Kip kerrie", step = 0, seconds = 7200)
        now += 60 * 60_000
        assertEquals(listOf(c.id), KitchenTimers(prefs) { now }.timers.value.map { it.id })

        k.dismiss(b.id)
        k.dismiss(a.id)
        k.dismiss(c.id)
        assertTrue(k.timers.value.isEmpty())
        assertNull(k.nextEnd())
    }
}
