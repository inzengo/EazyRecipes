# Toolchain op Windows (zonder Android Studio, alles op E:)

C: heeft weinig ruimte, dus alles wat groot wordt staat in `E:\DevTools`:

| Pad | Inhoud |
|---|---|
| `E:\DevTools\jdk-17` | Eclipse Temurin JDK 17 (zip, geen installer) |
| `E:\DevTools\android-sdk` | Android command-line tools + SDK |
| `E:\DevTools\gradle-home` | Gradle-distributie en dependency-cache (`GRADLE_USER_HOME`) |
| `E:\DevTools\android-user` | `.android`-map: debug-keystore, ADB-sleutels (`ANDROID_USER_HOME`) |
| `E:\DevTools\builds\EazyRecipes` | Build-output (via init-script, zie stap 5) |
| `E:\DevTools\tmp` | Tijdelijke bestanden van Gradle/Kotlin |

## 1. JDK 17

```sh
curl -L -o jdk17.zip "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
unzip jdk17.zip -d /e/DevTools/ && mv /e/DevTools/jdk-17* /e/DevTools/jdk-17
```

## 2. Android command-line tools

```sh
curl -L -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
mkdir -p /e/DevTools/android-sdk/cmdline-tools
unzip cmdtools.zip -d /e/DevTools/android-sdk/cmdline-tools
mv /e/DevTools/android-sdk/cmdline-tools/cmdline-tools /e/DevTools/android-sdk/cmdline-tools/latest
```

De map moet `cmdline-tools\latest` heten, anders weigert `sdkmanager`.

## 3. SDK-pakketten + licenties (Git Bash)

```sh
export JAVA_HOME='E:\DevTools\jdk-17'
yes | cmd //c "E:\\DevTools\\android-sdk\\cmdline-tools\\latest\\bin\\sdkmanager.bat --sdk_root=E:\\DevTools\\android-sdk --licenses"
cmd //c "E:\\DevTools\\android-sdk\\cmdline-tools\\latest\\bin\\sdkmanager.bat --sdk_root=E:\\DevTools\\android-sdk platform-tools platforms;android-35 build-tools;35.0.0"
```

Let op: in PowerShell komt de `y`-invoer niet door bij `--licenses`. Gebruik daarvoor Git Bash.

## 4. Omgevingsvariabelen (gebruiker, permanent; PowerShell)

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME','E:\DevTools\jdk-17','User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME','E:\DevTools\android-sdk','User')
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME','E:\DevTools\gradle-home','User')
[Environment]::SetEnvironmentVariable('ANDROID_USER_HOME','E:\DevTools\android-user','User')
# en aan de gebruikers-Path toevoegen:
#   E:\DevTools\jdk-17\bin
#   E:\DevTools\android-sdk\platform-tools
#   E:\DevTools\android-sdk\cmdline-tools\latest\bin
```

Open daarna een nieuwe terminal, zodat de variabelen actief worden.

## 5. Gradle: build-output en temp naar E:

`E:\DevTools\gradle-home\init.d\build-dir-on-e.init.gradle.kts`:

```kotlin
gradle.beforeProject {
    val rel = if (path == ":") "_root" else path.removePrefix(":").replace(':', '/')
    layout.buildDirectory.set(File("E:/DevTools/builds/${rootProject.name}/$rel"))
}
```

`E:\DevTools\gradle-home\gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=E:/DevTools/tmp
org.gradle.workers.max=2
kotlin.compiler.execution.strategy=in-process
```

**Waarom zo zuinig?** Met `-Xmx2048m` crashte de Gradle-daemon met
`Failed to commit metaspace` (hs_err-log). Het systeem heeft 16 GB RAM, maar het wisselbestand
staat op C: en kan niet groeien omdat C: vol is. Daardoor was maar ~2,4 GB virtueel geheugen beschikbaar.
Deze instellingen draaien één JVM met een kleine heap. Een volledige build duurt zo ~2,5 minuut.
Als C: weer ruimte heeft, of het wisselbestand naar E: verhuist, kunnen deze limieten weer omhoog.

`local.properties` in de projectmap (staat in `.gitignore`):

```properties
sdk.dir=E\:/DevTools/android-sdk
```

## 6. Bouwen en installeren

```sh
./gradlew assembleDebug
# APK: E:\DevTools\builds\EazyRecipes\app\outputs\apk\debug\app-debug.apk
```

Installeren op de telefoon:
- **Via USB:** zet op de telefoon Ontwikkelaarsopties → USB-foutopsporing aan en voer dan `adb install -r <pad naar app-debug.apk>` uit.
- **Zonder kabel:** kopieer de APK naar de telefoon, open hem en sta "installeren uit onbekende bronnen" toe.
