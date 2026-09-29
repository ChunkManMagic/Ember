package com.ember.companion.data

import kotlinx.serialization.Serializable

/**
 * Word banks for Ember's offline scenario engine.
 *
 * Deliberate design note: these banks are a *structural* scaffold — archetypes,
 * places, tensions, beats, sensory texture. They are adult-themed and
 * non-judgmental but stay non-graphic. The banks give you a premise and a
 * skeleton to write against; the prose is yours. Users can append their own
 * entries to any bank, which is merged in at generation time. Users can append their own
 * entries to any bank, which is merged in at generation time.
 */
object Banks {

    val nameStyles = listOf(
        "a first name and surname that don't rhyme", "a nickname earned in adulthood",
        "a title before the name, like a professional handle", "a single name, no surname",
        "a surname that carries the family's reputation", "a name borrowed from a second language",
        "a name they'd hate you for making public", "a deliberately forgettable name",
    )

    val ages = listOf(
        "mid-twenties", "early thirties", "late thirties", "early forties",
        "mid-forties", "late forties", "fifties", "older, and entirely at ease with it",
    )

    val roles = listOf(
        "the one who always plans the evening", "the one who lets the evening happen",
        "a professional in a job that requires composure", "a freelancer between contracts",
        "the new hire who knows everyone's names", "the mentor with a reputation to protect",
        "the one who left a comfortable life to start over", "a translator, a courier, a night-shift nurse",
        "someone who answers to nobody", "the person everyone is subtly afraid of",
        "a former friend of the other character", "two people who met once and both remember it",
        "a critic", "a landlord", "a patient regular", "the only witness",
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

    val traits = listOf(
        "unfailingly polite", "terrible at small talk, unnervingly direct about big things",
        "chronically early", "keeps a mental list of everything they owe",
        "laughs at their own jokes before finishing them", "cannot be hurried",
        "apologises reflexively", "answers questions with questions",
        "physically restless while lying still", "obsessively fair",
        "quietly competitive about trivia", "hums when thinking",
        "counts things under their breath", "flinches at being thanked",
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

    val wants = listOf(
        "to be chosen without having to audition for it", "one night where nobody needs anything from them",
        "to prove a year of self-doubt wrong", "to stop being the sensible one",
        "a confession they've rehearsed too many times", "to be found out",
        "permission to want something they haven't admitted", "to be bored, honestly, for a whole evening",
        "their name remembered correctly", "to be the mess for once",
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

    val fears = listOf(
        "being legible too easily", "the moment the mood corrects itself",
        "that they're only interesting when they're useful", "silence that lasts three seconds",
        "being compared to someone they replaced", "genuine, irreversible awkwardness",
        "that they'll say the kind thing and mean the polite thing",
        "caring more than the other person intended", "an audience",
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
        "that the person they love loves a version of them that was entirely staged",
        "a sudden look of pity from someone whose respect they desperately craved",
    )

    val secrets = listOf(
        "has read the other's private messages", "already booked a flight",
        "is far more successful than anyone at this gathering knows",
        "recognises the song and knows who it was written about",
        "has been in the room before, that night", "owns something that changes the whole dynamic",
        "has told someone else about this, months ago", "is not who the name implies",
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

    val flaws = listOf(
        "apologises instead of arguing", "makes a joke to end a conversation",
        "cannot accept help", "remembers every slight and files it",
        "always leaves one step early", "talks when silence would be better",
        "keeps score without meaning to", "trusts strangers immediately",
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

    val places = listOf(
        "a rented flat above a closed laundrette", "the back booth of a bar that's had its last round",
        "a hotel bar between two conventions", "a kitchen at 4am, one light on",
        "an empty gallery after closing", "a long-haul airport lounge at dawn",
        "a bookshop that hasn't closed yet", "a car park level nobody uses",
        "someone's balcony in a city neither of you knows", "a rehearsal space booked for one hour",
        "a laundromat at midnight", "a riverside bench in weather that argues against staying",
        "a hotel corridor because neither has the nerve for the room", "a friend's empty apartment",
        "a rooftop with the equipment still up", "a corridor of a museum after the last tour",
        "a service elevator", "a bar that has decided to stop serving",
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

    val timesOfDay = listOf(
        "just after midnight", "the hour before dawn", "ten past five in the morning",
        "mid-afternoon gone soft and bright", "the last hour of daylight", "that flat grey three in the afternoon",
        "Sunday morning with nowhere to be", "the dead part of the night when even the bar staff look bored",
        "the hour after everyone's plans cancelled",
    )

    val weather = listOf(
        "rain that started as an apology and settled in", "heat that makes the windows useless",
        "first snow", "a storm nobody expected", "fog thick enough to hide the street",
        "clear cold air", "a wind that will not let a door stay shut",
        "humidity heavy enough to sit in the room", "the specific green light after a storm",
    )

    val atmospheres = listOf(
        "everything slightly too quiet", "a television left on in another room",
        "the air-conditioning cycling", "music low enough to be deniable",
        "a fridge doing more talking than anyone", "sirens, then nothing",
        "one person pretending to look at their phone", "a chair that will not stop scraping",
        "the particular awkwardness of having agreed to do this", "colder than the conversation deserves",
        "smoke from the previous tenant's cigarette", "the smell of wet coats",
    )

    val powerBalances = listOf(
        "even, and neither willing to go first", "one has all the information and the other all the nerve",
        "one is the host and keeps forgetting it", "one has nothing to lose and knows it",
        "one is a guest who has stopped pretending to be polite about it",
        "both pretending the other has the upper hand", "one has seniority and no authority",
        "the balance is the only thing both of them is protecting",
    )

    val framings = listOf(
        "two people who were supposed to be talking about something else",
        "a reunion neither of them asked for", "a favour that cannot be refused cleanly",
        "an apology owed and being avoided", "a professional arrangement with a private edge",
        "a shared secret large enough to require a room", "a dare neither will back down from",
        "a friendship that has stopped pretending to be a friendship",
        "one of them has read too much about the other", "a night that was meant to be a story by morning",
        "a mutual reckoning postponed for three seasons",
        "an unsanctioned meeting arranged on short-notice burner channels",
        "an uncomfortable handover between outgoing and incoming specialists",
        "two rivals forced to share transit during an unexpected transit strike",
        "a private debriefing conducted away from surveillance",
        "an unannounced visit following an unsettling late-night message",
    )

    val tensions = listOf(
        "neither wants to be the first to admit this matters", "the lie is better than the truth and both know it",
        "someone else is expected to arrive", "a decision is being made in real time",
        "one of them has already decided and is pretending not to have",
        "the room is borrowed and the time is not", "being seen clearly is the frightening part",
        "one of them made a promise they intend to break",
        "the age gap is doing more work than either will name",
        "a message has been read and not answered", "the same joke landed differently than intended",
        "both recognize the exit route is closing and neither mentions it",
        "one has the authority to end this immediately and is hesitating",
        "the polite phrasing is barely disguising an ultimatum",
        "both know what happens next and are waiting for an external interruption",
        "the physical proximity has become impossible to ignore or justify",
        "one is waiting for a confession the other is determined to withhold",
    )

    val openers = listOf(
        "Neither of them says anything for long enough that it becomes a statement.",
        "\"You don't have to be polite about this,\" they say, which is not the same as being kind.",
        "There is a third glass on the table and neither of them acknowledges it.",
        "It starts as a joke and the joke outlives its own laughter.",
        "One of them has clearly already decided how the night goes, and is being extremely relaxed about it.",
        "\"Okay,\" they say, to no particular proposal, and it functions as one.",
        "The light goes out in the stairwell and neither reaches for the switch.",
        "They agree on one rule. It becomes the only rule that matters.",
        "\"I should go,\" one of them says, as an opening offer.",
        "A coat is handed over like a small act of violence.",
        "The conversation stalls and one of them leans in to check whether it's over.",
        "\"I'm going to be honest with you,\" they say, and the room gets smaller.",
        "The door closes behind them and neither makes a move toward the chairs.",
        "\"We don't have to make this harder than it already is,\" they say quietly.",
        "One of them is still taking off their gloves when the real topic is raised.",
        "They sit on opposite sides of the booth as if an invisible boundary is marked.",
        "\"You took your time,\" they say, without checking the clock.",
        "A folder is laid on the counter between them, untouched.",
    )

    val escalations = listOf(
        "the professional distance drops and neither replaces it",
        "someone's phone lights up and is turned over deliberately",
        "a second drink becomes a decision rather than a habit",
        "the teasing stops being funny and turns into something else",
        "one of them says the other's name differently, once",
        "the joke turns into a real question and nobody retreats",
        "the power balance visibly shifts and both pretend it hasn't",
        "someone laughs at the wrong moment",
        "a door closes inside the building and neither of them moves",
        "a quiet statement is made that cannot be laughed off as hyperbole",
        "one of them steps into the other's personal space without retreating",
        "the lights flicker or dim and neither character turns to look",
        "someone puts down their glass and leaves their hands flat on the table",
        "the third party leaves the room, leaving the door unlatched",
        "a direct demand replaces the polite suggestion",
    )

    val complications = listOf(
        "someone unexpected is in the building", "the hour is later than it was",
        "a promise made an hour ago is now due", "one of them is out of time for a reason not yet explained",
        "the rain gets worse and going home stops being simple",
        "a message arrives that changes what tonight was for",
        "one of them remembers something they should not know",
        "the place they were supposed to go is already booked",
        "someone they both know is the reason they're both here",
        "one of them has to leave in twenty minutes and hasn't said so",
        "the building manager begins locking the exterior security gates",
        "a phone call comes through that one of them cannot ignore without suspicion",
        "the storm knocks out the local power grid, leaving emergency exit signs",
        "a third acquaintance who knows both characters walks into the premises",
        "one of them realizes they left a critical piece of property behind",
        "the transit line shuts down earlier than scheduled",
    )

    val turns = listOf(
        "the one who seemed in control turns out to be asking permission",
        "the joke reveals it was never a joke",
        "the person who came here to refuse is the one who proposes",
        "the age difference turns out to be the smaller of two gaps",
        "someone says the sentence they came here to say, and it is not the one they planned",
        "the third person never existed",
        "control changes hands cleanly and permanently",
        "the retreat is withdrawn before it starts",
        "the real reason for tonight finally gets said out loud",
        "the character demanding answers realizes they don't want the truth",
        "the softer party reveals they held all legal leverage from the beginning",
        "the defensive facade drops into absolute, startling clarity",
        "the argument collapses into an admission of mutual complicity",
        "what seemed like a refusal transforms into an unconditional offer",
    )

    val twists = listOf(
        "they have met before, and one of them remembers exactly when",
        "the arrangement was arranged by someone in the room's orbit",
        "one of them already wrote this down years ago",
        "the person being left behind is the one who set this up",
        "the forbidden thing was permitted from the start",
        "the one who seemed more experienced has never done this at all",
        "someone in the building is being paid to know where they are",
        "the night is a rehearsal for something already decided",
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

    val sensory = listOf(
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

    val closers = listOf(
        "it ends without either of them naming what it was",
        "one of them asks, plainly, whether this is a one-time thing, and means it",
        "they part in a way that leaves the question unanswered on purpose",
        "someone laughs first and that turns out to be the bravest thing anyone did all night",
        "it holds, quietly, with nothing broken",
        "a mutual agreement is reached without either of them saying the word",
        "the last thing said is logistics, which is its own kind of confession",
        "they leave by separate exits without turning to check if the other watched",
        "an unspoken agreement settles between them, fragile but intact",
        "the last line is an ordinary practical instruction that carries immense weight",
        "they agree to speak again in forty-eight hours and both intend to keep it",
        "the door closes and the silence that follows is noticeably different",
    )

    val confidences = listOf(
        "one", "both", "neither", "one of them, for the first time in years",
    )

    /**
     * Banks the steering dials draw from. These are ordered on purpose: the dial
     * is an index into the list, so position 0 is always the mildest end and the
     * same dial setting always yields the same descriptor. Randomising these would
     * reintroduce exactly the noise the dials exist to remove.
     */
    val registers = listOf(
        "restrained — tension carried by implication, nothing stated outright",
        "charged — want is explicit, the acts are described but not itemised",
        "unfiltered — acts and sensation named plainly, no euphemism",
    )

    val pacingNotes = listOf(
        "slow burn: almost nothing happens for a long time, and that is the point",
        "steady build: each beat raises the stakes by a small, even increment",
        "immediate: the first scene already starts inside the tension",
    )

    val closeNotes = listOf(
        "restrained close: nothing is resolved, and both want it again",
        "charged close: the arrangement is made explicit before they part",
        "unfiltered close: the scene ends at its peak rather than after it",
    )

    val openingsByRegister = listOf(
        // restrained
        listOf(
            "one of them suggests a walk, and the other agrees too quickly",
            "a third seat at the table is offered and quietly declined",
            "the same question is asked twice, hours apart, in different words",
        ),
        // charged
        listOf(
            "a key is handed over without either of them naming what it opens",
            "the pretence of coincidence is abandoned within the first ten minutes",
            "one of them says the quiet part first, and pretends not to have",
        ),
        // unfiltered
        listOf(
            "the pretence ends in the first line, and neither pretends otherwise",
            "it starts as a dare and becomes a negotiation out loud",
            "one of them has clearly been waiting for an excuse, and makes it",
        ),
    )

    /**
     * Filler words that are long enough to pass a length filter but specific to
     * nothing, so a premise mentioning one of them should not force a bank value.
     */
    private val STOP_WORDS = setOf(
        "there", "these", "those", "their", "which", "where", "after", "before",
        "under", "about", "would", "could", "shall", "being", "doing", "going",
        "other", "while", "still", "every", "never", "always", "thing", "things",
        "someone", "something", "anything", "everything", "nothing", "because",
        "inside", "outside", "behind", "between", "through", "without", "little",
        "larger", "smaller", "bigger", "rather", "quite", "enough", "maybe",
    )

    /** Distinctive words in a bank value, used for premise matching. */
    fun contentWords(value: String): List<String> = value.lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.length >= 5 && it !in STOP_WORDS }

    /**
     * The pool value a premise explicitly names, or null when it names none.
     *
     * This is what makes the premise a real constraint on offline generation. It
     * matches on distinctive words rather than a prefix because every value in
     * these banks begins with an article, so a prefix test distinguishes nothing.
     * Most matches wins; ties go to the most specific candidate.
     */
    fun forcedBy(premise: String, pool: List<String>): String? {
        val lower = premise.trim().lowercase()
        if (lower.isEmpty()) return null
        return pool
            .map { candidate -> candidate to contentWords(candidate).count { it in lower } }
            .filter { it.second > 0 }
            .maxWithOrNull(
                compareBy<Pair<String, Int>> { it.second }
                    .thenBy { -contentWords(it.first).size },
            )?.first
    }

    /**
     * Default pool for a bank id. Rerolls need the id's base list to rebuild a
     * candidate set after a pin or block, so this has to stay in sync with the
     * ids used in [brief].
     */
    fun defaultsFor(id: String): List<String> = when (id) {
        "ages" -> ages
        "roles" -> roles
        "traits" -> traits
        "wants" -> wants
        "fears" -> fears
        "secrets" -> secrets
        "flaws" -> flaws
        "places" -> places
        "times" -> timesOfDay
        "weather" -> weather
        "atmosphere" -> atmospheres
        "sensory" -> sensory
        "framings" -> framings
        "powerBalances" -> powerBalances
        "tensions" -> tensions
        "confidences" -> confidences
        "escalations" -> escalations
        "complications" -> complications
        "turns" -> turns
        "twists" -> twists
        "openers" -> openers
        "closers" -> closers
        "nameStyles" -> nameStyles
        "names" -> nameStyles // name is composed from FIRST_NAMES + LAST_NAMES; style templates used
        "registers" -> registers
        "pacingNotes" -> pacingNotes
        "closeNotes" -> closeNotes
        "pov" -> Dials.POV
        "powerDial" -> Dials.POWER
        // Title parts are drawn from the role bank but pinned separately.
        "titleNoun", "titlePlural" -> roles
        // The opener bank is dial-dependent; the neutral list is the fallback.
        else -> emptyList()
    }

/** Per-bank preferences: pinned values are preferred, blocked values never appear. */
@Serializable
data class Taste(
        val pinned: Map<String, String> = emptyMap(),
        val blocked: Set<String> = emptySet(),
    ) {
        /**
         * Applies a bank id's taste. Returns the filtered pool; a pinned value is
         * the only candidate that survives, which is what makes rerolls converge
         * instead of wandering.
         */
        fun apply(id: String, pool: List<String>): List<String> {
            val filtered = pool.filterNot { it in blocked }
            val safe = filtered.ifEmpty { pool }
            val pin = pinned[id] ?: return safe
            return listOfNotNull(safe.firstOrNull { it == pin })
        }

        fun isPinned(id: String, value: String): Boolean = pinned[id] == value
        fun isBlocked(value: String): Boolean = value in blocked
    }

/** The user-extensible banks, keyed by the same ids used in the UI. */
@Serializable
data class Custom(val byBank: Map<String, List<String>> = emptyMap()) {
    fun forBank(id: String, defaults: List<String>): List<String> {
        val extra = byBank[id].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (extra.isEmpty()) return defaults
        val weightedExtra = extra.flatMap { e -> List(50) { e } }
        return defaults + weightedExtra
    }
}
}
