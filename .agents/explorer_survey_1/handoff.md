# UI/UX Architecture Survey & Redesign Blueprint

## 1. Observation

### 1.1 Architecture & Project Overview
- **App Name & Namespace**: `Ember` (`com.ember.companion`), minSdk 24, targetSdk 34, compileSdk 37.
- **Tech Stack**: Kotlin 2.0+, Jetpack Compose with Material 3 (BOM), AndroidX Navigation Compose, Room with KSP, OkHttp, Kotlinx Serialization, Coil 2.6.
- **Build Status**: `./gradlew assembleDebug` compiles successfully (`BUILD SUCCESSFUL in 56s`, 38 actionable tasks).

### 1.2 Component & File Catalog
| Component / Screen | File Path | Primary Composable | State & Data Models |
|---|---|---|---|
| **Activity Entry** | `app/src/main/java/com/ember/companion/MainActivity.kt` | `MainActivity` | `EmberApp.container`, `Diag.consumePending()`, `vm.ageGatePassed` |
| **Theme & Tokens** | `app/src/main/java/com/ember/companion/ui/theme/Theme.kt` | `EmberTheme` | `EmberDark`, `EmberLight`, `EmberType` |
| **App Root & Nav** | `app/src/main/java/com/ember/companion/ui/EmberRoot.kt` | `EmberRoot` | `vm.tab`, `vm.snackMessage`, `Tab.entries` |
| **Monolithic ViewModel** | `app/src/main/java/com/ember/companion/ui/EmberViewModel.kt` | `EmberViewModel` (1,196 lines) | `BrowserState`, `LabState`, `CardExtras`, Room flows |
| **Discover Screen** | `app/src/main/java/com/ember/companion/ui/discover/DiscoverScreen.kt` | `DiscoverScreen`, `Launcher`, `createWebView` | `BrowserState`, `SearchEngine`, `Bookmark`, `HistoryEntry` |
| **Scenario Lab** | `app/src/main/java/com/ember/companion/ui/lab/ScenarioLabScreen.kt` | `ScenarioLabScreen`, `GeneratePane`, `LibraryPane`, `SteeringCard`, `PartRow` | `LabState`, `Brief`, `BriefSlot`, `Part`, `Dials`, `Scenario` |
| **Card Export/Import** | `app/src/main/java/com/ember/companion/ui/lab/CardExportSheet.kt` | `CardExportDialog`, `CardImportDialog`, `CustomMappingEditor` | `CardExtras`, `CardPlatforms`, `CharacterCard` |
| **Library Screen** | `app/src/main/java/com/ember/companion/ui/library/LibraryScreen.kt` | `LibraryScreen`, `LibraryCard`, `MediaDetailDialog` | `MediaItem`, `libraryQuery`, `libraryKind`, `importBusy` |
| **Settings Screen** | `app/src/main/java/com/ember/companion/ui/settings/SettingsScreen.kt` | `SettingsScreen` | `SettingsStore`, `VpnMonitor.State`, `AiProvider` |
| **Age Gate & Crash** | `app/src/main/java/com/ember/companion/ui/AgeGate.kt`, `CrashScreen.kt` | `AgeGate`, `CrashScreen` | `SettingsStore.ageGatePassed`, `Diag` |
| **Generator Engine** | `app/src/main/java/com/ember/companion/data/Generator.kt`, `Banks.kt`, `LabModel.kt` | `Generator.brief()`, `character()`, `complication()` | `Banks`, `Dials`, `Brief`, `Part`, `Taste` |
| **AI Client Engine** | `app/src/main/java/com/ember/companion/core/AiClient.kt` | `AiClient.complete()`, `probe()` | `AiProvider`, `AiResult`, OpenAI / Anthropic / Gemini JSON |

---

### 1.3 Detailed Screen-by-Screen Observations & Exact Line References

#### A. Navigation & Inset Handling (`EmberRoot.kt:70-103`)
```kotlin
Scaffold(
    bottomBar = {
        NavigationBar {
            Tab.entries.forEach { entry ->
                NavigationBarItem(
                    selected = tab == entry,
                    onClick = { vm.selectTab(entry) },
                    icon = { Icon(entry.icon(), contentDescription = null) },
                    label = { Text(entry.label, maxLines = 1) },
                )
            }
        }
    },
    snackbarHost = { SnackbarHost(snackHost) },
) { inner ->
    val screenPadding = PaddingValues(...)
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.DISCOVER -> DiscoverScreen(vm = vm, contentPadding = screenPadding)
            Tab.LIBRARY -> LibraryScreen(vm = vm, contentPadding = screenPadding)
            Tab.LAB -> ScenarioLabScreen(vm = vm, contentPadding = screenPadding)
            Tab.SETTINGS -> SettingsScreen(vm = vm, contentPadding = screenPadding)
        }
    }
}
```
- **Tab switching**: Direct `when (tab)` evaluation without `AnimatedContent` or crossfade transitions. Switching tabs produces an instantaneous, jarring cut.
- **Double Inset Padding**: `EmberRoot` passes `screenPadding` (calculated from its `inner` insets) to all screens. However, each child screen (`DiscoverScreen.kt:165`, `ScenarioLabScreen.kt:99`, `LibraryScreen.kt:114`, `SettingsScreen.kt:92`) defines its own nested `Scaffold(contentWindowInsets = WindowInsets(0))` and then applies `Modifier.padding(inner).padding(contentPadding).fillMaxSize()`.
- **System Back Handling**: There is no backstack for tab navigation. If a user moves from Discover to Lab and presses the Android hardware Back button, the app immediately terminates (or age gate / launcher depending on active state) instead of navigating back to the previous tab.

#### B. DiscoverScreen (`DiscoverScreen.kt:99-361`, `415-602`)
- **Search Engine Selector (`DiscoverScreen.kt:451`)**:
  ```kotlin
  OutlinedButton(onClick = { onEngineMenuToggle(true) }) {
      Text(engine.label.take(3), style = MaterialTheme.typography.labelMedium)
      Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change search engine")
  }
  ```
  Truncates engine names to 3 letters: `"Duc"`, `"Goo"`, `"Bin"`, `"Sta"`, `"Por"`, `"XNX"`. This is unintuitive and unpolished.
- **URL Bar In TopAppBar (`DiscoverScreen.kt:197-229`)**:
  ```kotlin
  TopAppBar(
      title = {
          OutlinedTextField(
              value = urlDraft.ifBlank { browser.url },
              onValueChange = { urlDraft = it },
              singleLine = true,
              ...
          )
      },
      navigationIcon = { ... },
      actions = {
          if (browser.loading) { CircularProgressIndicator(...) }
          IconButton(...) { /* reload */ }
          IconButton(...) { /* bookmark */ }
          IconButton(...) { /* overflow */ }
      }
  )
  ```
  The URL bar is crammed into the `TopAppBar` title area alongside up to 5 action icons. On typical mobile screens (<400dp width), the text input is crushed into a tiny box of barely ~120dp. There is no clear ("✕") icon, no paste action, and no forward navigation button.
- **No Forward Navigation**: `WebViewClient` updates `browser.canGoForward` (`DiscoverScreen.kt:653`, `723`), but there is NO forward button in the top bar or overflow menu.
- **Loading Indicator (`DiscoverScreen.kt:239-247`)**: Uses a circular progress indicator inline in the actions row. It appears and disappears abruptly, causing action icons to jump sideways during page loads.
- **Launcher Vertical Bloat (`DiscoverScreen.kt:493-528`)**: Loops through 4 categories of sources and renders individual full-width Cards for every single link. Bookmarks and recent history are buried beneath multiple viewports of scrolling cards.
- **Bookmark Chips (`DiscoverScreen.kt:549`)**: Forced `width(150.dp)` with single line truncation, clipping most saved bookmark titles.

#### C. ScenarioLabScreen (`ScenarioLabScreen.kt:80-633`, `CardExportSheet.kt:58-407`)
- **Steering Card Viewport Dominance (`ScenarioLabScreen.kt:257-270`, `641-731`)**:
  - The `SteeringCard` contains:
    - 4 dial rows × 3 filter chips = 12 chips
    - Premise text field (`minLines = 2`)
    - Seed text field + "Reroll seed" button
  - This card consumes ~480dp of vertical space. On most phones, the primary "Full brief" action button is pushed below the fold.
- **PartRow Visual Cacophony (`ScenarioLabScreen.kt:760-811`)**:
  - For every part of the generated brief (up to 15-20 parts across Cast, Frame, Setting, Open, Beats, Twist, Close), `PartRow` renders:
    - Text value
    - Refresh icon button (reroll)
    - Lock/unlock icon button
    - Sub-row with: "Pin" text button, "Block" text button, and "Use AI text here" text button!
  - This results in 60+ buttons scattered throughout the text. The generated creative scenario reads like a dense control panel rather than a story or roleplay brief.
- **Fixed-Height AlertDialogs (`ScenarioLabScreen.kt:968`, `1053`; `CardExportSheet.kt:73`)**:
  - `ScenarioEditorDialog` uses `Modifier.height(440.dp)`.
  - `BanksDialog` uses `Modifier.height(440.dp)`.
  - `CardExportDialog` uses `Modifier.heightIn(max = 460.dp)`.
  - When the virtual keyboard appears inside these dialogs, the input fields are obscured, the dialog overflows, and buttons are pushed out of reach.
- **Segmented Mode Ambiguity (`ScenarioLabScreen.kt:127-141`)**:
  - Lab uses a top segmented button for "Generate" vs "Saved" (`Mode.LIBRARY`).
  - Having a "Saved" library tab inside Lab while the bottom navigation bar ALSO features a main "Library" tab creates mental model confusion.
- **AI Assist Section (`ScenarioLabScreen.kt:441-629`)**:
  - Pinned at the very bottom of the long scrolling column.
  - Generates responses in an "AI notes" card that can only append to the end of the brief as a generic note (`EmberViewModel.kt:1127-1135`), rather than integrating with specific slots (e.g., Cast motivation, Twist, Opening dialogue).

#### D. LibraryScreen (`LibraryScreen.kt:84-298`, `300-384`, `421-535`)
- **Pinned Full-Width "Import files" Button (`LibraryScreen.kt:188-211`)**:
  - A prominent full-width button sits directly between the filter chips and the grid. It occupies ~60dp of vertical space continuously, even when the user has 100+ files and just wants to browse.
- **Grid Layout Rigidity (`LibraryScreen.kt:225-241`)**:
  - `GridCells.Adaptive(minSize = 116.dp)` with 3:4 aspect ratio.
  - Truncates item titles to 2 lines (~15 characters).
  - Audio and text files display identical generic vector icons with no waveform, snippet, or metadata.
  - No toggle between Grid and List view.
- **Absence of Essential Sorting & Filtering**:
  - No sort selector (Date Added, Title A-Z, Size, Duration).
  - No filter for "Favorites" (even though each item has a favorite heart).
  - No filter or grouping for "Collections" (even though `MediaItem` has a `collection` field).
- **Cramped Media Detail Dialog (`LibraryScreen.kt:434-535`)**:
  - 420dp fixed-height AlertDialog containing Title, Tags, Collection, Notes, metadata, Open, Favorite, Remove, Cancel, Save.
  - Software keyboard immediately hides the confirm/dismiss buttons.

#### E. Theme & Aesthetics (`Theme.kt:19-71`)
- Basic color scheme with dark background (`0xFF0B0B10`) and ember primary (`0xFFE2553D`).
- Surfaces are flat with zero subtle tonal elevation or atmospheric depth.
- Lacks modern design accents: no glowing ember subtle gradients, no card border illumination, no badge pill styling, and standard plain buttons.

---

## 2. Logic Chain

```
[Observation 1.3.A] EmberRoot uses un-animated when(tab) + double Scaffold padding
   │
   ├──> [Reasoning Step 1]: Abrupt screen transitions destroy fluid perception;
   │    lack of tab backstack violates core Android navigation guidelines (CDD/Material).
   │
   └──> [Reasoning Step 2]: Double-padding insets cause unpredictable top/bottom offsets
        when soft keyboard opens or status bar mode toggles.

[Observation 1.3.B] DiscoverScreen squeezes URL field into TopAppBar title slot beside 5 icons
   │
   ├──> [Reasoning Step 3]: On mobile viewports (<400dp width), the URL field is barely ~120dp wide.
   │    Users cannot comfortably inspect or edit web addresses.
   │
   ├──> [Reasoning Step 4]: Absence of a Forward button makes browsing a one-way street.
   │
   └──> [Reasoning Step 5]: Search engine picker shows "Duc", "Goo", "Bin", causing severe visual friction.

[Observation 1.3.C] ScenarioLabScreen stacks 480dp SteeringCard + 10 Slot Cards + 60+ buttons
   │
   ├──> [Reasoning Step 6]: The primary call to action ("Full brief") is pushed below the fold.
   │
   ├──> [Reasoning Step 7]: Every line of text having 4 micro-buttons (Reroll, Lock, Pin, Block)
   │    generates intense visual noise that overwhelms the creative reading experience.
   │
   └──> [Reasoning Step 8]: 440dp fixed-height AlertDialogs fail when the virtual keyboard opens.

[Observation 1.3.D] LibraryScreen pins full-width "Import" button at top; lacks Sorting/Favorites/List view
   │
   ├──> [Reasoning Step 9]: Screen space is wasted on an action needed only occasionally.
   │
   └──> [Reasoning Step 10]: Users cannot find favorited media or sort by date/size, making
        the library unusable as it grows.
```

---

## 3. Caveats
1. **Read-Only Survey**: This report provides a structured audit and redesign specification. No main codebase files were altered during this survey.
2. **Device Hardware Variations**: Inset behavior was analyzed via Jetpack Compose Material 3 specifications. High-refresh-rate physical displays (90Hz/120Hz) and display cutouts (punch-hole/notches) will especially benefit from edge-to-edge unification and AnimatedContent.
3. **Adult Web Content Rendering**: The underlying Android `WebView` depends on system Chromium. Crash resilience (`onRenderProcessGone`) is already implemented in `DiscoverScreen.kt:664` and must be preserved during UI refactoring.

---

## 4. Conclusion & Concrete Redesign Blueprint

To transform Ember into an intuitive, modern, fluid, and efficient companion app, the following concrete improvements are recommended:

### Priority 1: Navigation & Root Shell Polish (`EmberRoot.kt`, `Theme.kt`)
1. **Smooth Animated Tab Transitions**:
   - Replace bare `when (tab)` with `AnimatedContent` utilizing a subtle crossfade and slide transition (`slideInHorizontally` + `fadeIn` / `slideOutHorizontally` + `fadeOut`).
2. **Backstack Support**:
   - Add backstack handling in `EmberViewModel` or `BackHandler` so pressing Back on non-Discover tabs returns to the previous tab or home.
3. **Unified Scaffold Insets**:
   - Let `EmberRoot` handle `NavigationBar` and provide content cleanly without double-nesting Scaffold padding, or pass `WindowInsets(0)` cleanly with proper `Modifier.imePadding()`.
4. **Enhanced Ember Visual Tokens**:
   - Elevate `EmberTheme`:
     - Introduce subtle card container elevations and warm outline borders (`Color(0xFF38231E)` in dark mode).
     - Give action buttons and chips rounded pill shapes (`CircleShape` / `RoundedCornerShape(12.dp)`).
     - Accentuate primary branding with glowing gradient hints on active buttons.

### Priority 2: DiscoverScreen Overhaul (`DiscoverScreen.kt`)
1. **Modern Browser Top/Bottom Bar Architecture**:
   - When in Browser Mode:
     - Keep the top bar clean: Display domain/title with a lock/shield indicator, reload button, and overflow menu.
     - Move navigation controls (Back, Forward, Share, Bookmarks, Home) to a streamlined bottom bar or unified pill.
     - Place a sleek `LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))` flush right underneath the top bar when `browser.loading == true`.
     - Clicking the address pill opens a dedicated, clean URL/Search input overlay with full width, clear button ("✕"), and paste shortcut.
2. **Launcher Redesign**:
   - Replace the awkward 3-letter engine button (`"Duc"`, `"Goo"`) with a clean horizontal filter chip selector or modern dropdown icon.
   - Re-organize sources into modern 2-column or grid cards with clean icons/category badges rather than a towering vertical list.
   - Elevate Bookmarks and Recent History to quick-access horizontal carousels near the top or as tabbed sections so returning users reach their content immediately.

### Priority 3: ScenarioLabScreen Overhaul (`ScenarioLabScreen.kt`, `CardExportSheet.kt`)
1. **Collapsible / Sheet Steering Controls**:
   - Transform the 480dp `SteeringCard` into an expandable / collapsible header ("Steering & Dials (4 active) ▾") or a dedicated bottom sheet.
   - When collapsed, display a compact summary row of current dials (e.g. `[Charged] [Steady Build] [Equal Power]`) so the "Generate" actions and scenario brief are instantly front and center.
2. **Dual View Modes: "Reading View" vs "Editor / Tuning View"**:
   - **Reading View (Default)**: Renders the scenario brief as a beautifully formatted, immersive prose card. Clean typography, elegant dividers, seamless paragraph flow. Includes a 1-tap "Copy", "Share", and "Save" button.
   - **Tuning View**: Enables slot and part rerolling, locking, pinning, and blocking.
3. **De-clutter Part Controls with Contextual Dropdown/BottomSheet**:
   - Remove the intrusive row of text buttons ("Pin", "Block", "Use AI text here") beneath every single line.
   - Instead, tap a line to open a sleek contextual menu or modal chip row (Lock, Pin, Block, AI).
4. **Replace Fixed 440dp Dialogs with ModalBottomSheet**:
   - Replace `ScenarioEditorDialog`, `BanksDialog`, and `CardExportDialog` with Material 3 `ModalBottomSheet`s with `Modifier.imePadding()`, ensuring seamless virtual keyboard interaction on any screen size.
5. **AI Assist Fluid Integration**:
   - Allow AI generated suggestions to directly target and replace specific slots (e.g., "Replace Beats with AI suggestion" or "Use as Character Want").

### Priority 4: LibraryScreen Overhaul (`LibraryScreen.kt`)
1. **Floating Action Button for Import**:
   - Move "Import files" from the permanent top header button into an `ExtendedFloatingActionButton` (or top bar action), freeing up crucial vertical real estate for media browsing.
2. **View Modes: Grid vs List**:
   - Add a toggle in the top bar to switch between a visual media Grid (ideal for images and video thumbnails) and a detailed List view (ideal for audio recordings and text documents with file size, duration, date).
3. **Sorting & Filtering Suite**:
   - Add a Sort menu: Newest First, Oldest First, Title (A-Z), Size (Largest).
   - Add a "Favorites" filter chip alongside the media kind chips.
4. **Modern Media Details ModalBottomSheet**:
   - Replace `MediaDetailDialog` with a full-featured `ModalBottomSheet` featuring an image/video thumbnail hero banner, audio playback trigger, metadata pill tags, and fluid inline editing.

---

## 5. Verification Method

### 5.1 Compilation & Build Integrity
Run from project root:
```bash
./gradlew assembleDebug
```
- **Success Criteria**: Exit code 0, APK generated at `app/build/intermediates/apk_ide_redirect_file/debug/createDebugApkListingFileRedirect`.

### 5.2 Unit Tests
Run:
```bash
./gradlew test
```
- **Success Criteria**: Passes `CharacterCardTest`, `LabModelTest`, and `AiResponseParsingTest`.

### 5.3 UX & Verification Checklist
- [ ] No double-padding inset issues or status bar clipping.
- [ ] Tab switching has fluid animated transitions.
- [ ] Discover screen has forward navigation and full-width URL input.
- [ ] Scenario Lab "Generate" action is immediately visible without scrolling.
- [ ] Scenario brief has a clutter-free Reading View.
- [ ] Library has a Floating Action Button and sorting/favorites filters.
- [ ] All dialogs adapt gracefully when software keyboard opens.
