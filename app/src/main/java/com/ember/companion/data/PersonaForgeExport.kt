package com.ember.companion.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * PersonaForge story transfer builder.
 *
 * Keeps Ember 100% compliant with PersonaForge canonical schema
 * (TRANSFER_SCHEMA_VERSION = 1 in ChunkCodeMagic transferSchema.ts and Android StoryTransfer.kt).
 * Scenarios brainstormed or drafted in Ember can be directly imported
 * by PersonaForge on both Android and Web.
 */
object PersonaForgeExport {

    const val TRANSFER_SCHEMA_VERSION = 1
    const val SOURCE_APP = "android"

    data class PersonaForgeData(
        val title: String,
        val mode: String = "ROLEPLAY",
        val characterName: String,
        val personality: String,
        val backstory: String = "",
        val appearance: String = "",
        val clothing: String = "",
        val accessories: String = "",
        val hairStyle: String = "",
        val hairColor: String = "",
        val eyeColor: String = "",
        val storyTone: String = "Dramatic",
        val relationship: String = "Strangers",
        val worldAtmosphere: String = "",
        val worldSetting: String = "",
        val keyLocations: String = "",
        val scenarioConflict: String = "",
        val scenarioStakes: String = "",
        val timePeriod: String = "",
        val factions: String = "",
        val magicOrTechnologyLevel: String = "",
        val incitingIncident: String = "",
        val characterFlaws: String = "",
        val secretMotive: String = "",
        val speechPattern: String = "",
        val likesAndDislikes: String = "",
        val coreBeliefs: String = "",
        val quirks: String = "",
        val greetingMessage: String = "",
        val scenarioInstructions: String = "",
        val suggestedPlayerName: String = "",
        val suggestedPlayerDescription: String = "",
        val playerPersonality: String = "",
        val playerBackstory: String = "",
        val playerAppearance: String = "",
        val tags: List<String> = emptyList(),
    )

    fun toStoryExportJson(data: PersonaForgeData): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", TRANSFER_SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("sourceApp", SOURCE_APP)
        root.put("sourceVersion", "1.0")

        val scenario = JSONObject()
        val scenarioId = UUID.randomUUID().toString()
        scenario.put("id", scenarioId)
        val scenarioTitle = data.title.ifBlank { data.characterName.ifBlank { "Untitled Scenario" } }
        scenario.put("name", scenarioTitle)
        val modeUpper = when (data.mode.trim().uppercase()) {
            "SCENARIO" -> "SCENARIO"
            "GAME" -> "GAME"
            "NARRATIVE" -> "NARRATIVE"
            else -> "ROLEPLAY"
        }
        scenario.put("mode", modeUpper)
        if (data.greetingMessage.isNotBlank()) scenario.put("greetingMessage", data.greetingMessage)
        if (data.scenarioInstructions.isNotBlank()) {
            scenario.put("scenarioInstructions", data.scenarioInstructions)
            scenario.put("customInstructions", data.scenarioInstructions)
        }
        if (data.backstory.isNotBlank()) scenario.put("backstory", data.backstory)
        val playerName = data.suggestedPlayerName.ifBlank { "The Protagonist" }
        scenario.put("playerCharacterName", playerName)
        if (data.suggestedPlayerDescription.isNotBlank()) {
            scenario.put("playerCharacterDescription", data.suggestedPlayerDescription)
        }

        val profile = JSONObject()
        profile.put("mode", modeUpper)
        profile.put("name", data.characterName.ifBlank { scenarioTitle })
        profile.put("personality", data.personality)
        if (data.backstory.isNotBlank()) profile.put("backstory", data.backstory)
        if (data.appearance.isNotBlank()) profile.put("appearance", data.appearance)
        if (data.clothing.isNotBlank()) profile.put("clothing", data.clothing)
        if (data.accessories.isNotBlank()) profile.put("accessories", data.accessories)
        if (data.hairStyle.isNotBlank()) profile.put("hairStyle", data.hairStyle)
        if (data.hairColor.isNotBlank()) profile.put("hairColor", data.hairColor)
        if (data.eyeColor.isNotBlank()) profile.put("eyeColor", data.eyeColor)
        if (data.storyTone.isNotBlank()) profile.put("storyTone", data.storyTone)
        if (data.relationship.isNotBlank()) profile.put("relationship", data.relationship)
        if (data.worldAtmosphere.isNotBlank()) {
            profile.put("worldAtmosphere", data.worldAtmosphere)
            profile.put("worldSetting", data.worldSetting.ifBlank { data.worldAtmosphere })
        } else if (data.worldSetting.isNotBlank()) {
            profile.put("worldSetting", data.worldSetting)
            profile.put("worldAtmosphere", data.worldSetting)
        }
        if (data.keyLocations.isNotBlank()) profile.put("keyLocations", data.keyLocations)
        if (data.scenarioStakes.isNotBlank()) profile.put("scenarioStakes", data.scenarioStakes)
        if (data.scenarioConflict.isNotBlank()) profile.put("scenarioConflict", data.scenarioConflict)
        if (data.timePeriod.isNotBlank()) profile.put("timePeriod", data.timePeriod)
        if (data.factions.isNotBlank()) profile.put("factions", data.factions)
        if (data.magicOrTechnologyLevel.isNotBlank()) profile.put("magicOrTechnologyLevel", data.magicOrTechnologyLevel)
        if (data.incitingIncident.isNotBlank()) profile.put("incitingIncident", data.incitingIncident)
        if (data.characterFlaws.isNotBlank()) {
            profile.put("characterFlaws", data.characterFlaws)
            profile.put("flaws", data.characterFlaws)
        }
        if (data.secretMotive.isNotBlank()) profile.put("secretMotive", data.secretMotive)
        if (data.speechPattern.isNotBlank()) profile.put("speechPattern", data.speechPattern)
        if (data.likesAndDislikes.isNotBlank()) profile.put("likesAndDislikes", data.likesAndDislikes)
        if (data.coreBeliefs.isNotBlank()) profile.put("coreBeliefs", data.coreBeliefs)
        if (data.quirks.isNotBlank()) profile.put("quirks", data.quirks)
        if (data.greetingMessage.isNotBlank()) profile.put("greetingMessage", data.greetingMessage)
        if (data.scenarioInstructions.isNotBlank()) {
            profile.put("scenarioInstructions", data.scenarioInstructions)
            profile.put("customInstructions", data.scenarioInstructions)
        }
        profile.put("suggestedPlayerName", playerName)
        if (data.suggestedPlayerDescription.isNotBlank()) {
            profile.put("suggestedPlayerDescription", data.suggestedPlayerDescription)
        }

        val playerProfile = JSONObject()
        playerProfile.put("name", playerName)
        playerProfile.put("description", data.suggestedPlayerDescription.ifBlank { "The protagonist in this scenario." })
        if (data.playerPersonality.isNotBlank()) playerProfile.put("personality", data.playerPersonality)
        if (data.playerBackstory.isNotBlank()) playerProfile.put("backstory", data.playerBackstory)
        if (data.playerAppearance.isNotBlank()) playerProfile.put("appearance", data.playerAppearance)
        profile.put("playerProfile", playerProfile)

        scenario.put("characterProfile", profile)
        root.put("scenario", scenario)

        val messages = JSONArray()
        if (data.greetingMessage.isNotBlank()) {
            val msg = JSONObject()
            msg.put("id", UUID.randomUUID().toString())
            msg.put("role", "model")
            msg.put("isFromUser", false)
            msg.put("text", data.greetingMessage)
            msg.put("timestamp", System.currentTimeMillis())
            messages.put(msg)
        }
        root.put("messages", messages)
        root.put("codex", JSONArray())
        root.put("inventory", JSONArray())
        root.put("summary", JSONObject.NULL)

        return root
    }

    /**
     * Map a [Brief], character card, and any draft overrides to PersonaForgeData.
     */
    fun fromBrief(
        brief: Brief,
        card: CharacterCard.Card? = null,
        drafts: Map<String, String>? = null,
    ): PersonaForgeData {
        fun part(key: String): String =
            brief.allParts().firstOrNull { it.key == key }?.value.orEmpty().trim()

        /**
         * Reads a part by its canonical key.
         *
         * Keys come from Generator's Part(...) calls, which are "air",
         * "weather", "reveal" and "open". This export used to ask for
         * "atmosphere", "wthr", "reveals" and "opener" — none of which any
         * generator path has ever produced — so every one of those lookups
         * returned blank and the exported card silently dropped the atmosphere,
         * weather and stakes fields. The alias is kept as a second chance for
         * scenarios saved before the keys were aligned.
         */
        fun slot(aliases: String, vararg keys: String): String {
            for (key in keys) {
                val value = part(key)
                if (value.isNotBlank()) return value
            }
            return part(aliases)
        }

        fun draft(key: String): String = drafts?.get(key)?.trim().orEmpty()

        val charName = draft("character_name").ifBlank {
            part("character_name").ifBlank {
                part("name").ifBlank { card?.name.orEmpty().ifBlank { brief.title } }
            }
        }

        val title = draft("name").ifBlank { part("name").ifBlank { brief.title.ifBlank { charName } } }
        val mode = draft("mode").ifBlank { part("mode").ifBlank { "ROLEPLAY" } }

        val personality = draft("personality").ifBlank {
            part("personality").ifBlank {
                card?.personality?.ifBlank { null } ?: buildString {
                    val role = part("arole")
                    val trait = part("atrait")
                    val want = part("awant")
                    val fear = part("afear")
                    if (role.isNotBlank()) append(role)
                    if (trait.isNotBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(trait)
                    }
                    if (want.isNotBlank()) {
                        if (isNotEmpty()) append(". Wants: ") else append("Wants: ")
                        append(want)
                    }
                    if (fear.isNotBlank()) {
                        if (isNotEmpty()) append(". Fears: ") else append("Fears: ")
                        append(fear)
                    }
                }.ifBlank { "Intriguing, responsive, and deeply grounded in the scene." }
            }
        }

        val settingBody = brief.slot("setting")?.body.orEmpty()
        val backstory = draft("backstory").ifBlank {
            part("backstory").ifBlank {
                settingBody.ifBlank { card?.description.orEmpty() }
            }
        }

        val appearance = draft("appearance").ifBlank {
            part("appearance").ifBlank {
                val texture = part("texture")
                val air = slot("atmosphere", "air")
                listOf(texture, air).filter { it.isNotBlank() }.joinToString(". ")
                    .ifBlank { card?.description.orEmpty() }
            }
        }

        val clothing = draft("clothing").ifBlank { part("clothing").ifBlank { part("texture") } }
        val storyTone = draft("storyTone").ifBlank {
            part("storyTone").ifBlank {
                slot("atmosphere", "air").ifBlank { Dials.EXPLICITNESS[brief.dials.explicitness.coerceIn(0, 2)] }
            }
        }
        val relationship = draft("relationship").ifBlank {
            part("relationship").ifBlank {
                part("power").ifBlank { Dials.POWER[brief.dials.power.coerceIn(0, 2)] }
            }
        }

        val worldAtmosphere = draft("worldAtmosphere").ifBlank {
            part("worldAtmosphere").ifBlank {
                listOf(part("place"), part("time"), slot("wthr", "weather"), slot("atmosphere", "air"))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }
        }
        val keyLocations = draft("keyLocations").ifBlank { part("keyLocations").ifBlank { part("place") } }
        val conflict = draft("scenarioConflict").ifBlank { part("scenarioConflict").ifBlank { part("tension").ifBlank { part("beat2") } } }
        val stakes = draft("scenarioStakes").ifBlank { part("scenarioStakes").ifBlank { slot("reveals", "reveal").ifBlank { part("beat1") } } }
        val timePeriod = draft("timePeriod").ifBlank { part("timePeriod").ifBlank { part("time") } }
        val inciting = draft("incitingIncident").ifBlank { part("incitingIncident").ifBlank { part("framing").ifBlank { brief.premise } } }

        val flaws = draft("characterFlaws").ifBlank { part("characterFlaws").ifBlank { slot("aflaw", "flaw") } }
        val secret = draft("secretMotive").ifBlank { part("secretMotive").ifBlank { slot("asecret", "secret") } }
        val speech = draft("speechPattern").ifBlank {
            part("speechPattern").ifBlank {
                part("register").ifBlank { Dials.EXPLICITNESS[brief.dials.explicitness.coerceIn(0, 2)] }
            }
        }
        val quirks = draft("quirks").ifBlank { part("quirks").ifBlank { part("atrait") } }

        val greeting = draft("greetingMessage").ifBlank {
            part("greetingMessage").ifBlank {
                card?.firstMessage?.ifBlank { null }
                    ?: brief.slot("dynamic")?.parts?.firstOrNull { it.key == "greetingMessage" }?.value
                    ?: brief.slot("open")?.body.orEmpty()
                    .ifBlank { slot("opener", "open") }
            }
        }

        val instructions = draft("scenarioInstructions").ifBlank {
            part("scenarioInstructions").ifBlank {
                card?.systemPrompt?.ifBlank { null } ?: buildString {
                    val framing = part("framing")
                    if (framing.isNotBlank()) appendLine("[Premise & Framing]\n$framing\n")
                    val beats = brief.slot("beats")?.body.orEmpty()
                    if (beats.isNotBlank()) appendLine("[Narrative Progression & Beats]\n$beats\n")
                    val twist = brief.slot("twist")?.body.orEmpty()
                    if (twist.isNotBlank()) appendLine("[Key Twist]\n$twist\n")
                    val close = brief.slot("close")?.body.orEmpty()
                    if (close.isNotBlank()) appendLine("[Resolution Focus]\n$close")
                }.trim()
            }
        }

        val playerName = draft("suggestedPlayerName").ifBlank {
            part("suggestedPlayerName").ifBlank {
                part("bname").ifBlank { "The Protagonist" }
            }
        }
        val playerDesc = draft("suggestedPlayerDescription").ifBlank {
            part("suggestedPlayerDescription").ifBlank {
                listOf(part("brole"), part("btrait")).filter { it.isNotBlank() }.joinToString(" · ")
            }
        }
        val playerPersonality = buildString {
            val bwant = part("bwant")
            val bfear = part("bfear")
            if (bwant.isNotBlank()) append("Wants: $bwant")
            if (bfear.isNotBlank()) {
                if (isNotEmpty()) append(". ")
                append("Fears: $bfear")
            }
        }

        val tagsList = draft("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty {
            card?.tags?.ifEmpty { null } ?: brief.allParts()
                .mapNotNull { it.value.takeIf(String::isNotBlank) }
                .filter { it.length in 3..25 }
                .take(6)
        }

        return PersonaForgeData(
            title = title,
            mode = mode,
            characterName = charName,
            personality = personality,
            backstory = backstory,
            appearance = appearance,
            clothing = clothing,
            storyTone = storyTone,
            relationship = relationship,
            worldAtmosphere = worldAtmosphere,
            worldSetting = worldAtmosphere,
            keyLocations = keyLocations,
            scenarioConflict = conflict,
            scenarioStakes = stakes,
            timePeriod = timePeriod,
            incitingIncident = inciting,
            characterFlaws = flaws,
            secretMotive = secret,
            speechPattern = speech,
            quirks = quirks,
            greetingMessage = greeting,
            scenarioInstructions = instructions,
            suggestedPlayerName = playerName,
            suggestedPlayerDescription = playerDesc,
            playerPersonality = playerPersonality,
            tags = tagsList,
        )
    }

    fun fromDraftValues(values: Map<String, String>): PersonaForgeData {
        fun v(key: String): String = values[key]?.trim().orEmpty()
        val title = v("name").ifBlank { v("character_name").ifBlank { "Untitled Scenario" } }
        val charName = v("character_name").ifBlank { title }
        return PersonaForgeData(
            title = title,
            mode = v("mode").ifBlank { "ROLEPLAY" },
            characterName = charName,
            personality = v("personality").ifBlank { "Intriguing and dynamic character." },
            backstory = v("backstory"),
            appearance = v("appearance"),
            clothing = v("clothing"),
            storyTone = v("storyTone").ifBlank { "Dramatic" },
            relationship = v("relationship").ifBlank { "Strangers" },
            worldAtmosphere = v("worldAtmosphere"),
            worldSetting = v("worldAtmosphere"),
            keyLocations = v("keyLocations"),
            scenarioConflict = v("scenarioConflict"),
            scenarioStakes = v("scenarioStakes"),
            timePeriod = v("timePeriod"),
            incitingIncident = v("incitingIncident"),
            characterFlaws = v("characterFlaws"),
            secretMotive = v("secretMotive"),
            speechPattern = v("speechPattern"),
            quirks = v("quirks"),
            greetingMessage = v("greetingMessage"),
            scenarioInstructions = v("scenarioInstructions"),
            suggestedPlayerName = v("suggestedPlayerName").ifBlank { "The Protagonist" },
            suggestedPlayerDescription = v("suggestedPlayerDescription"),
            tags = v("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    /**
     * Writes the export JSON to Downloads with standard PersonaForge naming.
     */
    fun writeToDownloads(context: Context, jsonString: String, scenarioTitle: String): File? {
        val safeTitle = scenarioTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "scenario" }
        val fileName = "${safeTitle}_personaforge.json"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, fileName)
            FileOutputStream(destFile).use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            destFile
        } catch (t: Throwable) {
            try {
                val dir = File(context.filesDir, "downloads").apply { mkdirs() }
                val fallback = File(dir, fileName)
                FileOutputStream(fallback).use { os ->
                    os.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                fallback
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun createShareIntent(context: Context, file: File, jsonString: String, title: String): Intent {
        val fileUri: Uri? = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Throwable) {
            null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "$title - PersonaForge Scenario")
            putExtra(Intent.EXTRA_TEXT, jsonString)
            if (fileUri != null) {
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    const val PERSONA_FORGE_PACKAGE = "com.aistudio.personaforge.xrqplm"

    /**
     * Directly targeted handoff intent to launch PersonaForge and deliver the scenario.
     */
    fun createDirectHandoffIntent(context: Context, file: File?, jsonString: String, title: String): Intent {
        val fileUri: Uri? = file?.let {
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
            } catch (_: Throwable) {
                null
            }
        }

        return Intent(Intent.ACTION_SEND).apply {
            setPackage(PERSONA_FORGE_PACKAGE)
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "$title - PersonaForge Scenario")
            putExtra(Intent.EXTRA_TEXT, jsonString)
            if (fileUri != null) {
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Check if PersonaForge is installed on the device.
     */
    fun isPersonaForgeInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PERSONA_FORGE_PACKAGE, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
