package nl.eazyrecipes.cook

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class KitchenTimer(
    val id: String,
    val recipeId: String,
    val recipeTitle: String,
    /** Index van de stap (0-based), om de timer in de kookmodus bij de juiste stap te tonen. */
    val step: Int,
    val seconds: Int,
    /** Epoch-millis waarop hij afgaat. */
    val endAt: Long,
    /** Afgegaan maar nog niet weggetikt. */
    val ringing: Boolean = false,
) {
    fun remainingSeconds(now: Long): Long = ((endAt - now + 999) / 1000).coerceAtLeast(0)
    val label: String get() = "${recipeTitle} · stap ${step + 1}"
}

/**
 * Alle lopende kookwekkers. Eén bron van waarheid voor de kookmodus én de [TimerService]; bewaard in
 * SharedPreferences, zodat een timer blijft bestaan als Android de app tussendoor afsluit.
 */
class KitchenTimers(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _timers = MutableStateFlow(load())
    val timers: StateFlow<List<KitchenTimer>> = _timers

    /** Start een timer. Loopt dezelfde (recept, stap, duur) al, dan die terug (dubbel tikken start er geen twee). */
    fun start(recipeId: String, recipeTitle: String, step: Int, seconds: Int): KitchenTimer {
        _timers.value.firstOrNull { !it.ringing && it.recipeId == recipeId && it.step == step && it.seconds == seconds }?.let { return it }
        val t = KitchenTimer(UUID.randomUUID().toString(), recipeId, recipeTitle, step, seconds, clock() + seconds * 1000L)
        change { it + t }
        return t
    }

    /** Stoppen (lopend) of wegtikken (afgegaan). */
    fun dismiss(id: String) = change { list -> list.filterNot { it.id == id } }

    /** Zet verlopen timers op "gaat af". @return de timers die nú pas zijn afgegaan (voor de melding). */
    fun markDue(): List<KitchenTimer> {
        val now = clock()
        val fresh = _timers.value.filter { !it.ringing && it.endAt <= now }
        if (fresh.isNotEmpty()) change { list -> list.map { if (it.id in fresh.map(KitchenTimer::id)) it.copy(ringing = true) else it } }
        return fresh
    }

    /** Eerstvolgende tijdstip waarop een timer afgaat, of `null`. */
    fun nextEnd(): Long? = _timers.value.filter { !it.ringing }.minOfOrNull { it.endAt }

    private fun change(transform: (List<KitchenTimer>) -> List<KitchenTimer>) {
        _timers.update(transform)
        prefs.edit().putString(KEY, toJson(_timers.value)).apply()
    }

    /**
     * Timers die al lang geleden afliepen (de app was dicht en de service gestopt) laten we vallen:
     * anders gaat de wekker van gisteren af zodra je de app opent.
     */
    private fun load(): List<KitchenTimer> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KitchenTimer(
                o.getString("id"), o.getString("recipeId"), o.getString("recipeTitle"), o.getInt("step"),
                o.getInt("seconds"), o.getLong("endAt"), o.optBoolean("ringing"),
            )
        }.filter { it.endAt > clock() - STALE_MS }
    }.getOrDefault(emptyList())

    private fun toJson(list: List<KitchenTimer>): String = JSONArray(list.map { t ->
        JSONObject().put("id", t.id).put("recipeId", t.recipeId).put("recipeTitle", t.recipeTitle).put("step", t.step)
            .put("seconds", t.seconds).put("endAt", t.endAt).put("ringing", t.ringing)
    }).toString()

    private companion object {
        const val KEY = "timers"
        const val STALE_MS = 30 * 60 * 1000L
    }
}
