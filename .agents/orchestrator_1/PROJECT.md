# Project: Ember Android App Audit & Overhaul

## Architecture
- **Application Namespace**: `com.ember.companion`
- **Presentation Layer**: Jetpack Compose (Material 3 BOM 2024.09.00), Navigation Compose 2.8.4, `EmberViewModel`
- **Data & Domain Layer**:
  - `Generator.kt`, `Banks.kt`: Combinatorial procedural generator
  - `LabModel.kt`: Structured scenario model (`Brief`, `BriefSlot`, `Part`, `Dials`)
  - `CharacterCard.kt`, `CardPlatforms.kt`: Character Card V1/V2/V3 parser, dual-chunk PNG encoder, platform adapters
  - `AiClient.kt`: Network client for OpenAI, Anthropic, and Google Gemini
  - `SettingsStore.kt`: Encrypted preferences for API keys and provider models
  - Room Database: `ScenarioDao`, `MediaItemDao`
- **Build Infrastructure**:
  - Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10 with Compose Compiler plugin
  - JVM 17 (OpenJDK 17 on ARM64 Linux / Termux)
  - Custom ARM64 AAPT2 override: `/data/data/com.termux/files/usr/bin/aapt2`
  - Output APK: `app/build/outputs/apk/debug/app-debug.apk`
  - Deployment targets: `/sdcard/Download/PersonaForge-debug.apk` and `~/PersonaForge-debug.apk`

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F01 | Provider Default & Parameters Fix | Fix Anthropic model (`claude-3-5-sonnet-20241022`), support OpenAI `max_completion_tokens`, add temperature configuration | M1 | Survey (Explorer 2) |
| F02 | System Prompt & Dynamic Token Bounds | Remove `<300 words` ceiling; dynamic depth scaling based on token budget; multi-genre tone guidance | M1 | Survey (Explorer 2) |
| F03 | Structured AI Generation & Slot Integration | Connect AI to structured `BriefSlot` / `Part` components; slot-targeted AI generation actions | M1 | Survey (Explorer 2) |
| F04 | Character Card AI Assist & Bank Expansion | AI generation for `first_mes` and `mes_example`; expand procedural word banks in `Banks.kt` | M1 | Survey (Explorer 2) |
| F05 | Navigation & Shell Redesign | Animated tab transitions (`AnimatedContent`), backstack navigation, unified edge-to-edge insets, warm Ember tokens | M2 | Survey (Explorer 1) |
| F06 | DiscoverScreen Modernization | Sleek top bar with forward navigation, slim progress indicator, full-width search overlay, clean launcher grid with full engine labels and elevated bookmarks | M2 | Survey (Explorer 1) |
| F07 | ScenarioLabScreen Overhaul | Collapsible Steering Card with active dials summary, dual Reading View vs Tuning View, decluttered contextual menus, replace 440dp dialogs with ModalBottomSheet + imePadding | M2 | Survey (Explorer 1) |
| F08 | LibraryScreen Redesign | Extended Floating Action Button for import, Grid vs List view toggle, sorting menu (date/title/size) + Favorites filter chip, modern Media Detail ModalBottomSheet | M2 | Survey (Explorer 1) |
| F09 | Full Build & Test Integrity | `./gradlew testDebugUnitTest` passing 100%, `./gradlew assembleDebug` exiting 0, copy APK to `/sdcard/Download/PersonaForge-debug.apk` and `~/PersonaForge-debug.apk` | M3 | Survey (Explorer 3) |
| F10 | Agent-as-Judge Acceptance Evaluation | Independent evaluation verifying UI flow is demonstrably more streamlined and cohesive; independent evaluation verifying scenario/idea generation is improved and robust | M3 | ORIGINAL_REQUEST |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Generation Logic & AI Integration Overhaul | `SettingsStore.kt`, `AiClient.kt`, `Banks.kt`, `Generator.kt`, `CharacterCard.kt`, `EmberViewModel.kt` (AI logic), unit tests | None | IN_PROGRESS |
| M2 | UI/UX & Flow Overhaul | `Theme.kt`, `EmberRoot.kt`, `DiscoverScreen.kt`, `ScenarioLabScreen.kt`, `CardExportSheet.kt`, `LibraryScreen.kt`, `EmberViewModel.kt` (UI state) | M1 | PLANNED |
| M3 | Build Integrity, E2E Verification & Agent-as-Judge Evaluation | Test runner, `./gradlew assembleDebug`, APK copy, independent UX and Generation evaluations | M1, M2 | PLANNED |

---

## Interface Contracts

### M1 (Generation Logic) ↔ M2 (UI Screens)
- **AI Completion API**:
  `AiClient.complete(prompt: String, context: String = "", maxTokens: Int = 800, temperature: Double? = null): AiResult`
- **Slot Targeted Generation**:
  `EmberViewModel.generateAiSlot(slotKey: String, actionType: String, customPrompt: String? = null)`
- **Full AI Scenario Generation**:
  `EmberViewModel.generateAiScenario(premise: String, tone: String? = null, maxTokens: Int = 1200)`
- **Character Card Enrichment**:
  `EmberViewModel.enrichCharacterCard(card: CharacterCard): Flow<CharacterCardEnrichState>`
- **Dials & Steering**:
  `Dials` remains backward compatible with existing `LabModel.kt` data structures while exposing rich summary strings for the collapsed steering view.

---

## Code Layout & Write Boundaries
- **Milestone 1 Worker Owns**:
  - `app/src/main/java/com/ember/companion/core/SettingsStore.kt`
  - `app/src/main/java/com/ember/companion/core/AiClient.kt`
  - `app/src/main/java/com/ember/companion/data/Banks.kt`
  - `app/src/main/java/com/ember/companion/data/Generator.kt`
  - `app/src/main/java/com/ember/companion/data/CharacterCard.kt`
  - `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt` (AI generation & slot-mutation methods)
  - `app/src/test/java/com/ember/companion/core/`
  - `app/src/test/java/com/ember/companion/data/`
- **Milestone 2 Worker Owns**:
  - `app/src/main/java/com/ember/companion/ui/theme/Theme.kt`
  - `app/src/main/java/com/ember/companion/ui/EmberRoot.kt`
  - `app/src/main/java/com/ember/companion/ui/discover/DiscoverScreen.kt`
  - `app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt`
  - `app/src/main/java/com/ember/companion/ui/lab/CardExportSheet.kt`
  - `app/src/main/java/com/ember/companion/ui/library/LibraryScreen.kt`
  - `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt` (UI state bindings: view modes, search overlays, collapsed steering state)
