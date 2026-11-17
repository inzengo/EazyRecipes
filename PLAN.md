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
- **Ontdekken**: zoeken op externe sites via sitemaps → lokale index, één tik importeren
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
- Navigatie: Recepten · In huis · Ontdek · Meer, plus een zwevende +-knop.
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

### Fase 2 — Data + import (volgende)
- Datamodel + Room, JSON-LD parser met tests op opgeslagen echte pagina's, WebView-fallback
- Delen-menu, link plakken, voorbeeldscherm
- Compose-thema volgens design v0.2 (fonts, kleuren, Phosphor-iconen)
- **Eerste test op de telefoon**

### Fase 3 — Bibliotheek & vinden
- Bibliotheek (tabs Recepten/Favorieten/Collecties), zoeken, filter-sheet, categorie-tegels
- Collecties maken en beheren, "In huis"

### Fase 4 — Koken & delen A
- Detail, porties, notities, kookmodus met timers; delen als tekst/`.eazy`-bestand; back-up

### Fase 5 — Overige import
- Bladwijzers-bestand, OCR, handmatig invoeren, dubbele recepten

Daarna: boodschappenlijst, Ontdekken, delen B → C.

## Open vragen
- Welke sites naast leukerecepten.nl en eefkooktzo.nl voor Ontdekken?
