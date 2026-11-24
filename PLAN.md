# EazyRecipes — Plan

Persoonlijke Android-app om recepten van verschillende sites en bronnen op één plek te verzamelen,
mooi te tonen en makkelijk terug te vinden. Doel: een zelf te installeren APK, niet publiceren.

**Kernprobleem:** recepten staan verspreid over websites, laptop, computer en telefoon.
De app moet (1) die bestaande chaos makkelijk binnenhalen, (2) nieuwe recepten met één tik opslaan,
(3) alles snel terugvindbaar maken, en (4) later delen met anderen mogelijk maken.

## Besluiten

| Onderwerp | Keuze | Waarom |
|---|---|---|
| Taal/UI | Kotlin + Jetpack Compose (Material 3) | Native standaard; delen-menu, WebView en database zonder bruggen. |
| Opslag | Lokaal: Room (SQLite) + FTS4 | Geen server nodig voor de basis. |
| Sync-klaar datamodel | UUID's, `updatedAt` per record, soft-delete (`deletedAt`) | Delen/samenwerken (B/C) later toevoegen zonder datamigratie. |
| Import | schema.org `Recipe` JSON-LD, met een WebView als fallback | Vrijwel elke receptsite levert JSON-LD; WebView omzeilt bot-blokkades (Cloudflare). |
| Deelformaat | Eén JSON-schema voor export, back-up en delen | Eén formaat om te onderhouden. |
| Distributie | Debug-/zelfgesigneerde APK | Sideloaden op eigen telefoon. |
| Min. Android | API 26 (Android 8) | Dekt praktisch alle toestellen. |

Rust is bewust niet gekozen; de importlogica blijft een losse module (`import/`).

## Onderzoeksbevindingen (2026-11-17)

- **leukerecepten.nl**: volledig `Recipe`-JSON-LD (`recipeIngredient`, `recipeInstructions` als
  `HowToSection`/`HowToStep`, voeding, afbeelding). Robots.txt verbiedt `/search/` en `?s=`.
- **eefkooktzo.nl**: achter Cloudflare; een kale HTTP-request krijgt `403`. Heeft een sitemap
  (`/sitemap_index.xml`) en `Crawl-delay: 20`.
- Gevolg: import heeft twee paden nodig: (1) een HTTP-fetch, (2) als dat faalt een onzichtbare WebView.

## Functies

### Versie 1 (basis)

**Binnenhalen**
- Link plakken + delen-menu ("Delen → EazyRecipes")
- Meerdere links tegelijk (één per regel, wachtrij met voortgang)
- Bladwijzers-bestand (HTML-export van Chrome/Firefox) → kies welke links importeren
- Foto/screenshot → on-device OCR (ML Kit) → tekst indelen in ingrediënten/stappen
- Handmatig invoeren
- Voorbeeldscherm vóór opslaan (alles bewerkbaar)
- Dubbele recepten herkennen (zelfde URL / vergelijkbare titel)
- Lokale kopie van tekst + foto's; bronlink blijft bewaard

**Vinden**
- Bibliotheek met grote foto-kaarten, zoekbalk bovenaan (FTS: titel, ingrediënten, tags)
- Automatische tags uit JSON-LD (`recipeCategory`, `recipeCuisine`, `keywords`, tijden)
- Filters: categorie, hoofdingrediënt (vlees/vis/vega…), totale tijd, bron, favoriet,
  ingrediënt wel/niet
- Collecties (handmatige groepen, recept kan in meerdere)
- **Wat heb ik in huis?** → 2–3 ingrediënten invoeren, recepten gerangschikt op overlap

**Koken**
- Receptdetail met eigen notities
- Porties omrekenen
- Kookmodus: scherm aan, grote tekst, stap voor stap, ingrediënten afvinken

**Delen (niveau A)**
- Recept delen als nette tekst + bronlink (WhatsApp e.d.)
- Recept/collectie delen als `.eazy`-bestand (JSON) dat de app kan importeren
- Volledige back-up export/import

### Later
- **Boodschappenlijst** uit gekozen recepten (vereist het parsen van ingrediënten: hoeveelheid/eenheid/naam)
- **Delen niveau B**: deellink voor recept/collectie/bibliotheek → webpagina, "Opslaan in EazyRecipes"
  (gratis tier Supabase of Cloudflare). Hiermee kun je je bibliotheek ook op de laptop bekijken.
- **Delen niveau C**: gedeelde collecties met accounts + sync
- Misschien: gekookt-log + rating, weekmenu, timers uit stappen, AI-extractie voor sites zonder JSON-LD

## Design (v0.2, vastgesteld)

Volledige uitwerking: [STATUS.md](STATUS.md#design-besluiten-v02) en de mockups in `docs/design/mockups/`
(klikbaar canvas: https://claude.ai/artifact/6UDLioCa2cawBMaDYd1Wjm).

- Basis: wit/grijs + inkt-zwart. Foto's zijn het belangrijkste beeld.
- Contextkleur: felle vlakken per categorie met zwarte tekst; de kop, de navigatie, het recept en de kookmodus kleuren mee.
- Bricolage Grotesque + Figtree; Phosphor-iconen (fill).
- Kleur nooit als enige signaal: altijd ook een icoon of label. Foutmeldingen krijgen een eigen vorm.
- Navigatie: Recepten · In huis · Lijst (boodschappen) · Meer, plus een zwevende +-knop. Ontdek is geschrapt (2026-11-24).
- Bibliotheek-tabs: Recepten · Favorieten · Collecties. Filters in een bottom sheet.

## Architectuur

```
app/
 ├─ ui/          Compose-schermen + ViewModels, theme (incl. contextuele accenten)
 ├─ data/        Room-entities, DAO's, repository, JSON-export/deelformaat
 ├─ import/      URL → RawPage (HTTP of WebView) → JSON-LD parser → Recipe
 │               + bladwijzers-parser, OCR-pipeline
 └─ discover/    (later) site-adapters + lokale index uit sitemaps
```

### Datamodel (eerste versie)

Alle tabellen: `id` (UUID), `createdAt`, `updatedAt`, `deletedAt?`.

- `Recipe`: title, description, sourceUrl, sourceSite, imagePath, servings, prepMinutes,
  cookMinutes, totalMinutes, category, cuisine, mainIngredient (vlees/vis/vega/vegan/…),
  notes, favorite
- `Ingredient`: recipeId, position, raw (later: amount, unit, name)
- `Step`: recipeId, position, sectionName, text
- `Tag`, `RecipeTag`; `Collection`, `CollectionRecipe`
- FTS-tabel over titel, ingrediënten en tags

## Fases

### Fase 0 — Fundament ✅
- [x] Plan, repo, Gradle/Compose-skelet met delen-menu
- [x] Toolchain via CLI op E: (zie `docs/SETUP-WINDOWS.md`)
- [x] Eerste APK bouwen

### Fase 1 — Design ✅
- [x] Mockups v0.1 (te braaf) → v0.2 (huidige richting), klikbaar prototype
- [x] Kleursysteem, typografie, iconen, navigatie
- [x] Favorieten als tab, collecties in de bibliotheek, filter-sheet, "snel"-label op kaarten

### Fase 2 — Data + import ✅
- Datamodel + Room, JSON-LD parser met tests op opgeslagen echte pagina's, WebView-fallback
- Delen-menu, link plakken, voorbeeldscherm
- Compose-thema volgens design v0.2 (fonts, kleuren, Phosphor-iconen)
- **Eerste test op de telefoon**

### Fase 3 — Bibliotheek & vinden ✅
- Bibliotheek (tabs Recepten/Favorieten/Collecties), zoeken, filter-sheet, categorie-tegels
- Collecties maken en beheren, "In huis"

### Fase 4 — Koken, ordenen & delen A ✅
Volgorde bewust: eerst de bugs, dan koken, dan het datamodel uitbreiden (categorieën/labels), en pas daarna delen,
zodat het `.eazy`-formaat en de back-up meteen de nieuwe velden bevatten en we het formaat niet hoeven te veranderen.

**4a — Zoeken & filters repareren** (gemeld bij testen, 2026-11-22)
- *Letters verdwijnen / cursor springt:* het zoekveld toont `filter.query` uit de ViewModel-state. Die komt pas terug
  nadat ook de FTS-zoekopdracht klaar is (`combine` + `mapLatest` + `stateIn`), dus een volgende toetsaanslag bouwt op
  een verouderde waarde. Oplossing: het veld houdt zijn eigen tekst (lokale state), de ViewModel krijgt de query
  apart en zoekt met een korte debounce.
- *Toetsenbord:* de zoektoets doet niets, het toetsenbord gaat niet weg bij scrollen of bij het openen van een recept.
  Het zoekveld zit in het scrollende raster; scrol je het weg, dan verliest het de focus.
- *Filter-sheet:* "Wis alles" wist categorie en zoekwoord niet. Een tijdfilter verbergt recepten zonder tijd zonder dat
  je dat ziet. Categorie en zoekwoord ontbreken als chips onder de tabs.
- Tests: ViewModel-test voor snel typen, screenshot van de sheet.

**4b — Detail: porties, notities & omslagfoto**
- Hoeveelheden herkennen vooraan de ingrediëntregel (`200 gr`, `½`, `1-2`, `1,5`) en omrekenen met de porties-stepper
  ("2 ½ el" netjes afronden). Regels zonder hoeveelheid blijven zoals ze zijn.
- Eigen notitie per recept (veld bestaat al: `RecipeEntity.notes`).
- Ingrediënten afvinken (alleen voor deze kooksessie).
- Omslagfoto wijzigen (wens 2026-11-22): kiezen uit de galerij (verkleind tot 1600 px), of de foto van de website terugzetten.

**4c — Kookmodus**
- Volledig scherm in de receptkleur, één stap tegelijk, groot stapnummer, swipen. Het scherm blijft aan.
- Tijd in een stap ("10 minuten", "1 uur", "5-7 min") → timerknop; timers lopen door met een melding (foreground notification).
- Ingrediënten van de stap onderin, uitklapbaar.

**4d — Eigen categorieën & labels** (wens 2026-11-22) ✅
- **Categorieën bewerkbaar:** de vaste `MainIngredient`-enum wordt een tabel `category` (naam, kleur, icoon, volgorde).
  De vijf huidige worden de standaard en blijven werken (migratie Room v1 → v2). Je kunt ze hernoemen, verwijderen,
  van kleur/icoon wisselen en nieuwe toevoegen (bv. "Soep", "Ontbijt").
  "Snel" blijft automatisch (≤ 30 min) en is geen categorie.
- **Kleuren uit een vaste palet** (± 12 felle kleuren die allemaal ≥ 6:1 contrast hebben met zwarte tekst), geen vrije
  kleurkiezer. Zo blijft het design-systeem kloppen (zwarte tekst op fel vlak). De donkere variant (`deep`) wordt per
  paletkleur meegeleverd.
- **Iconen uit een vaste set** Phosphor-iconen (± 24, eten-gerelateerd).
- **Labels:** de geïmporteerde tags van sites ("Hoofdgerecht", "Makkelijk") worden eigen labels: hernoemen,
  samenvoegen, verbergen, kleur geven. Filteren op label in de bibliotheek; labels zichtbaar op het detailscherm.
- De gok bij import (`MainIngredientGuesser`) kiest uit de standaardcategorieën; bestaat die niet meer, dan geen categorie.
- Beheerscherm onder "Meer".

**4e — Delen A & back-up**
- Recept delen als nette tekst + bronlink.
- Recept of collectie als `.eazy`-bestand (JSON met versienummer, foto's erin) en dat bestand weer openen in de app.
- Volledige back-up exporteren/terugzetten (zelfde formaat, alle recepten + categorieën + labels + collecties).

### Fase 5 — Overige import
- Bladwijzers-bestand, OCR, handmatig invoeren, dubbele recepten
- Alle manieren van toevoegen zitten onder de +-knop (wens 2026-11-22): link (of lijst links), foto of screenshot,
  zelf invoeren, bladwijzers, .eazy-bestand. Onder "Meer" blijft alleen back-up maken/terugzetten.
- Foto/screenshot: camera of tot 5 beelden uit de galerij → ML Kit-tekstherkenning in de app (offline) →
  `PhotoRecipeParser` deelt in op kopjes (Ingrediënten/Bereiding, ook Engels) of gokt zonder kopjes →
  bewerkscherm om na te kijken. Geen omslagfoto uit de foto (meestal een bladzijde tekst); die kies je daarna zelf.

Daarna: boodschappenlijst, delen B → C. (Ontdekken is geschrapt op 2026-11-24.)

### Fase 6 — Boodschappenlijst
Besluit 2026-11-24: Ontdek (zoeken naar nieuwe recepten) is geschrapt. In huis en Boodschappen hebben elk een eigen tabblad: **Recepten · In huis · Lijst · Meer** ("Boodschappen" past niet in de balk als de knop actief is).
- **6a** logica (`shopping/`): recepten + porties → één lijst. Gelijke ingrediënten samen (enkel-/meervoud: ui/uien,
  tomaat/tomaten; "koude boter", "boter of margarine" = boter), opgeteld per soort eenheid (g/kg, ml/dl/l, el, tl,
  stuks naar boven afgerond, overige eenheden apart: "600 g + 2 blikjes"). Per afdeling (groente & fruit, vlees & vis,
  zuivel, brood, voorraadkast, kruiden & sauzen, overig). Basis (zout, peper, olie, water) en wat bij In huis
  aangetikt staat → "Heb je al". "Naar smaak" en "optioneel" komen wél op de lijst. Delen als tekst.
- **6b** opslag en schermen: Room v5 (recepten op de lijst met porties, eigen items, afgevinkt), tabblad Lijst (scherm Boodschappen),
  "Op de lijst" op het receptscherm (met de gekozen porties), en vanaf In huis een recept met één tik op de lijst.
  Niet in de `.eazy`-back-up (de lijst is tijdelijk).

## Open vragen
