# Status — EazyRecipes

_Laatst bijgewerkt: 2026-11-19 (avond)_

## Waar staan we

| Fase | Status |
|---|---|
| 0 — Fundament (repo, skelet, toolchain, eerste APK) | ✅ klaar |
| 1 — Design (mockups, kleursysteem, navigatie) | ✅ klaar (v0.2), goedgekeurd op hoofdlijnen |
| 2 — Data + import | ✅ klaar |
| 3 — Bibliotheek & vinden | 🚧 3a + 3b klaar, **3c (In huis) volgende** |
| 3–5 en later | zie [PLAN.md](PLAN.md) |

## Wat er nu is

- **Repo:** Kotlin + Jetpack Compose-skelet (`app/`). Het delen-menu ("Delen → EazyRecipes") vangt al een link op en toont die.
- **APK:** `assembleDebug` werkt. Output: `E:\DevTools\builds\EazyRecipes\app\outputs\apk\debug\app-debug.apk`.
  Nog **niet** op een telefoon getest.
- **Datamodel (Room 2.7, KSP2):** `app/src/main/java/nl/eazyrecipes/data/`. Tabellen recipe, ingredient, step, tag, recipe_tag, collection, collection_recipe, plus de FTS4-index `recipe_fts`. Elke rij heeft `createdAt`/`updatedAt`/`deletedAt`; entiteiten hebben een UUID. `RecipeRepository` is de enige schrijfingang en houdt timestamps en zoekindex bij.
  Zoeken: elk woord als prefix ("kipf" vindt "kipfilet"), zonder accenten ("creme" vindt "crème"). Schema-export staat in `app/schemas/`.
- **Importer:** `app/src/main/java/nl/eazyrecipes/importer/`.
  - `JsonLdRecipeParser` (HTML → `ParsedRecipe`), ISO-duur → minuten, en `MainIngredientGuesser` (eerste gok voor vlees/vis/vega/vegan/zoet).
  - `RecipeImporter`: link → recept. Eerst HTTP (`HttpPageFetcher`, OkHttp). Bij 401/403/429/503, of als de HTML geen recept bevat, gaat hij via `WebViewPageFetcher` (onzichtbare WebView, voor Cloudflare-sites zoals eefkooktzo.nl). Haalt `utm_*`/`fbclid`/… uit de link.
  - `ImageStore`: slaat de receptfoto lokaal op (max. 15 MB, alleen `image/*`).
- **Tests:** `./gradlew testDebugUnitTest`, 61 tests, alle groen. Ze testen op twee echte leukerecepten.nl-pagina's (`app/src/test/resources/pages/`, ingekort tot de JSON-LD) en een reeks varianten van andere sites. De database wordt getest met Robolectric.
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

## Volgende stap: fase 3c — "In huis"

Gedaan in fase 3:
- **3a** (`881b71a`): bibliotheek volgens de mockup (zoeken, categorie-tegels, tabs, filter-sheet, uitgelicht recept, navigatiebalk). Ontdek en Meer zijn tussenpagina's.
- **3b** (`9e192e8`): collecties (tab, openen = gefilterde bibliotheek, hernoemen en verwijderen, mapje op het detailscherm).
- Eerder: recepten bewerken en een robuuster klembord (`ebf161b`). Nog niet op de telefoon getest (afgesproken).

**Plan voor 3c.** Het tabblad "In huis" toont nu `ComingSoon(...)`, via de `pantry`-parameter van `HomeScreen` in `MainActivity`.
1. `ui/pantry/PantryMatcher.kt` (pure Kotlin + unit tests):
   - **Lijst ingrediënten:** een vaste lijst van ~50 veelvoorkomende ingrediënten met varianten, bijvoorbeeld kip = kip, kipfilet, kippendij, …
   - **Matchen:** op hele woorden of woordgroepen, zonder hoofdletters en accenten. Zo telt "kippenbouillon" als bouillon, niet als kip.
   - **Wat niet meetelt:** zout, peper, olie, water, "optioneel:"- en "naar smaak"-regels tellen niet mee als "nog nodig".
   - **Eigen woorden:** matchen op het begin van een woord.
   - **Score:** het aandeel regels dat je hebt. Alleen recepten met ten minste één treffer, de hoogste score eerst, maximaal 12.
   - **"Nog nodig":** de naam zonder hoeveelheid ("200 gr kipfilet" → "kipfilet").
   - **Chips:** alleen lijst-items die in je eigen recepten voorkomen, de meest gebruikte eerst.
2. **Data:** een DAO-query met alle ingrediëntregels van niet-verwijderde recepten (Flow) en een repository-methode die per recept de regels geeft.
3. **`PantryViewModel`:** onthoudt je keuze en eigen woorden in SharedPreferences, zodat ze na het afsluiten blijven staan.
4. **`PantryScreen` volgens de mockup:**
   - lavendel kop "Wat heb je in huis?";
   - chips: zwart met ✓ = gekozen, wit met + = niet gekozen;
   - een veld "iets anders";
   - een lijst "Hiermee kun je aan de slag": foto, titel, streepjes per ingrediënt, "Nog nodig: …" en een score-rondje ("4/6") in de receptkleur. Tik opent het recept.
5. **Tests:** screenshot-test, STATUS bijwerken, commit.

Daarna: **fase 4** (kookmodus, porties omrekenen, notities, delen als tekst of `.eazy`, back-up). Zie PLAN.md.

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

Lees daarna deze STATUS.md en PLAN.md. Zeg tegen Claude: "we gaan verder met fase 2".
