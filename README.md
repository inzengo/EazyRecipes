# EazyRecipes

Persoonlijke Android-app (Kotlin + Jetpack Compose) om recepten van websites te verzamelen in één eigen bibliotheek.
Zie [PLAN.md](PLAN.md) voor besluiten, onderzoek en fases.

## Bouwen

Vereist: JDK 17 en Android SDK. Zie [docs/SETUP-WINDOWS.md](docs/SETUP-WINDOWS.md) voor de command-line installatie op deze machine (alles op E:).

```sh
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

Op deze machine komt de APK in `E:\DevTools\builds\EazyRecipes\app\outputs\apk\debug\app-debug.apk`. Zonder het init-script is dat `app/build/outputs/apk/debug/`.
