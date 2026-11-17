# Status — EazyRecipes

_Laatst bijgewerkt: 2026-11-17_

## Waar staan we

| Fase | Status |
|---|---|
| 0 — Fundament (repo, skelet, toolchain, eerste APK) | ✅ klaar |
| 1 — Design (mockups, kleursysteem, navigatie) | ✅ klaar (v0.2), goedgekeurd op hoofdlijnen |
| 2 — Data + import | ⏭️ **volgende** |
| 3–5 en later | zie [PLAN.md](PLAN.md) |

## Wat er nu is

- **Repo:** Kotlin + Jetpack Compose-skelet (`app/`). Het delen-menu ("Delen → EazyRecipes") vangt al een link op en toont die.
- **APK:** `assembleDebug` werkt. Output: `E:\DevTools\builds\EazyRecipes\app\outputs\apk\debug\app-debug.apk`.
  Nog **niet** op een telefoon getest.
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

## Volgende stap: fase 2 (data + import)

1. Datamodel in Room: UUID's, `updatedAt`, `deletedAt`, en FTS voor zoeken (zie PLAN.md).
2. JSON-LD-parser met unit tests op opgeslagen echte pagina's (leukerecepten.nl werkt; eefkooktzo.nl geeft 403 → WebView-fallback).
3. Delen-menu en link plakken → voorbeeldscherm → opslaan.
4. Het design-systeem in Compose zetten: thema, fonts, kleuren, Phosphor-iconen.
5. **Eerste test op de telefoon.**

## Open punten

- Na de vrijgemaakte ruimte op C: kunnen de zuinige Gradle-instellingen (`-Xmx1g`, 2 workers) misschien weer ruimer. Eerst testen.
- Welke sites komen er naast leukerecepten.nl en eefkooktzo.nl bij Ontdekken (latere fase)?
- Foto's in de mockup komen van Unsplash. In de app gebruiken we de foto van de bron, die lokaal wordt opgeslagen.

## Zo pak je het weer op

```sh
cd C:\Users\User\Documents\EazyRecipes
git log --oneline          # wat er gebeurd is
./gradlew assembleDebug    # controleer dat de build nog werkt
```

Lees daarna deze STATUS.md en PLAN.md. Zeg tegen Claude: "we gaan verder met fase 2".
