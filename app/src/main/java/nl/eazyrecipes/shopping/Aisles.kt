package nl.eazyrecipes.shopping

import nl.eazyrecipes.ui.pantry.pantryTokens

/** Afdelingen in de supermarkt, in de volgorde waarin je er (ongeveer) langs loopt. */
enum class Aisle(val label: String) {
    GROENTE("Groente & fruit"),
    VLEES("Vlees & vis"),
    ZUIVEL("Zuivel & eieren"),
    BROOD("Brood"),
    VOORRAAD("Voorraadkast"),
    KRUIDEN("Kruiden & sauzen"),
    OVERIG("Overig"),
}

object Aisles {

    /** Per item uit de "In huis"-lijst ([nl.eazyrecipes.ui.pantry.PantryCatalog]) zijn afdeling. */
    internal val byPantryKey: Map<String, Aisle> = buildMap {
        listOf(
            "ui", "lente-ui", "knoflook", "sjalot", "tomaat", "paprika", "wortel", "aardappel", "zoete-aardappel", "courgette",
            "aubergine", "champignons", "spinazie", "broccoli", "bloemkool", "prei", "komkommer", "sla", "avocado", "citroen",
            "limoen", "appel", "banaan", "gember", "peterselie", "koriander", "basilicum", "sperziebonen",
        ).forEach { put(it, Aisle.GROENTE) }
        listOf("kip", "gehakt", "spekjes", "rundvlees", "varkensvlees", "worst", "zalm", "witvis", "garnalen")
            .forEach { put(it, Aisle.VLEES) }
        listOf("eieren", "boter", "melk", "room", "creme-fraiche", "yoghurt", "kaas", "parmezaan", "mozzarella", "feta")
            .forEach { put(it, Aisle.ZUIVEL) }
        listOf("brood", "wraps").forEach { put(it, Aisle.BROOD) }
        listOf(
            "kokosmelk", "rijst", "pasta", "noedels", "couscous", "bloem", "suiker", "bouillon", "tomatenpuree", "kikkererwten",
            "bonen", "linzen", "mais", "erwten", "noten", "chocolade", "tonijn",
        ).forEach { put(it, Aisle.VOORRAAD) }
        listOf("honing", "mosterd", "sojasaus", "kerrie").forEach { put(it, Aisle.KRUIDEN) }
    }

    /**
     * Voor wat niet in de "In huis"-lijst staat: het laatste woord eindigt op… In het Nederlands staat het hoofdwoord
     * achteraan ("paprikapoeder" is poeder, "roomkaas" is kaas), dus het langste passende woordeinde wint.
     */
    private val endings: List<Pair<String, Aisle>> = buildList {
        fun add(aisle: Aisle, vararg words: String) = words.forEach { add(it to aisle) }
        add(
            Aisle.GROENTE, "groente", "groenten", "sla", "kool", "bieslook", "tijm", "rozemarijn", "munt", "dille", "salie", "bessen",
            "aardbeien", "aardbei", "peer", "peren", "sinaasappel", "sinaasappels", "mango", "ananas", "druiven", "radijs", "radijsjes",
            "biet", "bieten", "venkel", "selderij", "asperges", "pompoen", "peultjes", "tauge", "rabarber", "frambozen", "bramen",
            "spruitjes", "andijvie", "pastinaak", "postelein", "waterkers", "chilipeper", "chilipepers", "rawit", "citroengras",
            "fruit", "kiwi", "kiwis", "meloen", "granaatappel", "vijgen", "pruimen", "kersen", "boontjes", "peterselie", "kruidenmix",
        )
        add(
            Aisle.VLEES, "vlees", "filet", "filets", "ham", "kalkoen", "lamsvlees", "lamsrack", "eend", "eendenborst", "worst", "worstjes",
            "gehakt", "vis", "mosselen", "inktvis", "makreel", "haring", "forel", "kreeft", "krab", "zalm", "kip", "schnitzel",
            "schnitzels", "spek", "salami", "chorizo", "prosciutto", "carpaccio", "shoarma", "kebab", "hamburgers", "hamburger",
        )
        add(
            Aisle.ZUIVEL, "kaas", "melk", "room", "yoghurt", "kwark", "boter", "ei", "eieren", "mascarpone", "ricotta", "skyr", "vla",
            "karnemelk", "halloumi", "brie", "camembert", "geitenkaas", "zuivel",
        )
        add(Aisle.BROOD, "brood", "broodjes", "bolletjes", "croissants", "pita", "pitabroodjes", "naan", "beschuit", "toast", "bagels")
        add(
            Aisle.KRUIDEN, "poeder", "kruiden", "specerijen", "komijn", "kaneel", "nootmuskaat", "oregano", "saus", "azijn", "ketchup",
            "mayonaise", "pesto", "sambal", "olie", "peper", "zout", "kruidnagel", "kruidnagels", "laurier", "laurierblaadjes",
            "vanille", "vanillesuiker", "kurkuma", "paprikapoeder", "chilivlokken", "dressing", "siroop", "stroop", "tahin", "miso",
            "harissa", "currypasta", "mosterd",
        )
        add(
            Aisle.VOORRAAD, "bloem", "meel", "suiker", "pasta", "rijst", "bonen", "noten", "zaden", "pitten", "havermout", "cacao",
            "bakpoeder", "gist", "rozijnen", "maizena", "paneermeel", "crackers", "koekjes", "chips", "bouillon", "fond", "blokje",
            "blokjes", "linzen", "puree", "passata", "kokosmelk", "wijn", "bier", "frisdrank", "sap", "thee", "koffie", "jam",
            "pindakaas", "chocolade", "hagelslag", "muesli", "granola", "polenta", "kikkererwten", "tonijn", "ansjovis", "kappertjes",
            "olijven", "augurken", "zongedroogde",
        )
    }.sortedByDescending { it.first.length }

    /**
     * Afdeling voor een ingrediënt. [pantryKeys] = de "In huis"-items die in de regel voorkomen (die weten het zeker);
     * anders op het laatste woord van [name], dan op elk ander woord; anders [Aisle.OVERIG].
     */
    fun of(name: String, pantryKeys: Collection<String> = emptyList()): Aisle {
        pantryKeys.firstNotNullOfOrNull { byPantryKey[it] }?.let { return it }
        val words = pantryTokens(name)
        if (words.isEmpty()) return Aisle.OVERIG
        for (w in listOf(words.last()) + words.dropLast(1).reversed()) {
            endings.firstOrNull { (end, _) -> w.endsWith(end) }?.let { return it.second }
        }
        return Aisle.OVERIG
    }
}
