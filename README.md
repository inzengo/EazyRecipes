# EazyRecipes

Persoonlijke Android-app (Kotlin + Jetpack Compose) om recepten van websites te verzamelen in één eigen bibliotheek.
Zie [PLAN.md](PLAN.md) voor besluiten, onderzoek en fases.

## Bouwen

Vereist: JDK 17 en Android SDK (makkelijkst: Android Studio installeren en deze map openen).

```sh
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

De APK staat daarna in `app/build/outputs/apk/debug/app-debug.apk`. Zet hem op je telefoon (USB of bestand delen) en sta "installeren uit onbekende bronnen" toe.
