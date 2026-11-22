package nl.eazyrecipes.ui.pantry

/**
 * Een ingrediënt dat je in "In huis" kunt aantikken.
 * [variants] zijn hele woorden of woordgroepen (zonder hoofdletters/accenten vergeleken), zodat "kippenbouillon"
 * niet als "kip" telt. Samenstellingen die wél horen te tellen ("kipfilet") staan er daarom expliciet in.
 * Bevat een regel een van de [excludes], dan telt dit item niet ("lente-ui" is geen ui).
 */
data class PantryItem(
    val key: String,
    val label: String,
    internal val variants: List<List<String>>,
    internal val excludes: List<List<String>> = emptyList(),
)

private fun item(key: String, label: String, vararg variants: String, excludes: List<String> = emptyList()) =
    PantryItem(key, label, (listOf(key) + variants).map(::pantryTokens).distinct(), excludes.map(::pantryTokens))

/** De vaste lijst. Volgorde is alleen een tiebreak; de chips staan op hoe vaak ze in je recepten voorkomen. */
val PantryCatalog: List<PantryItem> = listOf(
    // groente en fruit
    item("ui", "Ui", "uien", "uitje", "uitjes", excludes = listOf("lente ui", "lente uien", "lente uitje", "lente uitjes")),
    item("lente-ui", "Lente-ui", "lente ui", "lente uien", "lente uitje", "lente uitjes", "lenteui", "lenteuitje", "lenteuitjes", "bosui", "bosuien", "bosuitjes"),
    item("knoflook", "Knoflook", "knoflookteen", "knoflookteentje", "knoflookteentjes"),
    item("sjalot", "Sjalot", "sjalotten", "sjalotje", "sjalotjes"),
    item("tomaat", "Tomaat", "tomaten", "tomaatjes", "cherrytomaten", "cherrytomaatjes", "trostomaten", "snoeptomaatjes", "tomatenblokjes", "passata"),
    item("paprika", "Paprika", "paprikas"),
    item("wortel", "Wortel", "wortels", "wortelen", "worteltjes", "winterpeen", "peen", "bospeen"),
    item("aardappel", "Aardappel", "aardappels", "aardappelen", "kriel", "krieltjes", "krielaardappels", "krielaardappeltjes", excludes = listOf("zoete aardappel", "zoete aardappels", "zoete aardappelen")),
    item("zoete-aardappel", "Zoete aardappel", "zoete aardappel", "zoete aardappels", "zoete aardappelen", "bataat", "bataten"),
    item("courgette", "Courgette", "courgettes"),
    item("aubergine", "Aubergine", "aubergines"),
    item("champignons", "Champignons", "champignon", "kastanjechampignons", "paddenstoelen"),
    item("spinazie", "Spinazie", "babyspinazie", "bladspinazie"),
    item("broccoli", "Broccoli"),
    item("bloemkool", "Bloemkool"),
    item("prei", "Prei"),
    item("komkommer", "Komkommer"),
    item("sla", "Sla", "ijsbergsla", "romainesla", "veldsla", "slamix", "kropsla", "rucola"),
    item("avocado", "Avocado", "avocados"),
    item("citroen", "Citroen", "citroenen", "citroensap", "citroenrasp"),
    item("limoen", "Limoen", "limoenen", "limoensap"),
    item("appel", "Appels", "appels", "appelen"),
    item("banaan", "Banaan", "bananen"),
    item("gember", "Gember", "gemberwortel"),
    item("peterselie", "Peterselie"),
    item("koriander", "Koriander"),
    item("basilicum", "Basilicum"),
    // vlees en vis
    item("kip", "Kip", "kipfilet", "kipfilets", "kippenfilet", "kippendij", "kippendijen", "kippendijfilet", "kippendijfilets", "kipdijfilet", "kipdijfilets", "kippendijtjes", "kippenpootjes", "kippenbout", "kipschnitzel", "kipschnitzels", "kippenborst", "kippenpoten", "kippenbouten", "kippenvleugels", "kippenvleugeltjes", "kipstukjes", "kipreepjes"),
    item("gehakt", "Gehakt", "rundergehakt", "varkensgehakt", "kipgehakt", "gehaktballetjes"),
    item("spekjes", "Spekjes", "spek", "spekblokjes", "spekreepjes", "ontbijtspek", "bacon", "pancetta"),
    item("rundvlees", "Rundvlees", "biefstuk", "biefstukken", "riblappen", "runderlappen", "sukadelappen", "stoofvlees", "rosbief", "entrecote"),
    item("varkensvlees", "Varkensvlees", "varkenshaas", "varkensfilet", "karbonade", "karbonades", "speklappen", "procureur", "varkensreepjes"),
    item("worst", "Worst", "rookworst", "chorizo", "braadworst", "worstjes"),
    item("zalm", "Zalm", "zalmfilet", "zalmfilets", "zalmmoot", "zalmmoten"),
    item("witvis", "Witvis", "kabeljauw", "kabeljauwfilet", "koolvis", "koolvisfilet", "pangasius", "tilapia", "schol", "kibbeling", "visfilet", "visfilets"),
    item("garnalen", "Garnalen", "gamba", "gambas", "scampi", "scampis"),
    item("tonijn", "Tonijn"),
    // zuivel en eieren
    item("eieren", "Eieren", "ei", "eitje", "eitjes", "eidooier", "eidooiers", "eiwit", "eiwitten"),
    item("boter", "Boter", "roomboter"),
    item("melk", "Melk"),
    item("room", "Room", "kookroom", "slagroom", excludes = listOf("zure room")),
    item("creme-fraiche", "Crème fraîche", "creme fraiche", "cremefraiche", "zure room"),
    item("yoghurt", "Yoghurt", "yogurt"),
    item("kaas", "Kaas", "geraspte kaas", "cheddar", "gruyere", "emmentaler", "goudse"),
    item("parmezaan", "Parmezaan", "parmezaanse", "parmigiano", "pecorino", "grana padano"),
    item("mozzarella", "Mozzarella", "burrata"),
    item("feta", "Feta", "fetakaas"),
    // voorraadkast
    item("kokosmelk", "Kokosmelk", "kokosroom"),
    item("rijst", "Rijst", "basmatirijst", "pandanrijst", "zilvervliesrijst", "risottorijst", "jasmijnrijst", "sushirijst", "arborio"),
    item("pasta", "Pasta", "spaghetti", "penne", "macaroni", "fusilli", "tagliatelle", "linguine", "lasagnebladen", "farfalle", "orzo", "rigatoni", "tortellini", "gnocchi"),
    item("noedels", "Noedels", "mie", "mienestjes", "rijstnoedels", "eiernoedels", "udon", "ramen"),
    item("couscous", "Couscous", "parelcouscous", "bulgur", "quinoa"),
    item("brood", "Brood", "stokbrood", "ciabatta", "boterhammen", "broodjes", "pistolets"),
    item("wraps", "Wraps", "wrap", "tortilla", "tortillas"),
    item("bloem", "Bloem", "tarwebloem", "patentbloem", "bakmeel"),
    item("suiker", "Suiker", "kristalsuiker", "basterdsuiker", "poedersuiker", "rietsuiker"),
    item("bouillon", "Bouillon", "bouillonblokje", "bouillonblokjes", "bouillontablet", "bouillonpoeder", "kippenbouillon", "groentebouillon", "runderbouillon", "visbouillon", "bouillontabletten",
        "kippenbouillonblokje", "kippenbouillonblokjes", "groentebouillonblokje", "groentebouillonblokjes", "runderbouillonblokje", "runderbouillonblokjes",
        "groentebouillontablet", "kippenbouillontablet", "runderbouillontablet", "groentebouillonpoeder", "kippenfond", "groentefond", "runderfond"),
    item("tomatenpuree", "Tomatenpuree"),
    item("kikkererwten", "Kikkererwten"),
    item("bonen", "Bonen", "kidneybonen", "witte bonen", "zwarte bonen", "bruine bonen"),
    item("sperziebonen", "Sperziebonen", "boontjes", "haricots verts"),
    item("linzen", "Linzen"),
    item("mais", "Maïs", "maiskorrels"),
    item("erwten", "Erwten", "doperwten", "doperwtjes", "erwtjes"),
    item("honing", "Honing"),
    item("mosterd", "Mosterd"),
    item("sojasaus", "Sojasaus", "ketjap", "tamari"),
    item("noten", "Noten", "walnoten", "cashewnoten", "pijnboompitten", "amandelen", "pecannoten", "hazelnoten", "pinda", "pindas"),
    item("chocolade", "Chocolade", "chocola", "chocoladestukjes"),
    item("kerrie", "Kerrie", "kerriepoeder", "curry", "currypoeder"),
)
