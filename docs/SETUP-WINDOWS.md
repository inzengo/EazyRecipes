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

`E:\DevTools\gradle-home\init.d\build-dir-on-e.init.gradle.kts` maakt van `<project>\build` een
**directory junction** naar `E:\DevTools\builds\<project>\<module>` (bijvoorbeeld `app\build` → `E:\DevTools\builds\EazyRecipes\app`).
Gradle ziet dus een pad op C:, maar de bestanden staan fysiek op E:. Een junction maken kan zonder adminrechten.

**Waarom geen `buildDirectory` direct op E:?** Dat werkte tot Room erbij kwam. KSP (de code-generator van Room) en
AGP's `isIncludeAndroidResources` rekenen paden relatief aan de projectmap uit. Bij twee schijven crashen ze met
`this and base files have different roots`.

```kotlin
import java.nio.file.Files

gradle.beforeProject {
    val rel = if (path == ":") "_root" else path.removePrefix(":").replace(':', '/')
    val target = File("E:/DevTools/builds/${rootProject.name}/$rel")
    if (projectDir.absolutePath.uppercase().startsWith("E:")) {   // project staat al op E:
        layout.buildDirectory.set(target)
        return@beforeProject
    }
    val link = File(projectDir, "build")
    // `clean` zou de junction zelf weggooien → alleen de inhoud op E: wissen.
    // afterEvaluate, zodat dit ná de base-plugin komt (die voegt `build` anders opnieuw toe).
    // Via het junction-pad (C:) wissen: anders merkt Gradle niet dat de outputs weg zijn.
    afterEvaluate {
        tasks.withType(Delete::class.java).matching { it.name == "clean" }.configureEach {
            setDelete(provider { link.listFiles()?.toList().orEmpty() })
        }
    }
    if (Files.exists(link.toPath())) {
        val real = link.toPath().toRealPath().toFile()
        if (real.canonicalPath.equals(target.canonicalPath, ignoreCase = true)) return@beforeProject
        throw GradleException("$link bestaat al en wijst niet naar $target (maar naar $real).")
    }
    target.mkdirs()
    val p = ProcessBuilder("cmd", "/c", "mklink", "/J", link.absolutePath, target.absolutePath)
        .redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    if (p.waitFor() != 0) throw GradleException("Junction maken mislukt: $out")
}
```

Staat er nog een echte `build`-map (geen junction), dan stopt het script met een melding. Verwijder die map dan; het is alleen build-output.

`E:\DevTools\gradle-home\gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8 -Djava.io.tmpdir=E:/DevTools/tmp
org.gradle.workers.max=2
kotlin.compiler.execution.strategy=in-process
```

**Geschiedenis:** eerst stond hier `-Xmx1g -XX:MaxMetaspaceSize=512m`. Met `-Xmx2048m` crashte de daemon toen met
`Failed to commit metaspace`. Het wisselbestand staat op C: en C: was vol, waardoor er maar ~2,4 GB virtueel geheugen was.
Na het opruimen van C: (26 GB vrij, 2026-11-19) kon het weer naar 2 GB. Met Room/KSP erbij liep 512 MB metaspace vol.
Raakt C: weer vol, ga dan terug naar de zuinige waarden.

**Robolectric** (database-tests) downloadt een Android-jar naar `~/.m2`. `app/build.gradle.kts` zet `user.home` voor
de test-JVM op `E:\DevTools\robolectric-home`, zodat dat niet op C: belandt.

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
