# Handoff Report: Ember Build, Dependency, and Test Infrastructure Survey

**Agent**: `explorer_survey_3`  
**Working Directory**: `/data/data/com.termux/files/home/Ember/.agents/explorer_survey_3`  
**Timestamp**: `2026-09-27T01:31:00Z`  
**Target Project**: `/data/data/com.termux/files/home/Ember`  

---

## 1. Observation

### 1.1 Gradle Wrapper & Toolchain
- **Gradle Wrapper** (`gradle/wrapper/gradle-wrapper.properties` lines 1–8):
  - Distribution URL: `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip`
  - Gradle Version: **Gradle 9.3.1**
  - Toolchain plugin: `org.gradle.toolchains.foojay-resolver-convention` version `1.0.0` declared in `settings.gradle.kts` (line 15).
- **Launcher & Daemon JVM** (verified via `./gradlew -v`):
  - Java Version: `openjdk version "17.0.20" 2026-07-21`
  - Runtime: OpenJDK 64-Bit Server VM (`JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/`)
  - Platform/OS: Linux 6.12.30 aarch64 (Termux on Android 16/36)

### 1.2 Android SDK Configuration
- **Local SDK Path** (`local.properties` line 1):
  ```properties
  sdk.dir=/data/data/com.termux/files/home/android-sdk
  ```
- **Installed SDK Packages** (`/data/data/com.termux/files/home/android-sdk`):
  - Platforms: `android-34`, `android-36`, `android-36.1`, `android-37.0`
  - Build-Tools: `36.0.0`
  - Commandline-tools and platform-tools present.
- **Module SDK Target Settings** (`app/build.gradle.kts` lines 8–18):
  ```kotlin
  android {
    namespace = "com.ember.companion"
    compileSdk = 37

    defaultConfig {
      applicationId = "com.ember.companion"
      minSdk = 24
      targetSdk = 34
      versionCode = 1
      versionName = "1.0"
    }
  ```
- **Java Compatibility** (`app/build.gradle.kts` lines 30–33):
  ```kotlin
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  ```

### 1.3 Versions and Dependencies Catalog (`gradle/libs.versions.toml`)
- **Key Version Numbers**:
  - Android Gradle Plugin (AGP): `9.1.1` (`com.android.application`)
  - Kotlin: `2.2.10`
  - Compose BOM: `2024.09.00`
  - Google KSP: `2.3.5` (`com.google.devtools.ksp`)
  - Room: `2.7.0` (`androidx.room:room-runtime`, `androidx.room:room-ktx`, `androidx.room:room-compiler`)
  - Navigation Compose: `2.8.4`
  - Activity Compose: `1.10.1`
  - Lifecycle: `2.11.0` (`runtime-ktx`, `runtime-compose`, `viewmodel-compose`)
  - Coroutines: `1.10.2` (`kotlinx-coroutines-core`, `kotlinx-coroutines-android`)
  - Serialization: `1.7.3` (`kotlinx-serialization-json`)
  - Coil Compose: `2.7.0` (`io.coil-kt:coil-compose`)
  - OkHttp: `4.10.0` (`com.squareup.okhttp3:okhttp`)
  - WebKit: `1.14.0` (`androidx.webkit:webkit`)
  - JUnit: `4.13.2`
  - JSON: `20250107` (`org.json:json`)
  - AndroidX Test JUnit: `1.3.0` (`androidx.test.ext:junit`)
- **Compiler Configuration**:
  - Jetpack Compose uses the modern **Kotlin Compose Compiler Plugin** (`org.jetbrains.kotlin.plugin.compose` version 2.2.10) applied via `alias(libs.plugins.kotlin.compose)` in both root and `:app` build scripts. There is no legacy `composeOptions.kotlinCompilerExtensionVersion`.
  - Room code generation uses KSP (`ksp(libs.androidx.room.compiler)`).
  - Data serialization uses Kotlin Serialization plugin (`org.jetbrains.kotlin.plugin.serialization`).

### 1.4 Termux-Specific Daemon & Build Properties
- **Project Configuration** (`gradle.properties` lines 9–26):
  ```properties
  org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:+UseParallelGC
  org.gradle.parallel=true
  kotlin.code.style=official
  android.nonTransitiveRClass=true
  org.gradle.caching=true
  org.gradle.configuration-cache=true
  # Set the maximum number of workers to 2 to avoid overloading memory on Termux.
  org.gradle.workers.max=2
  # Set the Kotlin compiler execution strategy to in-process to avoid "Could not
  # connect to Kotlin compile daemon" error.
  kotlin.compiler.execution.strategy=in-process
  ```
- **Global User Gradle Configuration** (`~/.gradle/gradle.properties` line 1):
  ```properties
  android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
  ```
  - Direct observation: `/data/data/com.termux/files/usr/bin/aapt2` is an executable ARM64 ELF binary (`Android Asset Packaging Tool (aapt) 2.20-android-16.0.0_r4`, size 3,594,152 bytes) dynamically linked for Termux Bionic libc. This override is critical because the Maven-distributed `aapt2` binary is x86_64 glibc and cannot run natively on ARM64 Termux.
- **Observed Build Warnings/Quirks**:
  - `WARNING: The option setting 'android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2' is experimental.`
  - KSP executes with an unhandled headless AWT exception (`java.lang.NullPointerException` in `AWT-EventQueue-0` on `FileDocumentManager`), which is non-fatal; KSP successfully outputs symbols and the build succeeds.
  - Per core brain memory (`2026-09-04_gradle-test-runner-configuration-cache-in-termux.md`): If `./gradlew clean` or file deletion occurs, Gradle configuration cache can trigger `NoSuchFileException` on in-progress binary test result files. Running with `--no-configuration-cache` recovers execution cleanly.

### 1.5 Test Infrastructure and Existing Test Suites
- **Test Source Directory**:
  - Only `app/src/test/java/` exists (there is NO `app/src/androidTest/`).
  - All tests are pure JVM unit tests; no emulator, device, or Robolectric is needed.
- **Existing Test Classes**:
  1. `com.ember.companion.core.AiResponseParsingTest` (`app/src/test/java/com/ember/companion/core/AiResponseParsingTest.kt`, 138 lines):
     - 11 unit tests.
     - Exercises parsing logic across OpenAI, Gemini, and Anthropic responses: tests `looksLikeAnAnswer()`, refusal handling, reasoning content fallback, part-joining, placeholder stripping (`null`, `undefined`, `NaN`), and null JSON primitive handling.
  2. `com.ember.companion.data.CharacterCardTest` (`app/src/test/java/com/ember/companion/data/CharacterCardTest.kt`, 425 lines):
     - 29 unit tests.
     - Exercises Character Card specification V1, V2, and V3 JSON conversion; PNG tEXt chunk encoding (`ccv3`, `chara`), CRC verification, and recovery; CHARX zip archive packaging; third-party platform adaptations (Chub, Agnai, JanitorAI, CharacterAI, SillyTavern); and custom field mapping.
  3. `com.ember.companion.data.LabModelTest` (`app/src/test/java/com/ember/companion/data/LabModelTest.kt`, 198 lines):
     - 21 unit tests.
     - Exercises idea and scenario procedural generation: `Generator.brief()`, `Banks`, `Dials` (explicitness, power, pace, pov), `Taste` (pinned/blocked attributes), slot extraction, part replacement (`withPart`), and retitling.
- **Unit Test JSON Stub Resolution**:
  - `app/build.gradle.kts` lines 75–77:
    ```kotlin
    testImplementation(libs.junit)
    // The android.jar shipped for unit tests only stubs org.json, so card
    // serialisation cannot be exercised without a real implementation.
    testImplementation(libs.json)
    ```
    `libs.json` supplies real `org.json:json:20250107`, permitting `JSONObject` manipulation in JVM tests without throwing `RuntimeException("Stub!")`.
- **Verified Test Execution**:
  - Command: `./gradlew testDebugUnitTest`
  - Result: `BUILD SUCCESSFUL in 35s` (28 actionable tasks, 61/61 tests passed, 0 failures, 0 skipped, test execution time 1.793s).
  - Report generated at: `app/build/reports/tests/testDebugUnitTest/index.html`.

### 1.6 Build Outputs & User Deployment Rule Requirement
- **Generated APK Output**:
  - Build task: `./gradlew assembleDebug`
  - Output path: `/data/data/com.termux/files/home/Ember/app/build/outputs/apk/debug/app-debug.apk`
  - Size: 19,587,913 bytes (~19.6MB)
  - Metadata: `/data/data/com.termux/files/home/Ember/app/build/outputs/apk/debug/output-metadata.json`
- **Mandatory User Rule** (from `/data/data/com.termux/files/home/AGENTS.md`):
  > "Android deployment: After building assembleDebug, ALWAYS copy app-debug.apk to /sdcard/Download/PersonaForge-debug.apk and ~/PersonaForge-debug.apk."
- **Concrete Copy Command**:
  ```bash
  cp /data/data/com.termux/files/home/Ember/app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/PersonaForge-debug.apk && cp /data/data/com.termux/files/home/Ember/app/build/outputs/apk/debug/app-debug.apk /data/data/com.termux/files/home/PersonaForge-debug.apk
  ```
  - Both `/sdcard/Download/PersonaForge-debug.apk` and `/data/data/com.termux/files/home/PersonaForge-debug.apk` exist and are accessible for writes.

---

## 2. Logic Chain

1. **Build Environment Viability**:
   - *Observation*: `local.properties` points to `/data/data/com.termux/files/home/android-sdk`, which has `platforms/android-37.0` and `build-tools/36.0.0`. `JAVA_HOME` points to Termux OpenJDK 17.0.20. `~/.gradle/gradle.properties` overrides `aapt2` with the native ARM64 binary `/data/data/com.termux/files/usr/bin/aapt2`.
   - *Inference*: AGP 9.1.1 and Gradle 9.3.1 run directly in Termux userland without cross-compilation emulation. The native `aapt2` override eliminates the ELF format incompatibility of Maven binaries.

2. **Termux Concurrency & Stability**:
   - *Observation*: `gradle.properties` restricts workers to `org.gradle.workers.max=2`, sets JVM heap to `-Xmx2048m -XX:+UseParallelGC`, and forces `kotlin.compiler.execution.strategy=in-process`.
   - *Inference*: Multiple worker threads or a separate Kotlin compilation daemon would exceed Termux memory limits or fail during Unix domain socket IPC. In-process compilation with max 2 workers keeps the build stable and predictable.

3. **Test Fast-Feedback Loop**:
   - *Observation*: All 61 tests reside in `app/src/test/java/`, use JUnit 4, and include real `org.json`. Running `./gradlew testDebugUnitTest` executes in ~35s (warm cache ~15s), with tests running in under 2 seconds.
   - *Inference*: Any modifications to R1 (UI screens), R2 (generation logic, prompts, `Generator.kt`, `Banks.kt`, `AiClient.kt`), or data models can be verified instantly via pure JVM unit tests without needing an emulator or Android device attached. New tests for upgraded generator logic should be placed in `app/src/test/java/com/ember/companion/data/` or `core/`.

4. **Release / Verification Pipeline**:
   - *Observation*: Running `./gradlew assembleDebug` compiles the full debug APK to `app/build/outputs/apk/debug/app-debug.apk`.
   - *Inference*: To fulfill Requirement R3 and comply with the mandatory deployment rule in `AGENTS.md`, any implementer must execute `./gradlew assembleDebug` followed by copying `app-debug.apk` to `/sdcard/Download/PersonaForge-debug.apk` and `~/PersonaForge-debug.apk`.

---

## 3. Caveats

- **No Instrumented Tests**: `app/src/androidTest` is not configured. UI tests cannot be run via Gradle without an attached Android device or running emulator via ADB. Compose UI verification relies on unit testing ViewModel logic and manual/emulator inspection.
- **Configuration Cache Invalidation**: If build directories (`build/` or `test-results/`) are wiped manually, Gradle configuration cache may occasionally throw `NoSuchFileException`. If that occurs, run with `--no-configuration-cache` once to regenerate.
- **KSP AWT Stacktrace**: When Room KSP runs, an innocuous IntelliJ AWT NullPointerException appears in stdout/stderr. It does not indicate a compilation failure and can be ignored.

---

## 4. Conclusion

1. **Infrastructure Health**: The Ember Android build and test infrastructure is 100% operational on Termux. Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10 (with Compose Compiler plugin), and Room 2.7.0 (KSP 2.3.5) compile cleanly and stably.
2. **Test Capability**: A comprehensive 61-test JVM unit test suite is active and passes with 100% success. Fast automated regression testing for AI response parsing and idea/scenario generation logic is fully established.
3. **Deployment Command**: After code changes, running `./gradlew assembleDebug` and copying the APK to the two required destinations (`/sdcard/Download/PersonaForge-debug.apk` and `~/PersonaForge-debug.apk`) satisfies all project and user-rule deployment requirements.

---

## 5. Verification Method

To independently verify the build, tests, and deployment pipeline, run the following commands:

```bash
# 1. Run the entire unit test suite (61 tests)
cd /data/data/com.termux/files/home/Ember
./gradlew testDebugUnitTest --no-configuration-cache

# 2. Inspect test report results
cat app/build/reports/tests/testDebugUnitTest/index.html | grep -E "tests|failures|successful"

# 3. Build the debug APK
./gradlew assembleDebug

# 4. Confirm output APK existence and size
ls -lh app/build/outputs/apk/debug/app-debug.apk

# 5. Execute mandatory user deployment copy rule
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/PersonaForge-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk /data/data/com.termux/files/home/PersonaForge-debug.apk

# 6. Verify copies exist
ls -lh /sdcard/Download/PersonaForge-debug.apk ~/PersonaForge-debug.apk
```

**Invalidation Conditions**:
- Any build failure or non-zero exit code from `./gradlew assembleDebug` or `./gradlew testDebugUnitTest`.
- Failure of any of the 61 unit tests.
- Missing `app-debug.apk` at `app/build/outputs/apk/debug/app-debug.apk`.
