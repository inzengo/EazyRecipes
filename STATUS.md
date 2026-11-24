# Status — EazyRecipes

_Laatst bijgewerkt: 2026-11-24_

## Waar staan we

| Fase | Status |
|---|---|
| 0 — Fundament (repo, skelet, toolchain, eerste APK) | ✅ klaar |
| 1 — Design (mockups, kleursysteem, navigatie) | ✅ klaar (v0.2), goedgekeurd op hoofdlijnen |
| 2 — Data + import | ✅ klaar |
| 3 — Bibliotheek & vinden | ✅ klaar (3a, 3b, 3c) — nog niet op telefoon getest |
| 4 — Koken, ordenen & delen | ✅ klaar (4a–4e) — **nog niet op de telefoon getest** |
| 5 — Overige import | ✅ klaar (zelf invoeren, meerdere links, bladwijzers, foto/OCR) — **nog niet op de telefoon getest** |
| 3–5 en later | zie [PLAN.md](PLAN.md) |

## Wat er nu is

- **Repo:** Kotlin + Jetpack Compose-skelet (`app/`). Het delen-menu ("Delen → EazyRecipes") vangt al een link op en toont die.
- **APK:** `assembleDebug` werkt. Output: `E:\DevTools\builds\EazyRecipes\app\outputs\apk\debug\app-debug.apk`.
  Nog **niet** op een telefoon getest.
- **Datamodel (Room 2.7, KSP2), schema v4:** `app/src/main/java/nl/eazyrecipes/data/`. Tabellen recipe, ingredient, step, tag, recipe_tag, collection, collection_recipe, category, recipe_category, plus de FTS4-index `recipe_fts`. Migraties v1 → v2 → v3 → v4 in `EazyDatabase.kt` (getest met een echte v1-database uit `app/schemas/.../1.json`, `MigrationTest`). Elke rij heeft `createdAt`/`updatedAt`/`deletedAt`; entiteiten hebben een UUID. `RecipeRepository` is de enige schrijfingang en houdt timestamps en zoekindex bij.
  Zoeken: elk woord als prefix ("kipf" vindt "kipfilet"), zonder accenten ("creme" vindt "crème"). Schema-export staat in `app/schemas/`.
- **Importer:** `app/src/main/java/nl/eazyrecipes/importer/`.
  - `JsonLdRecipeParser` (HTML → `ParsedRecipe`), ISO-duur → minuten, en `MainIngredientGuesser` (eerste gok voor vlees/vis/vega/vegan/zoet).
  - `RecipeImporter`: link → recept. Eerst HTTP (`HttpPageFetcher`, OkHttp). Bij 401/403/429/503, of als de HTML geen recept bevat, gaat hij via `WebViewPageFetcher` (onzichtbare WebView, voor Cloudflare-sites zoals eefkooktzo.nl). Haalt `utm_*`/`fbclid`/… uit de link.
  - `ImageStore`: slaat de receptfoto lokaal op (max. 15 MB, alleen `image/*`).
- **Tests:** `./gradlew testDebugUnitTest`, 138 tests, alle groen (2026-11-24). Ze testen op twee echte leukerecepten.nl-pagina's (`app/src/test/resources/pages/`, ingekort tot de JSON-LD) en een reeks varianten van andere sites. De database wordt getest met Robolectric.
- **Schermen (Compose, design v0.2):**
  - Bibliotheek: grote kop, masonry-raster met kaarten, lege toestand, +-knop → "link plakken" (vult zelf in vanaf het klembord).
  - Importscherm: ophalen → voorbeeld waarin alles bewerkbaar is (soort, titel, tijd, porties, ingrediënten, stappen) → opslaan. Bij een link die al bestaat verschijnt een melding "staat al in je bibliotheek".
  - Eenvoudig detailscherm: favoriet, bronlink, **bewerken** (dezelfde editor als bij importeren), verwijderen.
  - Delen-menu → importscherm.
- **Design-systeem:** `ui/theme/Theme.kt` (kleuren, contextkleuren, typografie), `ui/components/`. Fonts staan als variabele TTF in `res/font` (licenties in `docs/licenses/`). De Phosphor-iconen zijn als vectoren omgezet uit de mockup (`res/drawable/ph_*.xml`).
- **Screenshots zonder telefoon:** `ScreenshotTest` tekent de schermen naar `app/build/screenshots/` (Robolectric, offline).
- **Toolchain:** JDK 17, Android SDK, Gradle-cache en build-output staan allemaal op `E:\DevTools`. Zie [docs/SETUP-WINDOWS.md](docs/SETUP-WINDOWS.md).
- **Design-mockups:** klikbaar canvas → https://claude.ai/artifact/6UDLioCa2cawBMaDYd1Wjm
  (privé). De broncode staat in `docs/design/mockups/` (`Main.dc.html` = prototype v0.2, `Versie1.dc.html` = eerste versie ter vergelijking).

## Design-besluiten (v0.2)

- **Lettertypes:** Bricolage Grotesque (koppen, cijfers, knoppen) + Figtree (tekst). Beide zijn gratis Google Fonts.
- **Kleuren:**
  - basis wit `#FFFFFF` / grijs `#F2F2F0` / inkt `#121212`
  - merkkleur lavendel `#9B8CFF`
  - per context een fel vlak met **zwarte tekst** (contrast ≥ 6:1):

  | Context | Kleur |
  |---|---|
  | vlees | `#FF5A4A` |
  | vis | `#4D9DFF` |
  | vega | `#3DD66F` |
  | vegan | `#47E3BC` |
  | zoet | `#FF8AD4` |
  | snel | `#FFC738` |

- **Contextkleur:** kies je een categorie, dan kleurt de kop mee (met een groot icoon als watermerk), net als de navigatie en de +-knop. Receptdetail en kookmodus nemen de kleur van het recept over.
- **Iconen:** Phosphor Icons (MIT), fill-variant. Vlees = koe (Phosphor heeft geen drumstick/biefstuk).
- **Navigatie:** zwevende zwarte pil (Recepten · In huis · Ontdek · Meer) met een aparte ronde +-knop.
- **Bibliotheek:**
  - grote kop met de zoekbalk erin en een filterknop
  - categorie-tegels
  - tabs **Recepten · Favorieten · Collecties**
  - een uitgelicht recept bovenaan, daaronder een masonry-raster met foto's
- **Receptkaart:** foto, categorie-sticker, hartje (favoriet = lavendel, niet rood, want rood is vlees) en een tijd-label. Recepten van 30 minuten of korter krijgen een geel "Snel"-label.
- **Filterpaneel (bottom sheet):** tijd, bron en sorteren, met een live teller ("Toon X recepten"). Actieve filters staan als chips onder de tabs.
- **Detail:**
  - grote foto met een witte kaart eroverheen
  - drie statistieken (tijd, porties, ingrediënten)
  - een porties-stepper in de receptkleur
  - ingrediënten om af te vinken en genummerde stappen
  - een eigen notitie
  - delen als bericht, `.eazy`-bestand of (later) deellink
- **Kookmodus:** het scherm vult zich met de receptkleur, met een groot stapnummer. Noemt een stap een tijd ("10 minuten"), dan komt er een timerknop bij. Het scherm blijft aan.
- **In huis:** ingrediënten aantikken. Je ziet dan de recepten die het best passen, met een score ("4/6") en wat je nog mist.

## Online back-up

Remote `origin` = https://github.com/inzengo/EazyRecipes (privé). `main` volgt `origin/main`; push na elke afgeronde stap.

## Fase 3 — wat er gedaan is

- **3a** (`881b71a`): bibliotheek volgens de mockup (zoeken, categorie-tegels, tabs, filter-sheet, uitgelicht recept, navigatiebalk). Ontdek en Meer zijn tussenpagina's.
- **3b** (`9e192e8`): collecties (tab, openen = gefilterde bibliotheek, hernoemen en verwijderen, mapje op het detailscherm).
- **3c** "In huis" (`ui/pantry/`):
  - `PantryCatalog`: ~65 veelvoorkomende ingrediënten met varianten (kip = kip, kipfilet, kippendijen, …) en uitzonderingen (lente-ui ≠ ui, zoete aardappel ≠ aardappel, zure room ≠ room).
  - `PantryMatcher`: matcht op hele woorden zonder hoofdletters/accenten ("kippenbouillon" = bouillon, niet kip). Zout, peper, olie, water, "naar smaak", optioneel en kopjes tellen niet mee. Eigen woorden matchen op het begin van een woord; typ je een woord dat in de lijst staat, dan wordt die chip gekozen. Score = aandeel regels dat je hebt; ≥ 1 treffer, hoogste eerst, max. 12. "Nog nodig" toont de naam zonder hoeveelheid.
  - `PantryViewModel`: keuze en eigen woorden in SharedPreferences (`pantry`). Analyse van de regels alleen opnieuw als de bibliotheek verandert.
  - `PantryScreen`: lavendel kop, chips (meest gebruikt eerst, na 18 "+ N meer"), veld "iets anders", "Wissen", lijst met foto, streepjes, "Nog nodig" en score-rondje in de receptkleur. Tik opent het recept.
  - Data: `RecipeDao.observeIngredients()` + `RecipeRepository.observeRecipesWithIngredients()`.
  - Screenshots: `08-pantry.png`, `08b-pantry-empty.png`.
- Eerder: recepten bewerken en een robuuster klembord (`ebf161b`). Nog niet op de telefoon getest (afgesproken).

## Fase 4 — voortgang

Plan met deelstappen 4a–4e staat in [PLAN.md](PLAN.md#fase-4--koken-ordenen--delen-a). Nieuwe wens (2026-11-22): eigen categorieën en labels met eigen kleuren → 4d, vóór delen (4e) zodat het `.eazy`-formaat ze meteen bevat.

- **4a** zoeken & filters:
  - Zoekveld leest de tekst uit Compose-state in de ViewModel (`LibraryViewModel.query`), niet uit de afgeleide state → geen verdwenen letters of springende cursor meer. Zoeken zelf met 150 ms debounce.
  - Toetsenbord sluit bij de zoektoets, bij scrollen, bij het openen van een recept en bij de filterknop.
  - "Wis alles" in de sheet wist ook categorie en zoekwoord. Een tijdfilter meldt hoeveel recepten zonder tijd wegvallen. De gekozen categorie staat als chip onder de tabs.
  - Test: `LibraryViewModelTest`.
- **4b** detailscherm:
  - `recipe/Amounts.kt`: hoeveelheid + eenheid vooraan een regel herkennen ("200 gr", "1½ el", "2-3", "2 à 3", "1⁄2", "1.000 g") en omrekenen met nette keukengetallen (grammen op 5 vanaf 100, breuken onder de 10). Bij 1× wordt niets afgerond, alleen netjes geschreven.
  - Porties-stepper in de receptkleur; hoeveelheden rekenen mee. Ingrediënten afvinken (per sessie, gereset als de ingrediënten veranderen).
  - "Mijn notitie": opslaan na 600 ms stilte, en meteen bij terug/bewerken/afsluiten (via `AppContainer.appScope`, zodat het ook lukt als het scherm net verdwijnt).
  - Omslagfoto: cameraknop op de foto → kies uit galerij (Photo Picker, geen toestemming nodig; verkleind tot 1600 px JPEG, EXIF-gedraaid) of foto van de website terugzetten. Oude foto wordt opgeruimd.

- **4c** kookmodus (`cook/`):
  - `CookScreen`: één stap per pagina (swipen of knoppen), groot stapnummer, receptkleur, scherm blijft aan. Gebruikt de ViewModel van het detailscherm, dus dezelfde porties. Ingrediënten die in de stap genoemd worden staan eronder (`StepIngredients.kt`, via de "In huis"-lijst: "kip" ↔ "kipfilet"), met "Alles tonen".
  - `StepTimers`: tijden in de stap ("10 minuten", "8-10 min" → ondergrens, "anderhalf uur", "1/2 uur", "een kwartier", "tien minuten", "1 uur en 15 minuten"). Knop "Start timer"; lopende timer stoppen = lang indrukken.
  - `KitchenTimers` (in `AppContainer`): lopende timers, bewaard in SharedPreferences; > 30 min geleden afgelopen = weg bij opnieuw openen.
  - `TimerService`: voorgrond-service (type specialUse) met aftellende melding, wake-lock tot de timer afloopt, en een eigen alarmgeluid in een lus (ook als meldingen uit staan) tot je "Stoppen" tikt. Meldingstoestemming wordt één keer gevraagd bij de eerste timer.
  - **Op de telefoon testen:** timer laten aflopen met het scherm uit, met de app gesloten, en met meldingen uit.

- **4d-1** eigen categorieën:
  - `CategoryEntity` (naam, kleur, icoon, volgorde, soft-delete) vervangt de vaste `MainIngredient`-soorten; recepten hebben `categoryId`. De vijf standaardsoorten hebben vaste ids (`vlees`, `vis`, `vega`, `vegan`, `zoet`), zodat de gok bij import er een categorie van maakt. Een verwijderde standaardsoort met dezelfde naam opnieuw maken zet hem terug.
  - Palet: 14 kleuren in de stijl van de app (fel vlak, zwarte tekst ≥ 6:1, plus donkere variant), waarvan de eerste zes de originele contextkleuren. 30 Phosphor-iconen (fill). Zie `ui/theme/CategoryStyle.kt`.
  - Beheer: Meer → Categorieën (hernoemen, kleur, icoon, volgorde, toevoegen, verwijderen; recepten blijven bewaard zonder soort). Kies-soort in de editor toont je eigen categorieën.
  - "Vega toont ook vegan" is vervallen: een tegel toont nu precies zijn eigen soort.
  - Alle schermen halen kleur/icoon via `LocalCategories` (gevuld in `MainActivity`).

- **4d-2** labels (de bestaande `tag`-tabel, schema v4: `color`, `hidden`, `mergedInto`):
  - Receptscherm: labels als gekleurde pillen + "Labels"-knop → venster om labels aan/uit te zetten of een nieuw label te maken.
  - Bibliotheek: filter-sheet heeft een groep "Labels" (álle gekozen labels moeten erop staan); gekozen labels staan als chip onder de tabs.
  - Meer → Labels: zoeken, kleur (zelfde palet of grijs), verbergen (niet op recepten/in filter, wel vindbaar met zoeken), hernoemen, samenvoegen (ook door te hernoemen naar een bestaande naam), verwijderen.
  - Samengevoegd label onthoudt waarin het opging (`mergedInto`): een nieuwe import met de oude naam ("veggie") komt bij het nieuwe ("vegetarisch"). Een gewoon verwijderd label komt terug als een import het meebrengt.
  - Zoekindex en recept-`updatedAt` worden set-gebaseerd bijgewerkt (één SQL-statement, ook bij honderden recepten).

- **Meerdere categorieën per recept** (wens 2026-11-22, schema v4: tabel `recipe_category`): paella = vis én vlees, staat onder beide tegels. `recipe.categoryId` blijft de hoofdsoort (kleur); op de kaart een tweede, kleinere sticker. In "Soort recept" vink je er meer aan en kies je met "Kleur" de hoofdsoort. De import-gok geeft vis + vlees als beide voorkomen (o.a. gamba's, chorizo). Bestaande recepten houden hun ene soort; een al geïmporteerde paella moet je zelf even "Vlees" erbij geven.

- **4e** delen & back-up (`share/`):
  - `.eazy` = ZIP met `eazy.json` (versie 1: recepten, categorieën, labels, collecties) + `images/`. Lezen met grenzen (grootte per onderdeel en totaal, JSON-diepte, tekstlengtes, aantallen), ids alleen `[A-Za-z0-9-]`, links alleen http(s). Zie `EazyFile.kt`.
  - Receptscherm → deelknop: als tekst (met gekozen porties) of als `.eazy`. Discord verstuurt berichten boven 2000 tekens stilletjes niet; Discord krijgt daarom via `EXTRA_REPLACEMENT_EXTRAS` een ingekorte tekst (`RecipeText.fit`, op een hele regel, met bronlink). Andere apps krijgen het hele recept (v0.6.1). Collectie-menu → "Delen als bestand". Meer → "Back-up maken" (jij kiest waar) en "Bestand openen".
  - Openen (ook via "Openen met"/"Delen" vanuit een andere app, alleen `content://`): voorbeeld → importeren. Dubbele recepten (zelfde id of bronlink) worden overgeslagen; categorieën op id/naam gekoppeld of aangemaakt; labels op naam; collecties op naam samengevoegd. Import en back-up lopen in `appScope` door als je het scherm verlaat; mislukt een import, dan blijven er geen losse foto's achter.

- **Fase 5 (deel)** overige import:
  - **Zelf invoeren:** in "Recept toevoegen" → "Geen link? Voer een recept zelf in" (lege editor), en bij "Geen recept gevonden" de knop "Zelf invoeren" (link wordt bron).
  - **Meerdere links:** plak een lijst in "Recept toevoegen" (Enter = nieuwe regel, "Plakken" neemt alle links mee) → wachtrij (`importer/BatchImport.kt`): één voor één ophalen en meteen opslaan, voortgangsscherm met openen per recept en "mislukte opnieuw". Bestaande recepten worden overgeslagen. De wachtrij leeft in het geheugen (weg als Android de app afsluit).
  - **Bladwijzers:** Meer → "Bladwijzers importeren" → HTML-export van Chrome/Firefox/Safari (`importer/Bookmarks.kt`) → kiezen per map (met zoeken; wat je al hebt staat uit) → wachtrij.
  - **Importopties onder de +-knop** (`8f9b7f7`): link plakken, zelf invoeren, bladwijzers en `.eazy`-bestand op één plek.
  - **Foto/OCR** (`c8a4341`, v0.6.0): +-knop → camera of tot 5 afbeeldingen uit de galerij (kookboekpagina's, screenshots). ML Kit tekstherkenning (gebundeld, offline) leest de tekst buiten de hoofdthread, verkleind tot 2500 px. `importer/PhotoRecipeParser.kt` deelt in: titel, porties, tijd, ingrediënten, stappen → bestaande editor om na te kijken. Er gaat geen OCR-regel verloren. APK alleen voor `arm64-v8a` om het ML Kit-model klein te houden.

## Volgende stap

1. **Alles op de telefoon testen** (fase 3–5, APK v0.6.1) — getest op 2026-11-24: alles werkt, behalve tekst delen naar Discord (opgelost in 0.6.1, nog even nakijken). Aandachtspunten: zoeken (typen), porties omrekenen, foto kiezen, kookmodus + timers (scherm uit, app dicht, meldingen uit), categorieën/labels, meerdere soorten per recept, delen als tekst en als bestand (naar jezelf sturen en openen), back-up maken en terugzetten, meerdere links, bladwijzers, recept van een foto/screenshot (kookboek en schermafbeelding).
2. Bevindingen van de test oplossen, daarna de volgende fase kiezen uit [PLAN.md](PLAN.md#later).

## Open punten

- Gradle-geheugen is verhoogd naar `-Xmx2g` / 1 GB metaspace. Dat is getest met een `clean` + build + tests.
- `build`-mappen zijn nu junctions naar E: (nodig voor KSP en screenshot-tests). `clean` wist via het junction-pad. Zie [docs/SETUP-WINDOWS.md](docs/SETUP-WINDOWS.md#5-gradle-build-output-en-temp-naar-e).
- De hoofdingrediënt-gok (`MainIngredientGuesser`) is een heuristiek. In het voorbeeldscherm moet de gebruiker hem kunnen aanpassen.
- Welke sites komen er naast leukerecepten.nl en eefkooktzo.nl bij Ontdekken (latere fase)?
- Foto's in de mockup komen van Unsplash. In de app gebruiken we de foto van de bron, die lokaal wordt opgeslagen.

## Zo pak je het weer op

```sh
cd C:\Users\User\Documents\EazyRecipes
git log --oneline          # wat er gebeurd is
./gradlew assembleDebug    # controleer dat de build nog werkt
```

Lees daarna deze STATUS.md en PLAN.md. Zeg tegen Claude: "we gaan verder", Claude leest dan STATUS.md.
