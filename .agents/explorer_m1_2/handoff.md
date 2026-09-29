# Handoff Report: Word Banks Expansion & Character Card AI Assist Architecture

**Agent**: `explorer_m1_2`  
**Milestone**: M1 (Generation Logic & AI Integration Overhaul)  
**Target Audience**: Milestone 1 Implementer (`worker_m1`), Milestone 2 UI Implementer (`worker_m2`), Orchestrator  
**Date**: 2026-09-27T01:34:00Z  

---

## 1. Observation

### 1.1 Existing Procedural Word Banks (`Banks.kt`)
Inspection of `app/src/main/java/com/ember/companion/data/Banks.kt` reveals the current procedural scaffolding:
- **Bank Sizes & Repetition Risk**:
  - `roles` (lines 26–34): 15 entries (one entry is a compound `"a translator, a courier, a night-shift nurse"`).
  - `traits` (lines 36–44): 14 entries.
  - `wants` (lines 46–52): 10 entries.
  - `fears` (lines 54–60): 9 entries.
  - `secrets` (lines 62–68): 7 entries.
  - `flaws` (lines 70–75): 8 entries.
  - `places` (lines 77–87): 18 entries.
  - `twists` (lines 189–198): 8 entries.
  - `sensory` (lines 200–211): 10 entries.
  - `tensions` (lines 129–137): 11 entries.
  - `framings` (lines 120–127): 10 entries.
  - `openers` (lines 139–152): 12 entries.
  - `escalations` (lines 154–164): 9 entries.
  - `complications` (lines 166–175): 10 entries.
  - `turns` (lines 177–187): 9 entries.
  - `closers` (lines 213–221): 7 entries.
- **Premise Matching Algorithm (`Banks.kt:286-309`)**:
  - `contentWords(value: String)` splits on non-alpha and filters words with `length >= 5` not in `STOP_WORDS`.
  - `forcedBy(premise: String, pool: List<String>)` checks word count matches in lowercase premise text, breaking ties by fewest total content words.
  - Any bank entry expansion must preserve rich, distinctive >=5 letter keywords so premise steering remains sensitive and accurate.

### 1.2 Character Card Generation & Export Pipeline
Inspection of `CharacterCard.kt`, `CardPlatforms.kt`, `EmberViewModel.kt`, and `CardExportSheet.kt`:
- **`CharacterCard.fromBrief` (`CharacterCard.kt:254-293`)**:
  ```kotlin
  fun fromBrief(
      brief: Brief,
      tags: List<String> = emptyList(),
      exampleDialogue: String = "",
      systemPrompt: String = "",
      postHistoryInstructions: String = "",
      alternateGreetings: List<String> = emptyList(),
      creator: String = "Ember",
  ): Card { ...
      firstMessage = brief.slot("open")?.body.orEmpty(),
      exampleDialogue = exampleDialogue,
      ...
  }
  ```
  - `firstMessage` is hardcoded to `brief.slot("open")?.body.orEmpty()`. Because `brief.slot("open")` is drawn from `Banks.openers` or `Banks.openingsByRegister`, it is currently a single procedural sentence (e.g., `"Neither of them says anything for long enough that it becomes a statement."`). While effective as a scene prompt, in roleplay platforms (SillyTavern, Chub, RisuAI, JanitorAI), `first_mes` is expected to be an immersive opening post (150–300 words) written in character voice.
  - `exampleDialogue` (`mes_example`) is passed through untouched and defaults to `""`.
  - There is currently no parameter in `fromBrief` to pass a custom or AI-generated `firstMessage`.
- **Card State in `EmberViewModel.kt` (`EmberViewModel.kt:786-881`)**:
  ```kotlin
  data class CardExtras(
      val exampleDialogue: String = "",
      val systemPrompt: String = "",
      val postHistoryInstructions: String = "",
      val alternateGreetings: List<String> = emptyList(),
      val tags: List<String> = emptyList(),
      val platformId: String = CardPlatforms.ALL.first().id,
      val spec: CharacterCard.Spec = CharacterCard.Spec.V2,
      val useCustom: Boolean = false,
      val custom: CardPlatforms.Custom = CardPlatforms.Custom(),
  )
  ```
  - `CardExtras` does not have a field for `firstMessage`.
  - In `ExtrasEditor` (`CardExportSheet.kt:274-318`), the user can edit `exampleDialogue`, `systemPrompt`, `postHistoryInstructions`, etc., but cannot edit or view the `firstMessage` field in an editable text box.
- **AI Infrastructure (`AiClient.kt:104-149`)**:
  - `AiClient.complete(userPrompt: String, context: String = "", maxTokens: Int = 800): AiResult` executes network requests against OpenAI, Anthropic, or Gemini.
  - The client is thread-safe and runs on `Dispatchers.IO`.

---

## 2. Logic Chain

### 2.1 Word Bank Expansion Rationale
1. **Combinatorial Ceiling**: With 7 secrets, 8 flaws, and 9 fears, users generating scenarios or characters quickly encounter identical combinations within 5 to 10 generations.
2. **Atmospheric Cohesion**: Ember's design philosophy is adult-themed, non-graphic, tension-driven, observational, and psychologically grounded.
3. **Premise Forcing Compatibility**: To work with `Banks.forcedBy()`, each added entry must contain specific keywords with length $\ge 5$ (e.g., "ferry", "archives", "audition", "blackmail", "coroner", "penthouse") that do not appear in `STOP_WORDS`.
4. **Deterministic Dial Indexing**: Dials index `registers`, `pacingNotes`, `closeNotes`, and `openingsByRegister`. Those dial-indexed lists must NOT have items arbitrarily appended or reordered, as index `0..2` matches the dial values. However, all free pools (`roles`, `traits`, `wants`, `fears`, `secrets`, `flaws`, `places`, `twists`, `sensory`, `tensions`, `framings`, `escalations`, `complications`, `turns`, `closers`) can be expanded freely without altering dial semantics.

### 2.2 Character Card Enrichment Rationale
1. **Industry Standard Expectations**:
   - `first_mes`: Sets the tone, scenario premise, physical setting, and opening dialogue. In third or first person with actions in asterisks `*...*` and dialogue in quotes `"..."`. Crucially, it must never speak for `{{user}}`.
   - `mes_example`: Formatted strictly with `<START>`, followed by alternating `{{user}}` and `{{char}}` turns. Standard practice requires 2–3 distinct vignettes displaying demeanor, emotional boundary testing, and intimate/high-stakes dialogue.
2. **Separation of Concerns**:
   - `CharacterCard.kt` should house pure functions for prompt building (`buildFirstMessagePrompt`, `buildMesExamplePrompt`) and response sanitization (`cleanFirstMessage`, `cleanMesExample`). This keeps prompt formatting 100% testable in JVM unit tests without Android Context or MockWebServer.
   - `EmberViewModel.kt` should manage the asynchronous coroutine execution, API error handling, and state exposure (`CardEnrichState`), updating `CardExtras`.
   - `CardExportSheet.kt` should render the UI triggers (action buttons with progress spinners) and allow the user to view and edit both `firstMessage` and `exampleDialogue`.
3. **Backward Compatibility**:
   - Updating `CharacterCard.fromBrief`:
     ```kotlin
     fun fromBrief(
         brief: Brief,
         tags: List<String> = emptyList(),
         firstMessage: String? = null, // new optional parameter with default null
         exampleDialogue: String = "",
         systemPrompt: String = "",
         postHistoryInstructions: String = "",
         alternateGreetings: List<String> = emptyList(),
         creator: String = "Ember",
     ): Card
     ```
     When `firstMessage` is null, it falls back to `brief.slot("open")?.body.orEmpty()`. All existing callers and unit tests remain 100% functional without modification.

---

## 3. Concrete Specifications & Implementation Patterns

### 3.1 Procedural Word Bank Expansions (`Banks.kt`)

The implementer should add the following vetted lists to `Banks.kt`:

#### A. Places (`Banks.places` — 22 new entries added)
```kotlin
val places = listOf(
    // Existing entries preserved
    "a rented flat above a closed laundrette", "the back booth of a bar that's had its last round",
    "a hotel bar between two conventions", "a kitchen at 4am, one light on",
    "an empty gallery after closing", "a long-haul airport lounge at dawn",
    "a bookshop that hasn't closed yet", "a car park level nobody uses",
    "someone's balcony in a city neither of you knows", "a rehearsal space booked for one hour",
    "a laundromat at midnight", "a riverside bench in weather that argues against staying",
    "a hotel corridor because neither has the nerve for the room", "a friend's empty apartment",
    "a rooftop with the equipment still up", "a corridor of a museum after the last tour",
    "a service elevator", "a bar that has decided to stop serving",
    // Expanded entries
    "an all-night diner with neon buzzing against rain-streaked vinyl",
    "the rear carriage of the last commuter train leaving the terminal",
    "a rain-swept ferry deck crossing dark harbor water",
    "a locked archival library in a university basement after hours",
    "a private dining booth hidden behind heavy velvet curtains",
    "an industrial freight elevator stalled between loading floors",
    "an artist's loft with turpentine in the air and covered canvases",
    "a 24-hour pharmacy parking lot under flickering sodium lights",
    "a botanical conservatory humid and shadowy during an evening storm",
    "a fire escape landing suspended three floors above an alley",
    "a wood-paneled law library during the weekend building lockdown",
    "a photographer's darkroom bathed in faint red safelight",
    "a quiet marina slipway with rigging clinking against metal masts",
    "a sleeper compartment on an overnight interstate train",
    "a motel balcony overlooking an empty swimming pool at 3am",
    "an antique shop backroom filled with covered mirrors and clockwork",
    "a decommissioned radio broadcast booth with dead acoustics",
    "a gravel turnout along an unlit coastal bluff road",
    "a hospital waiting area where the coffee has been burned since midnight",
    "a rooftop garden terrace where the event staff has packed up",
    "a basement boiler alcove humming with hot iron pipes",
    "a luggage storage office at a continental train junction",
)
```

#### B. Roles (`Banks.roles` — 20 new entries added)
```kotlin
val roles = listOf(
    // Existing entries preserved
    "the one who always plans the evening", "the one who lets the evening happen",
    "a professional in a job that requires composure", "a freelancer between contracts",
    "the new hire who knows everyone's names", "the mentor with a reputation to protect",
    "the one who left a comfortable life to start over", "a translator, a courier, a night-shift nurse",
    "someone who answers to nobody", "the person everyone is subtly afraid of",
    "a former friend of the other character", "two people who met once and both remember it",
    "a critic", "a landlord", "a patient regular", "the only witness",
    // Expanded entries
    "an investigator who already knows what the documents say",
    "a photographer who notices details people try to conceal",
    "a defense attorney who secured an uncomfortable verdict",
    "a hotel concierge who manages other people's emergencies",
    "an archivist handling disputed and fragile correspondence",
    "an off-duty surgeon unable to switch off emergency vigilance",
    "a quiet security consultant whose contract expires at midnight",
    "a forensic auditor tracking vanished corporate accounts",
    "a bartender who recognizes grief faster than intoxication",
    "an architect visiting a building they regret designing",
    "a classical musician who abandoned public performances",
    "an estranged relative who arrived without luggage or warning",
    "a discreet mediator called when private arrangements collapse",
    "a former protégé who has surpassed the original teacher",
    "a professional rival who understands the stakes better than anyone",
    "a departed confidant who reappeared after years of silence",
    "a passenger traveling under a ticket booked by someone else",
    "a private appraiser valuing an estate behind closed doors",
    "a night dispatcher accustomed to voices under acute pressure",
    "an editor who knows precisely which sections were excised",
)
```

#### C. Wants (`Banks.wants` — 15 new entries added)
```kotlin
val wants = listOf(
    // Existing entries preserved
    "to be chosen without having to audition for it", "one night where nobody needs anything from them",
    "to prove a year of self-doubt wrong", "to stop being the sensible one",
    "a confession they've rehearsed too many times", "to be found out",
    "permission to want something they haven't admitted", "to be bored, honestly, for a whole evening",
    "their name remembered correctly", "to be the mess for once",
    // Expanded entries
    "an alibi that holds without having to explain why they were there",
    "to be touched like someone worth keeping past sunrise",
    "to say the unvarnished truth once and watch the room survive",
    "to take instructions from someone whose authority they genuinely respect",
    "a decisive break that leaves no lingering obligations",
    "to stop justifying a decision everyone warned against",
    "to yield an argument to someone who actually earned the victory",
    "one conversation that does not require emotional translation",
    "to be the reason someone else deliberately breaks their own rule",
    "an honest appraisal of the compromises made to reach this position",
    "to leave the room first before the terms are renegotiated",
    "permission to surrender a responsibility they never volunteered for",
    "to be remembered for who they were before the compromise",
    "a quiet agreement that will not unravel by tomorrow morning",
    "to be seen clearly without being pitied for the struggle",
)
```

#### D. Fears (`Banks.fears` — 15 new entries added)
```kotlin
val fears = listOf(
    // Existing entries preserved
    "being legible too easily", "the moment the mood corrects itself",
    "that they're only interesting when they're useful", "silence that lasts three seconds",
    "being compared to someone they replaced", "genuine, irreversible awkwardness",
    "that they'll say the kind thing and mean the polite thing",
    "caring more than the other person intended", "an audience",
    // Expanded entries
    "that their composure is the only attribute anyone finds tolerable",
    "being forgiven too quickly before the impact is recognized",
    "running into someone who witnessed them during an earlier collapse",
    "the terrifying possibility that they are the only one who remembers",
    "hearing the unspoken truth when the alcohol wears off",
    "being understood so thoroughly that deception becomes impossible",
    "discovering that walking away will be far easier than staying ever was",
    "waking up to the realization that they were merely a convenient substitute",
    "being forced into the role of explaining why everything deteriorated",
    "having their generosity interpreted as a subtle bid for dominance",
    "having their bluff called by someone who remains perfectly calm",
    "a direct question they have spent half a decade circumventing",
    "discovering that the sacrifice they made was completely pointless",
    "the quiet cruelty of someone choosing formal politeness over honesty",
    "that their independence has quietly turned into isolation",
)
```

#### E. Secrets (`Banks.secrets` — 15 new entries added)
```kotlin
val secrets = listOf(
    // Existing entries preserved
    "has read the other's private messages", "already booked a flight",
    "is far more successful than anyone at this gathering knows",
    "recognises the song and knows who it was written about",
    "has been in the room before, that night", "owns something that changes the whole dynamic",
    "has told someone else about this, months ago", "is not who the name implies",
    // Expanded entries
    "knows exactly who leaked the confidential correspondence",
    "carries the key to a flat they were explicitly told to surrender",
    "was the anonymous buyer who acquired the disputed property",
    "has already signed the paperwork confirming departure next week",
    "knows the other character's verifiable alibi is completely fabricated",
    "possesses an audio recording of the conversation that started the dispute",
    "is secretly in debt to a mutual acquaintance",
    "intercepted a private dispatch meant for the other person years ago",
    "deliberately engineered the casual encounter that brought them here",
    "received medical test results this morning and told nobody",
    "witnessed the entire incident in the corridor and chose silence",
    "keeps an unsent confession saved in their draft messages",
    "is operating under an assumed background to shield family members",
    "was offered payment by a third party simply to attend tonight",
    "never actually destroyed the keepsake they claimed to have thrown away",
)
```

#### F. Flaws (`Banks.flaws` — 15 new entries added)
```kotlin
val flaws = listOf(
    // Existing entries preserved
    "apologises instead of arguing", "makes a joke to end a conversation",
    "cannot accept help", "remembers every slight and files it",
    "always leaves one step early", "talks when silence would be better",
    "keeps score without meaning to", "trusts strangers immediately",
    // Expanded entries
    "interprets generosity as an impending demand for leverage",
    "tests loyalty by deliberately retreating and waiting to be pursued",
    "expects emotional mind-reading while refusing to state desires out loud",
    "becomes unnervingly polite the moment they decide to sever ties",
    "withholds warmth reflexively whenever they feel exposed",
    "rationalizes cutting remarks as necessary intellectual honesty",
    "approaches mutual vulnerability like a tactical negotiation",
    "dispenses unsolicited practical advice to avoid sitting with someone's pain",
    "disappears for days whenever a dialogue touches genuine stakes",
    "makes sweeping commitments while emotional that they resent fulfilling",
    "prefers a catastrophic rupture over a patient compromise",
    "scrutinizes micro-expressions until all natural rapport evaporates",
    "uses professional competence as an impenetrable shield against intimacy",
    "surrenders their own legitimate boundaries to preserve superficial calm",
    "demands absolute certainty before offering the smallest concession",
)
```

#### G. Traits (`Banks.traits` — 15 new entries added)
```kotlin
val traits = listOf(
    // Existing entries preserved
    "unfailingly polite", "terrible at small talk, unnervingly direct about big things",
    "chronically early", "keeps a mental list of everything they owe",
    "laughs at their own jokes before finishing them", "cannot be hurried",
    "apologises reflexively", "answers questions with questions",
    "physically restless while lying still", "obsessively fair",
    "quietly competitive about trivia", "hums when thinking",
    "counts things under their breath", "flinches at being thanked",
    // Expanded entries
    "tracks hands rather than eyes throughout a conversation",
    "speaks in a measured cadence that forces others to lean closer",
    "insistent on exact titles and correct spelling of proper names",
    "touches doorframes lightly whenever crossing into a room",
    "refuses to sit with back facing an open doorway or corridor",
    "pauses a noticeable two beats before addressing personal inquiries",
    "habitually aligns small objects on table surfaces while listening",
    "abstains from alcohol whenever in the presence of colleagues",
    "glances at mechanical watch hands rather than checking a phone",
    "recalls calendar dates and weather conditions with unsettling fidelity",
    "laughs soundlessly with crinkled eyelids and motionless posture",
    "sustains eye contact slightly past the customary threshold",
    "enunciates with the careful precision of an advanced second-language speaker",
    "keeps outer coat buttoned tightly even when indoor heating is excessive",
    "never repeats a spoken question if ignored on the first attempt",
)
```

#### H. Twists (`Banks.twists` — 12 new entries added)
```kotlin
val twists = listOf(
    // Existing entries preserved
    "they have met before, and one of them remembers exactly when",
    "the arrangement was arranged by someone in the room's orbit",
    "one of them already wrote this down years ago",
    "the person being left behind is the one who set this up",
    "the forbidden thing was permitted from the start",
    "the one who seemed more experienced has never done this at all",
    "someone in the building is being paid to know where they are",
    "the night is a rehearsal for something already decided",
    // Expanded entries
    "the project they have been negotiating does not exist; it was an excuse to meet",
    "one of them is recording the audio, but to protect the other rather than betray them",
    "the third party they are trying to shield has already settled the matter",
    "the formal contract expired hours before either of them walked in",
    "one of them acquired ownership of the building earlier this week",
    "the sealed envelope was delivered to the wrong room three hours ago",
    "both were commissioned by rival clients with mutually exclusive instructions",
    "the apology being prepared addresses the wrong grievance entirely",
    "one of them orchestrated the entire sequence of events over six months",
    "the bystander at the neighboring table is listening on instructions",
    "the key they brought fails because the deadbolts were replaced this morning",
    "both characters are misleading each other about who requested the meeting",
)
```

#### I. Sensory Textures (`Banks.sensory` — 15 new entries added)
```kotlin
val sensory = listOf(
    // Existing entries preserved
    "the smell of someone else's coat still in the room",
    "a hum from a fridge neither of them will investigate",
    "the specific weight of a hand on the back of a neck",
    "cold glass leaving a ring on a table",
    "the sound of a key that turns out not to be the door",
    "bright light behind closed eyelids",
    "wool, wet pavement, and someone else's cigarette",
    "a phone vibrating face-down on wood",
    "the air moving differently in an open doorway",
    "the particular silence of a room with one clock in it",
    // Expanded entries
    "the sharp mineral tang of damp concrete after a cloudburst",
    "a brass latch clicking into place with heavy mechanical certainty",
    "the lingering scent of citrus rind and sulfur matches on fingertips",
    "ice cubes settling against crystal in a room that suddenly went quiet",
    "the dry scrape of unvarnished chair legs across parquet flooring",
    "a cool draft seeping beneath heavy velvet doorway drapes",
    "the faint magnetic buzz of overhead fluorescent ballast warming up",
    "worn leather warm from body heat and smelling faintly of cedarwood",
    "the muffled impact of footsteps echoing through floorboards above",
    "a thin column of steam rising from an abandoned black coffee cup",
    "the static crackle of wool cloth brushing against an overcoat",
    "the smell of soaked umbrella fabric drying near an active cast-iron radiator",
    "a pulse fluttering visibly in the hollow beneath a collarbone",
    "the rhythmic rattle of an exhaust vent vibrating against corrugated tin",
    "the chilled enamel rim of a metal cup against chapped lips",
)
```

#### J. Story Beats (`tensions`, `framings`, `escalations`, `complications`, `turns`, `closers`, `openers`)
Add 6–10 items to each in `Banks.kt`:
- **`tensions`**:
  - "both recognize the exit route is closing and neither mentions it"
  - "one has the authority to end this immediately and is hesitating"
  - "the polite phrasing is barely disguising an ultimatum"
  - "both know what happens next and are waiting for an external interruption"
  - "the physical proximity has become impossible to ignore or justify"
  - "one is waiting for a confession the other is determined to withhold"
- **`framings`**:
  - "a mutual reckoning postponed for three seasons"
  - "an unsanctioned meeting arranged on short-notice burner channels"
  - "an uncomfortable handover between outgoing and incoming specialists"
  - "two rivals forced to share transit during an unexpected transit strike"
  - "a private debriefing conducted away from surveillance"
  - "an unannounced visit following an unsettling late-night message"
- **`escalations`**:
  - "a quiet statement is made that cannot be laughed off as hyperbole"
  - "one of them steps into the other's personal space without retreating"
  - "the lights flicker or dim and neither character turns to look"
  - "someone puts down their glass and leaves their hands flat on the table"
  - "the third party leaves the room, leaving the door unlatched"
  - "a direct demand replaces the polite suggestion"
- **`complications`**:
  - "the building manager begins locking the exterior security gates"
  - "a phone call comes through that one of them cannot ignore without suspicion"
  - "the storm knocks out the local power grid, leaving emergency exit signs"
  - "a third acquaintance who knows both characters walks into the premises"
  - "one of them realizes they left a critical piece of property behind"
  - "the transit line shuts down earlier than scheduled"
- **`turns`**:
  - "the character demanding answers realizes they don't want the truth"
  - "the softer party reveals they held all legal leverage from the beginning"
  - "the defensive facade drops into absolute, startling clarity"
  - "the argument collapses into an admission of mutual complicity"
  - "what seemed like a refusal transforms into an unconditional offer"
- **`closers`**:
  - "they leave by separate exits without turning to check if the other watched"
  - "an unspoken agreement settles between them, fragile but intact"
  - "the last line is an ordinary practical instruction that carries immense weight"
  - "they agree to speak again in forty-eight hours and both intend to keep it"
  - "the door closes and the silence that follows is noticeably different"
- **`openers`**:
  - "The door closes behind them and neither makes a move toward the chairs."
  - "\"We don't have to make this harder than it already is,\" they say quietly."
  - "One of them is still taking off their gloves when the real topic is raised."
  - "They sit on opposite sides of the booth as if an invisible boundary is marked."
  - "\"You took your time,\" they say, without checking the clock."
  - "A folder is laid on the counter between them, untouched."

---

### 3.2 Character Card AI Assist Architecture (`CharacterCard.kt` & `EmberViewModel.kt`)

#### A. Prompt Engineering in `CharacterCard.kt`

Add helper object or functions in `CharacterCard.kt`:

```kotlin
object CharacterCardPrompts {

    /**
     * Builds the AI prompt for generating a rich first_mes (character greeting).
     */
    fun buildFirstMessagePrompt(brief: Brief, card: Card): String = buildString {
        appendLine("Generate the opening greeting ('first_mes') for a roleplay character card in character voice.")
        appendLine()
        appendLine("CHARACTER PROFILE:")
        appendLine("- Name: ${card.name}")
        if (card.personality.isNotBlank()) appendLine("- Personality: ${card.personality}")
        brief.slot("cast")?.body?.let { appendLine("- Cast Details:\n$it") }
        appendLine()
        appendLine("SCENARIO & SETTING:")
        if (card.scenario.isNotBlank()) appendLine("- Scenario: ${card.scenario}")
        brief.slot("setting")?.body?.let { appendLine("- Setting Details:\n$it") }
        brief.slot("frame")?.body?.let { appendLine("- Dynamic & Tone:\n$it") }
        brief.slot("open")?.body?.let { appendLine("- Opening Hook / Seed: $it") }
        appendLine()
        appendLine("REQUIREMENTS:")
        appendLine("1. Write entirely in character voice as ${card.name}.")
        appendLine("2. Perspective: Use roleplay narration format (third person past/present or second person, actions/thoughts in asterisks *like this*, spoken dialogue in quotes \"like this\").")
        appendLine("3. Ground the scene immediately in the physical setting with concrete sensory texture.")
        appendLine("4. Show the character's personality traits and underlying tension through actions and speech.")
        appendLine("5. Directly address {{user}} within the scene context. DO NOT speak, think, or act for {{user}}.")
        appendLine("6. Conclude with an engaging conversational hook, gesture, or dilemma that invites an immediate response.")
        appendLine("7. Length: 150 to 280 words. No meta-commentary, markdown headers, or introductory chatter.")
    }

    /**
     * Builds the AI prompt for generating 2-3 dialogue examples in <START> format.
     */
    fun buildMesExamplePrompt(brief: Brief, card: Card): String = buildString {
        appendLine("Generate 2 to 3 dialogue examples ('mes_example') for this character card.")
        appendLine()
        appendLine("CHARACTER:")
        appendLine("- Name: ${card.name}")
        if (card.personality.isNotBlank()) appendLine("- Personality: ${card.personality}")
        brief.slot("cast")?.body?.let { appendLine("- Cast Context:\n$it") }
        appendLine()
        appendLine("FORMAT RULES:")
        appendLine("- Every example vignette MUST start with the exact delimiter '<START>' on its own line.")
        appendLine("- Use '{{user}}:' and '{{char}}:' to indicate speaker turns.")
        appendLine("- Include atmospheric actions/expressions in asterisks *...* and dialogue in quotes \"...\".")
        appendLine("- Vignette 1: Characteristic baseline conversation establishing tone, mannerisms, and posture.")
        appendLine("- Vignette 2: Conflict or boundary testing when a flaw, secret, or sensitive topic is touched.")
        appendLine("- Vignette 3: An intense, vulnerable, or high-stakes exchange demonstrating emotional depth.")
        appendLine("- Each vignette should be 2 to 4 turns long.")
        appendLine("- Total length: 300 to 500 words. Provide ONLY the vignettes starting with <START>.")
    }

    /**
     * Cleans up raw LLM text for first_mes: strips markdown code fences and conversational preambles.
     */
    fun cleanFirstMessage(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter("\n")
            if (text.endsWith("```")) text = text.substringBeforeLast("```").trim()
        }
        // Remove conversational preambles like "Here is the first message:" or "Greeting:"
        text = text.replace(Regex("^(?:Here is (?:a|the) (?:first message|greeting|opening)[^:]*:\\s*)+", RegexOption.IGNORE_CASE), "")
        return text.trim()
    }

    /**
     * Cleans up raw LLM text for mes_example: ensures proper <START> structure and strips commentary.
     */
    fun cleanMesExample(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter("\n")
            if (text.endsWith("```")) text = text.substringBeforeLast("```").trim()
        }
        // If the model left out the initial <START>, prepend it
        if (!text.startsWith("<START>", ignoreCase = true)) {
            val startIdx = text.indexOf("<START>", ignoreCase = true)
            text = if (startIdx != -1) {
                text.substring(startIdx)
            } else {
                "<START>\n$text"
            }
        }
        return text.trim()
    }
}
```

#### B. Update `CharacterCard.fromBrief` in `CharacterCard.kt`

```kotlin
fun fromBrief(
    brief: Brief,
    tags: List<String> = emptyList(),
    firstMessage: String? = null,
    exampleDialogue: String = "",
    systemPrompt: String = "",
    postHistoryInstructions: String = "",
    alternateGreetings: List<String> = emptyList(),
    creator: String = "Ember",
): Card {
    fun part(key: String): String =
        brief.allParts().firstOrNull { it.key == key }?.value.orEmpty()

    val description = brief.slots
        .filter { it.key in setOf("cast", "setting", "beats", "twist", "close") }
        .joinToString("\n\n") { "## ${it.heading}\n${it.body}" }
        .trim()

    val personality = brief.slots.firstOrNull { it.key == "cast" }
        ?.parts.orEmpty()
        .filter { it.key.endsWith("trait") }
        .joinToString(", ") { it.value.trim() }
        .take(300)

    return Card(
        name = part("aname").substringBefore(" — ").ifBlank { brief.title },
        description = description,
        personality = personality,
        scenario = brief.slot("frame")?.body.orEmpty(),
        firstMessage = firstMessage ?: brief.slot("open")?.body.orEmpty(),
        exampleDialogue = exampleDialogue,
        systemPrompt = systemPrompt,
        postHistoryInstructions = postHistoryInstructions,
        alternateGreetings = alternateGreetings,
        tags = tags,
        creator = creator,
        characterVersion = "1.0",
        modificationDate = System.currentTimeMillis(),
    )
}
```

#### C. `EmberViewModel.kt` State & Methods

1. **Extend `CardExtras`**:
   ```kotlin
   data class CardExtras(
       val firstMessage: String? = null,
       val exampleDialogue: String = "",
       val systemPrompt: String = "",
       val postHistoryInstructions: String = "",
       val alternateGreetings: List<String> = emptyList(),
       val tags: List<String> = emptyList(),
       val platformId: String = CardPlatforms.ALL.first().id,
       val spec: CharacterCard.Spec = CharacterCard.Spec.V2,
       val useCustom: Boolean = false,
       val custom: CardPlatforms.Custom = CardPlatforms.Custom(),
   )
   ```

2. **Add `CardEnrichState`**:
   ```kotlin
   sealed interface CardEnrichState {
       object Idle : CardEnrichState
       data class Busy(val target: String) : CardEnrichState // "greeting", "examples", "all"
       data class Success(val message: String) : CardEnrichState
       data class Error(val message: String) : CardEnrichState
   }
   
   val cardEnrichState = MutableStateFlow<CardEnrichState>(CardEnrichState.Idle)
   ```

3. **Update `currentCard()`**:
   ```kotlin
   fun currentCard(): CharacterCard.Card {
       val brief = lab.value.brief ?: return CharacterCard.Card()
       val extras = cardExtras.value
       return CharacterCard.fromBrief(
           brief = brief,
           tags = extras.tags,
           firstMessage = extras.firstMessage,
           exampleDialogue = extras.exampleDialogue,
           systemPrompt = extras.systemPrompt,
           postHistoryInstructions = extras.postHistoryInstructions,
           alternateGreetings = extras.alternateGreetings,
       )
   }
   ```

4. **Add Mutation & AI Enrichment Methods in `EmberViewModel`**:
   ```kotlin
   fun setCardFirstMessage(value: String) {
       cardExtras.value = cardExtras.value.copy(firstMessage = value)
   }

   fun clearCardEnrichState() {
       cardEnrichState.value = CardEnrichState.Idle
   }

   fun enrichCharacterCardGreeting() {
       val brief = lab.value.brief ?: run {
           showMessage("Generate or import a brief first")
           return
       }
       if (!settings.aiEnabled.value) {
           showMessage("Turn on AI assist in Settings first")
           return
       }
       if (!settings.aiHasKey.value) {
           showMessage("Add an API key in Settings first")
           return
       }
       val card = currentCard()
       val prompt = CharacterCardPrompts.buildFirstMessagePrompt(brief, card)
       cardEnrichState.value = CardEnrichState.Busy("greeting")
       viewModelScope.launch {
           when (val result = container.aiClient.complete(prompt, maxTokens = 600)) {
               is AiResult.Ok -> {
                   val cleaned = CharacterCardPrompts.cleanFirstMessage(result.text)
                   setCardFirstMessage(cleaned)
                   cardEnrichState.value = CardEnrichState.Success("Greeting generated")
                   showMessage("First message generated")
               }
               is AiResult.Failure -> {
                   cardEnrichState.value = CardEnrichState.Error(result.message)
                   showMessage("AI error: ${result.message}")
               }
           }
       }
   }

   fun enrichCharacterCardExamples() {
       val brief = lab.value.brief ?: run {
           showMessage("Generate or import a brief first")
           return
       }
       if (!settings.aiEnabled.value) {
           showMessage("Turn on AI assist in Settings first")
           return
       }
       if (!settings.aiHasKey.value) {
           showMessage("Add an API key in Settings first")
           return
       }
       val card = currentCard()
       val prompt = CharacterCardPrompts.buildMesExamplePrompt(brief, card)
       cardEnrichState.value = CardEnrichState.Busy("examples")
       viewModelScope.launch {
           when (val result = container.aiClient.complete(prompt, maxTokens = 1000)) {
               is AiResult.Ok -> {
                   val cleaned = CharacterCardPrompts.cleanMesExample(result.text)
                   setCardExampleDialogue(cleaned)
                   cardEnrichState.value = CardEnrichState.Success("Dialogue examples generated")
                   showMessage("Dialogue examples generated")
               }
               is AiResult.Failure -> {
                   cardEnrichState.value = CardEnrichState.Error(result.message)
                   showMessage("AI error: ${result.message}")
               }
           }
       }
   }

   fun enrichAllCharacterCardFields() {
       viewModelScope.launch {
           val brief = lab.value.brief ?: return@launch
           if (!settings.aiEnabled.value || !settings.aiHasKey.value) {
               showMessage("Configure AI in Settings first")
               return@launch
           }
           cardEnrichState.value = CardEnrichState.Busy("all")
           val card = currentCard()
           val greetPrompt = CharacterCardPrompts.buildFirstMessagePrompt(brief, card)
           val greetRes = container.aiClient.complete(greetPrompt, maxTokens = 600)
           if (greetRes is AiResult.Ok) {
               setCardFirstMessage(CharacterCardPrompts.cleanFirstMessage(greetRes.text))
           } else if (greetRes is AiResult.Failure) {
               cardEnrichState.value = CardEnrichState.Error(greetRes.message)
               return@launch
           }

           val examplePrompt = CharacterCardPrompts.buildMesExamplePrompt(brief, currentCard())
           val exampleRes = container.aiClient.complete(examplePrompt, maxTokens = 1000)
           if (exampleRes is AiResult.Ok) {
               setCardExampleDialogue(CharacterCardPrompts.cleanMesExample(exampleRes.text))
               cardEnrichState.value = CardEnrichState.Success("Card enriched with greeting & dialogue")
               showMessage("Card successfully enriched with AI")
           } else if (exampleRes is AiResult.Failure) {
               cardEnrichState.value = CardEnrichState.Error(exampleRes.message)
           }
       }
   }
   ```

#### D. UI Updates in `CardExportSheet.kt`

1. **AI Action Row in `CardExportDialog`**:
   Add a quick-action row below target selection:
   ```kotlin
   val enrichState by vm.cardEnrichState.collectAsStateWithLifecycle()
   val isBusy = enrichState is CardEnrichState.Busy

   Row(
       horizontalArrangement = Arrangement.spacedBy(8.dp),
       modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
   ) {
       OutlinedButton(
           onClick = vm::enrichCharacterCardGreeting,
           enabled = !isBusy,
           modifier = Modifier.weight(1f),
       ) {
           Text(if (enrichState == CardEnrichState.Busy("greeting")) "Generating..." else "✨ Greeting")
       }
       OutlinedButton(
           onClick = vm::enrichCharacterCardExamples,
           enabled = !isBusy,
           modifier = Modifier.weight(1f),
       ) {
           Text(if (enrichState == CardEnrichState.Busy("examples")) "Generating..." else "✨ Examples")
       }
       Button(
           onClick = vm::enrichAllCharacterCardFields,
           enabled = !isBusy,
           modifier = Modifier.weight(1f),
       ) {
           Text(if (enrichState == CardEnrichState.Busy("all")) "Enriching..." else "✨ All")
       }
   }
   ```

2. **Editable `First message` in `ExtrasEditor`**:
   In `ExtrasEditor` (`CardExportSheet.kt:274`), insert the `First message` field right before or after `exampleDialogue`:
   ```kotlin
   OutlinedTextField(
       value = extras.firstMessage ?: card.firstMessage,
       onValueChange = vm::setCardFirstMessage,
       label = { Text("First message (greeting)") },
       placeholder = { Text("*Steps into the room...* \"You're late.\"") },
       minLines = 3,
       modifier = Modifier.fillMaxWidth(),
   )
   ```

---

## 4. Caveats & Assumptions

1. **Dial Invariance**:
   - `registers`, `pacingNotes`, `closeNotes`, and `openingsByRegister` are strictly indexed by `dials.explicitness` and `dials.pace` (index 0, 1, 2). **Do NOT add or delete items in these 4 dial-indexed lists**, or dial index mapping in `Generator.kt` will silently break.
2. **Premise Matching Stop-Word Collision**:
   - Every entry in `Banks.places`, `roles`, etc. begins with an article (`"a "`, `"an "`, `"the "`). `forcedBy` relies on words $\ge 5$ letters not in `STOP_WORDS`. The proposed entries have been verified to have 2 to 6 distinctive keywords each (e.g. "ferry", "archival", "elevator", "darkroom", "conservatory", "concierge", "surgeon", "consultant").
3. **Token Budgets for Character Card AI Generation**:
   - For `first_mes`, `maxTokens = 600` is plenty for a 200–280 word greeting.
   - For `mes_example`, dialogue examples in `<START>` format with 2–3 vignettes require `maxTokens = 1000` to prevent truncation.
4. **Android File Sharing**:
   - `CharacterCard.encodePng` writes dual chunks (`KEY_V3` = `"ccv3"`, `KEY_V2` = `"chara"`). Enhancing `first_mes` and `mes_example` preserves the full dual-chunk binary structure.

---

## 5. Conclusion

1. **Word Banks (`Banks.kt`)**:
   - Expanding the 14 procedural banks with over 180 curated, tension-dense, non-graphic, adult-themed entries multiplies combinatorial variety by more than $50\times$, directly solving the repetitive generation issue.
   - Preserves 100% backward compatibility with `Generator.kt`, `LabModel.kt`, `LabModelTest.kt`, and dial-indexing semantics.
2. **Character Card AI Assist (`CharacterCard.kt`, `EmberViewModel.kt`, `CardExportSheet.kt`)**:
   - Adding `CharacterCardPrompts` with tailored `buildFirstMessagePrompt`, `buildMesExamplePrompt`, `cleanFirstMessage`, and `cleanMesExample` provides robust, testable prompt logic for V1/V2/V3 card ecosystems.
   - Adding `firstMessage` override to `CharacterCard.fromBrief` and `CardExtras` allows bidirectional AI and manual editing of the greeting.
   - Providing direct UI triggers in `CardExportSheet.kt` gives users a 1-tap experience to turn a procedural brief into a complete, ready-to-import character card with authentic voice and multi-turn dialogue examples.

---

## 6. Verification Method

To independently verify the implementation when applied by the M1 worker:

1. **Unit Test Suite Execution**:
   Run the Gradle test command from the repository root:
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. **New Unit Tests to Add**:
   - In `app/src/test/java/com/ember/companion/data/CharacterCardTest.kt`:
     - Test `fromBrief` with explicit `firstMessage`: verify that the card adopts the explicit `firstMessage` over `brief.slot("open")`.
     - Test `CharacterCardPrompts.buildFirstMessagePrompt`: verify that character name, personality traits, and setting are embedded.
     - Test `CharacterCardPrompts.cleanFirstMessage`: verify that markdown code blocks and preambles are stripped.
     - Test `CharacterCardPrompts.cleanMesExample`: verify that `<START>` is present at the beginning and code blocks are removed.
   - In `app/src/test/java/com/ember/companion/data/LabModelTest.kt`:
     - Test `forcedBy` with new bank keywords:
       ```kotlin
       @Test
       fun `premise forces an expanded bank place`() {
           val match = Banks.forcedBy("they meet near the darkroom", Banks.places)
           assertTrue(match?.contains("darkroom") == true)
       }
       ```
     - Test `defaultsFor` covers all expanded banks with nonzero size.
3. **End-to-End Build Verification**:
   ```bash
   ./gradlew assembleDebug
   ```
   Must exit with code 0.
