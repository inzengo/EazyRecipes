# EazyRecipes — Plan

Persoonlijke Android-app om recepten van verschillende sites op één plek te verzamelen,
mooi te tonen en makkelijk terug te vinden. Doel: een zelf te installeren APK, niet publiceren.

## Besluiten

| Onderwerp | Keuze | Waarom |
|---|---|---|
| Taal/UI | Kotlin + Jetpack Compose (Material 3) | Native standaard; delen-menu, WebView en database zonder bruggen. Prestaties zitten in netwerk/afbeeldingen, niet in CPU. |
| Opslag | Alleen lokaal: Room (SQLite) + FTS4 voor zoeken | Geen server nodig; alleen telefoon. Export/import naar JSON-bestand als back-up. |
| Import | schema.org `Recipe` JSON-LD uitlezen | Vrijwel elke receptsite levert dit voor Google. |
| Distributie | Debug-/zelfgesigneerde APK | Sideloaden op eigen telefoon. |
| Min. Android | API 26 (Android 8) | Dekt praktisch alle toestellen. |

Rust is bewust niet gekozen; de importlogica blijft een losse module (`import/`) zodat die later
eventueel via UniFFI in Rust herschreven kan worden.

## Onderzoeksbevindingen (2026-11-17)

- **leukerecepten.nl**: receptpagina's bevatten een volledig `Recipe`-JSON-LD blok
  (`recipeIngredient`, `recipeInstructions` als `HowToSection`/`HowToStep`, voeding, afbeelding).
  Robots.txt verbiedt `/search/` en `?s=` → hun zoekpagina niet scrapen.
- **eefkooktzo.nl**: achter Cloudflare; een kale HTTP-request krijgt `403`. Heeft wel een
  sitemap (`/sitemap_index.xml`) en `Crawl-delay: 20`.
- Gevolg: import heeft twee paden nodig — (1) snelle HTTP-fetch, (2) fallback via een
  onzichtbare WebView die de pagina als echte browser laadt en het JSON-LD eruit haalt.

## Architectuur

```
app/
 ├─ ui/          Compose-schermen + ViewModels (bibliotheek, detail, import, ontdekken)
 ├─ data/        Room-entities, DAO's, repository, JSON-export
 ├─ import/      URL → RawPage (HTTP of WebView) → JSON-LD parser → Recipe
 └─ discover/    (fase 3) site-adapters + lokale index uit sitemaps
```

Eén module om te beginnen; opsplitsen pas als het nodig is.

### Datamodel (eerste versie)

- `Recipe`: id, title, description, sourceUrl, sourceSite, imageUrl/lokaal pad, servings,
  prepMinutes, cookMinutes, totalMinutes, category, cuisine, notes, rating, favorite,
  createdAt, updatedAt
- `Ingredient`: recipeId, position, raw tekst (+ later geparsed: hoeveelheid, eenheid, naam)
- `Step`: recipeId, position, sectionName, text
- `Tag` + `RecipeTag` (n-op-m)
- FTS-tabel over titel, ingrediënten en tags

Afbeeldingen worden lokaal opgeslagen, zodat recepten offline werken en blijven bestaan als de site verdwijnt.

## Fases

### Fase 0 — Fundament (nu)
- [x] Plan, repo, Gradle/Compose-skelet
- [x] Toolchain installeren: JDK 17 + Android SDK via CLI op E: (zie `docs/SETUP-WINDOWS.md`)
- [x] Eerste APK bouwen (`assembleDebug`, 9 MB)
- [ ] APK op telefoon zetten en delen-menu testen

### Fase 1 — Importeren
- Delen-menu ("Delen → EazyRecipes") + link plakken
- HTTP-fetch → JSON-LD parser (incl. `@graph`, arrays van types, `HowToSection`, ISO 8601-duur zoals `PT45M`)
- WebView-fallback bij 403/geen JSON-LD
- Voorbeeldscherm vóór opslaan: titel/ingrediënten/stappen bewerken
- Handmatig recept toevoegen
- Unit tests met opgeslagen HTML van echte receptpagina's

### Fase 2 — Bibliotheek & design
- Overzicht als kaartjesraster met grote foto's; snel zoeken bovenaan
- Filters: tags, categorie (ontbijt/lunch/diner/bakken…), totale tijd, bron-site, favorieten,
  "bevat ingrediënt" / "zonder ingrediënt"
- Sorteren: recent toegevoegd, naam, tijd, waardering
- Receptdetail: porties omrekenen, kookmodus (scherm blijft aan, grote tekst, stap voor stap),
  ingrediënten afvinken, eigen notities
- Export/import JSON als back-up
- Design-ideatie volgt in een aparte sessie (mockups vóór bouwen)

### Fase 3 — Ontdekken (extern zoeken)
- Per site een adapter; voorkeur voor **sitemaps** → lokale index (titel + URL), periodiek ververst
  met respect voor `Crawl-delay`
- Zoekresultaten tonen → één tik importeren
- Eerst proefje per site om stabiliteit te checken

### Later / misschien
- Boodschappenlijst uit geselecteerde recepten, weekmenu
- Ingrediënten slim parsen (hoeveelheid/eenheid) voor nauwkeuriger omrekenen
- AI-extractie voor sites zonder JSON-LD
- Sync naar laptop

## Open vragen
- Design-stijl en kleuren (volgende sessie)
- Welke sites naast leukerecepten.nl en eefkooktzo.nl staan op de lijst voor fase 3?
- Bestaande recepten op laptop/telefoon: welke vorm (links, bookmarks, documenten)? → bepaalt een bulk-import.
