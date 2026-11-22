package nl.eazyrecipes.importer

/**
 * Maakt van herkende tekst (OCR van een foto of screenshot) een concept-recept.
 *
 * Het is een gok die de gebruiker daarna in het bewerkscherm nakijkt. Daarom de regel: **geen tekst kwijtraken**.
 * Elke regel komt in de titel, de beschrijving, de ingrediënten of de stappen terecht; alleen een statusbalk
 * (tijd, batterij) en een paginanummer bovenaan of onderaan laten we weg.
 * - Kopjes als "Ingrediënten" en "Bereiding" (ook Engels) zetten de modus om; de volgorde maakt niet uit.
 *   Ontbreekt een van de twee kopjes, dan gokken we die overgang zelf.
 * - Gokken: korte regels met een hoeveelheid zijn ingrediënten, een lange zin is een stap.
 * - OCR breekt zinnen af op de regelbreedte van de foto: stappen plakken we weer aan elkaar tot een zin eindigt
 *   of een nieuw nummer begint.
 */
object PhotoRecipeParser {

    private enum class Mode { INTRO, INGREDIENTS, STEPS }

    private val ingredientHeader = Regex(
        """^(ingredi[eë]nten|ingredienten|ingredients?|benodigdheden|boodschappen(lijst)?|wat heb je nodig|dit heb je nodig|je hebt nodig|nodig|you(?:'ll)? need)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val stepHeader = Regex(
        """^(bereiding(swijze)?|werkwijze|zo maak je (het|dit)|aan de slag|instructies|methode|preparation|method|instructions|directions|steps)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val servingsRx = Regex(
        """(?:voor\s+)?(\d{1,2})\s*(?:personen|persoon|pers\.?|porties|portie|people|servings)\b|\b(?:serves|porties?|personen)\s*:?\s*(\d{1,2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val minutesRx = Regex("""(\d{1,3})\s*(?:min(?:uten|uut|s|\.)?|minutes?)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val hoursRx = Regex("""(\d{1,2}(?:[.,]5)?)\s*(?:uur|u|hours?|hrs?)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    /** Woorden rond een tijd of portie die zelf geen inhoud zijn: "Bereidingstijd: ca. 30 min". */
    private val metaWords = Regex(
        """\b(voor|ca|circa|ongeveer|bereidingstijd|kooktijd|oventijd|tijd|totaal|klaar in|about|approx|total|time|ready in|prep|cook|serves|en|and)\b""",
        RegexOption.IGNORE_CASE,
    )
    /** "Bereidingstijd", "Kooktijd", "Prep time"; niet "altijd" of "tijdens". */
    private val timeKeyword = Regex("""\b(\p{L}*(?<!al)tijd|klaar in|(prep|cook|cooking|total|baking)?\s?time|ready in)\b""", RegexOption.IGNORE_CASE)
    private val totalKeyword = Regex("""\b(totaal|totale|total)\b""", RegexOption.IGNORE_CASE)

    private val bullet = Regex("""^[•·▪◦○●□■\-–—*✓✔>]+\s*""")
    private val stepNumber = Regex("""^(?:(?:stap|step)\s*)?(\d{1,2})\s*[.):](?!\d)\s*|^(?:stap|step)\s*(\d{1,2})\b\s*[:.\-]?\s*""", RegexOption.IGNORE_CASE)
    private val units =
        "g|gr|gram|grams|kg|kilo|mg|ml|cl|dl|l|liter|el|tl|eetlepels?|theelepels?|kopjes?|stuks?|st|teentjes?|tenen|teen|blikjes?|blik|pakjes?|pak|zakjes?|zak|bosjes?|bos|takjes?|snufjes?|snuf|scheutjes?|plakjes?|plakken|handjes?|handvol|cups?|tbsp|tsp|oz|lb|lbs|pinch"
    private val quantityStart = Regex("""^(?:ca\.?\s*|±\s*|circa\s+)?(?:\d+(?:[.,/]\d+)?|[½¼¾⅓⅔⅛])(?:\s*[-–]\s*\d+)?\s*(?:($units)\b\.?)?""", RegexOption.IGNORE_CASE)
    private val unitWord = Regex("""\b($units)\b""", RegexOption.IGNORE_CASE)
    /** Een ingrediëntregel die zo eindigt, loopt door op de volgende regel ("200 g feta, in" + "blokjes"). */
    private val connectorEnd = Regex("""\s(in|en|of|met|voor|van|tot|op|om|de|het|een|and|or|with|to|for|the|a)$""", RegexOption.IGNORE_CASE)
    /** Statusbalk van een screenshot ("12:41 ✦ 87%"): een tijd, en verder geen letters. */
    private val statusBar = Regex("""^\d{1,2}:\d{2}[^\p{L}]*$""")
    /** Alleen tekens zonder betekenis (lijntjes, puntjes). Cijfers en "½" tellen wél als inhoud. */
    private val noInk = Regex("""^[^\p{L}\p{N}]+$""")
    private val pageNumber = Regex("""^(?:\d{1,3}|pagina\s*\d+|page\s*\d+|p\.\s*\d+)$""", RegexOption.IGNORE_CASE)
    private val sentenceEnd = Regex("""[.!?](\s|$)""")

    fun parse(text: String): ParsedRecipe? {
        val lines = cleanLines(text)
        if (lines.none { it.any(Char::isLetter) }) return null

        val hasIngredientHeader = lines.any(::isIngredientHeader)
        val hasStepHeader = lines.any(::isStepHeader)
        var mode = Mode.INTRO
        var title: String? = null
        var introLines = 0
        val intro = mutableListOf<String>()
        val ingredients = mutableListOf<String>()
        val stepLines = mutableListOf<String>()
        var servings: Int? = null
        var totalMinutes: Int? = null
        /** De regel waar [totalMinutes] vandaan komt: wordt die overschreven, dan gaat de regel naar de beschrijving. */
        var timeLine: String? = null

        /**
         * Porties of tijd uit [line] halen. `true` = de regel is "op": hij bevat niets anders en de waarde is bewaard.
         * Een tweede tijd- of portieregel ("Kooktijd: 30 min" na "Bereidingstijd: 15 min") komt in de beschrijving,
         * anders raken we hem kwijt. Een totaaltijd wint van een eerdere deeltijd.
         */
        fun takeMeta(line: String, minutesToo: Boolean): Boolean {
            val metaOnly = isMetaOnly(line)
            var stored = false
            servingsRx.find(line)?.let { s ->
                if (servings == null) { servings = s.groupValues[1].ifEmpty { s.groupValues[2] }.toIntOrNull(); stored = true }
            }
            val m = if (minutesToo && (metaOnly || timeKeyword.containsMatchIn(line))) minutesOf(line) else null
            if (m != null && (totalMinutes == null || totalKeyword.containsMatchIn(line))) {
                timeLine?.let { intro += it }
                totalMinutes = m
                timeLine = if (metaOnly) line else null
                stored = true
            }
            if (!metaOnly) return false
            if (!stored && (servingsRx.containsMatchIn(line) || minutesOf(line) != null)) intro += line
            return stored || servingsRx.containsMatchIn(line) || minutesOf(line) != null
        }

        for (line in lines) {
            val header = when {
                isIngredientHeader(line) -> Mode.INGREDIENTS
                isStepHeader(line) -> Mode.STEPS
                else -> null
            }
            if (header != null) {
                mode = header
                val rest = restAfterHeader(line, if (header == Mode.INGREDIENTS) ingredientHeader else stepHeader)
                // "Ingrediënten voor 4 personen", "Bereiding: 45 minuten": waarde bewaren; andere tekst erachter is inhoud.
                if (rest != null && !takeMeta(rest, minutesToo = true)) {
                    if (header == Mode.INGREDIENTS) addIngredient(ingredients, rest) else stepLines += rest
                }
                continue
            }
            if (mode != Mode.STEPS && takeMeta(line, minutesToo = mode == Mode.INTRO)) continue

            if (mode == Mode.INTRO) {
                // Zonder kopje zelf de overgang vinden, ook al vóór de titel (een screenshot begint soms midden in het recept).
                mode = when {
                    // Met alleen een ingrediëntenkopje is tekst daarvóór de inleiding, geen bereiding.
                    !hasStepHeader && !hasIngredientHeader && isStepLike(line) -> Mode.STEPS
                    // Bovenaan kan het de titel zijn ("Pak choi uit de wok"): dan alleen met een hoeveelheid of opsommingsteken.
                    !hasIngredientHeader && isIngredientLike(line, strict = title == null && introLines < 3) -> Mode.INGREDIENTS
                    else -> Mode.INTRO
                }
                if (mode == Mode.INTRO) {
                    // Een titel staat bovenaan: alleen de eerste paar regels komen in aanmerking.
                    if (title == null && introLines < 3 && looksLikeTitle(line)) title = line else intro += line
                    introLines++
                    continue
                }
            }
            if (mode == Mode.INGREDIENTS && !hasStepHeader && isStepLike(line)) mode = Mode.STEPS
            when (mode) {
                Mode.INGREDIENTS -> addIngredient(ingredients, line)
                Mode.STEPS -> stepLines += line
                Mode.INTRO -> Unit
            }
        }

        // Alleen een titel en tekst, niets herkend: de tekst is dan vast de bereiding.
        if (ingredients.isEmpty() && stepLines.isEmpty() && intro.isNotEmpty()) {
            stepLines += intro
            intro.clear()
        }

        return ParsedRecipe(
            title = title.orEmpty(),
            // Niet inkorten: een lange inleiding is ook tekst van de gebruiker.
            description = intro.joinToString(" ").ifBlank { null },
            servings = servings,
            totalMinutes = totalMinutes,
            ingredients = ingredients,
            steps = mergeSteps(stepLines).map { ParsedStep(it) },
        )
    }

    /** Lege regels, statusbalk en tekens zonder inhoud weg; een paginanummer alleen als eerste of laatste regel. */
    private fun cleanLines(text: String): List<String> {
        val lines = text.lines().map { it.replace(' ', ' ').trim() }
            .filter { it.isNotEmpty() && !statusBar.matches(it) && !noInk.matches(it) }
        return lines.filterIndexed { i, l -> !((i == 0 || i == lines.lastIndex) && pageNumber.matches(l)) }
    }

    private fun isIngredientHeader(line: String) = isHeader(line, ingredientHeader)
    private fun isStepHeader(line: String) = isHeader(line, stepHeader)

    /**
     * Een kopje: kort vóór een dubbele punt ("Bereiding: Klop de eieren…"), of het kopwoord met hooguit een
     * aanvulling ("Ingrediënten voor 4 personen", "Ingredients (serves 4)"). "Bereiding Snijd de ui" is een zin.
     */
    private fun isHeader(line: String, rx: Regex): Boolean {
        if (':' in line) {
            val head = line.substringBefore(':').trim()
            return head.length <= 40 && rx.containsMatchIn(head)
        }
        if (line.length > 40 || line.endsWith(".")) return false
        val m = rx.find(line) ?: return false
        val rest = line.substring(m.range.last + 1).trim()
        return rest.isEmpty() || rest.first() == '(' || rest.first().isLowerCase() || rest.first().isDigit()
    }

    /** Wat er na het kopje staat: "Ingrediënten: 2 eieren" → "2 eieren"; `null` als er niets staat. */
    private fun restAfterHeader(line: String, rx: Regex): String? {
        val rest = if (':' in line) line.substringAfter(':') else rx.find(line)?.let { line.substring(it.range.last + 1) }.orEmpty()
        return rest.trim().trim('(', ')').trim().ifEmpty { null }
    }

    /** Alleen porties/tijd en vulwoorden, zoals "4 personen · 30 min" of "Bereidingstijd: ca. 1 uur". */
    private fun isMetaOnly(line: String): Boolean {
        var rest = line
        for (rx in listOf(servingsRx, minutesRx, hoursRx, metaWords)) rest = rx.replace(rest, " ")
        return rest.count(Char::isLetter) < 2
    }

    private fun looksLikeTitle(line: String) =
        line.length in 3..80 && line.count(Char::isLetter) >= 3 && !quantityStart.containsMatchIn(line) && !line.endsWith(".")

    private fun isIngredientLike(line: String, strict: Boolean = false): Boolean {
        val l = bullet.replace(line, "")
        if (stepNumber.containsMatchIn(l) && l.length > 40) return false
        return l.length <= 60 && (quantityStart.find(l)?.value?.isNotBlank() == true || bullet.containsMatchIn(line) || (!strict && unitWord.containsMatchIn(l)))
    }

    /**
     * Een stap: "1. Verwarm de oven…", of een lange regel die als zin leest. Een lange ingrediëntregel
     * ("Een handvol basilicum, grof gescheurd, plus extra om te garneren") heeft meestal geen punt.
     */
    private fun isStepLike(line: String): Boolean {
        val l = bullet.replace(line, "")
        stepNumber.find(l)?.let { m ->
            val after = l.substring(m.value.length)
            if (after.length > 15 && after.first().isUpperCase()) return true
        }
        if (quantityStart.find(l)?.value?.isNotBlank() == true) return false
        return l.contains(' ') && ((l.length > 60 && sentenceEnd.containsMatchIn(l)) || l.length > 90)
    }

    private fun minutesOf(line: String): Int? {
        val h = hoursRx.find(line)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val m = minutesRx.find(line)?.groupValues?.get(1)?.toIntOrNull()
        if (h == null && m == null) return null
        return ((h ?: 0.0) * 60).toInt() + (m ?: 0)
    }

    /** Elke regel is een ingrediënt, behalve een afgebroken vervolg ("200 g kip, in" + "blokjes"). */
    private fun addIngredient(list: MutableList<String>, raw: String) {
        val line = bullet.replace(raw, "").trim()
        if (line.isEmpty()) return
        val prev = list.lastOrNull()
        val startsOwn = bullet.containsMatchIn(raw) || quantityStart.find(line)?.value?.isNotBlank() == true
        val continues = prev != null && !startsOwn && (
            prev.endsWith(',') || prev.endsWith('-') || prev.endsWith('(') || connectorEnd.containsMatchIn(prev) ||
                prev.count { it == '(' } > prev.count { it == ')' } || line.startsWith(")")
            )
        if (continues) list[list.lastIndex] = join(prev!!, line) else list += line
    }

    /** Losse OCR-regels → stappen: een nieuwe stap bij een nummer of opsommingsteken, of na een afgesloten zin. */
    internal fun mergeSteps(lines: List<String>): List<String> {
        fun numberOf(l: String) = stepNumber.find(l)?.takeIf { l.length > it.value.length }
        // Genummerde stappen: dan begint alleen een nummer een nieuwe stap (een stap kan uit meer zinnen bestaan).
        val numberedList = lines.count { numberOf(it) != null } >= 2
        val steps = mutableListOf<String>()
        for (raw in lines) {
            val numbered = numberOf(raw)
            val bulleted = bullet.containsMatchIn(raw)
            val text = when {
                numbered != null -> raw.substring(numbered.value.length)
                bulleted -> bullet.replace(raw, "")
                else -> raw
            }.trim()
            if (text.isEmpty()) continue
            val prev = steps.lastOrNull()
            val newStep = prev == null || numbered != null || bulleted || (!numberedList && prev.last() in ".!?")
            if (newStep) steps += text else steps[steps.lastIndex] = join(prev!!, text)
        }
        return steps
    }

    /** Twee afgebroken regels samenvoegen; "door-" + "roeren" wordt "doorroeren". */
    private fun join(a: String, b: String): String =
        if (a.endsWith('-') && b.firstOrNull()?.isLowerCase() == true) a.dropLast(1) + b else "$a $b"
}
