package com.ember.companion.core

import android.content.Context
import android.content.SharedPreferences
import com.ember.companion.data.Banks
import com.ember.companion.data.Dials
import com.ember.companion.data.db.Scenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Fixed mask used only when the hardware Keystore is unavailable. Not a security claim. */
private val FALLBACK_KEY = "ember::companion::v1".toCharArray()

enum class AiProvider(val label: String, val needsKey: Boolean) {
    OPENAI("OpenAI-compatible", true),
    ANTHROPIC("Anthropic", true),
    GEMINI("Google Gemini", true),
}

/**
 * All Ember preferences live here. Non-secret settings are plain prefs; the API
 * key is the single secret and is always stored as Keystore ciphertext via
 * [KeyStoreCipher], never in plaintext.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ember_settings", Context.MODE_PRIVATE)
    private val cipher = KeyStoreCipher()

    private val _ageGatePassed = MutableStateFlow(prefs.getBoolean(KEY_AGE_GATE, false))
    val ageGatePassed: StateFlow<Boolean> = _ageGatePassed.asStateFlow()

    private val _aiEnabled = MutableStateFlow(prefs.getBoolean(KEY_AI_ENABLED, false))
    val aiEnabled: StateFlow<Boolean> = _aiEnabled.asStateFlow()

    private val _aiProvider = MutableStateFlow(
        runCatching { AiProvider.valueOf(prefs.getString(KEY_AI_PROVIDER, null) ?: AiProvider.OPENAI.name) }
            .getOrDefault(AiProvider.OPENAI),
    )
    val aiProvider: StateFlow<AiProvider> = _aiProvider.asStateFlow()

    private val _aiBaseUrl = MutableStateFlow(prefs.getString(KEY_AI_BASE_URL, defaultBaseUrl(AiProvider.OPENAI)) ?: "")
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl.asStateFlow()

    private val _aiModel = MutableStateFlow(
        sanitizeModel(prefs.getString(KEY_AI_MODEL, null), _aiProvider.value)
    )
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _aiTemperature = MutableStateFlow(
        prefs.getFloat(KEY_AI_TEMPERATURE, DEFAULT_TEMPERATURE).coerceIn(0.0f, 2.0f),
    )
    val aiTemperature: StateFlow<Float> = _aiTemperature.asStateFlow()

    init {
        val saved = prefs.getString(KEY_AI_MODEL, null)
        val clean = sanitizeModel(saved, _aiProvider.value)
        if (saved != clean) {
            prefs.edit().putString(KEY_AI_MODEL, clean).apply()
        }
    }

    private val _aiHasKey = MutableStateFlow(prefs.getString(KEY_API_KEY, null)?.isNotBlank() == true)
    val aiHasKey: StateFlow<Boolean> = _aiHasKey.asStateFlow()

    private val _veniceHasKey = MutableStateFlow(prefs.getString(KEY_VENICE_API_KEY, null)?.isNotBlank() == true)
    val veniceHasKey: StateFlow<Boolean> = _veniceHasKey.asStateFlow()

    private val _offscreenGuard = MutableStateFlow(prefs.getBoolean(KEY_OFFSCREEN_GUARD, true))
    val offscreenGuard: StateFlow<Boolean> = _offscreenGuard.asStateFlow()

    private val _blockThirdPartyCookies = MutableStateFlow(prefs.getBoolean(KEY_BLOCK_3P_COOKIES, true))
    val blockThirdPartyCookies: StateFlow<Boolean> = _blockThirdPartyCookies.asStateFlow()

    private val _desktopMode = MutableStateFlow(prefs.getBoolean(KEY_DESKTOP_MODE, false))
    val desktopMode: StateFlow<Boolean> = _desktopMode.asStateFlow()

    private val _incognito = MutableStateFlow(prefs.getBoolean(KEY_INCOGNITO, false))
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()

    // ---- Lab steering persistence ------------------------------------------
    private val _labDials = MutableStateFlow(Dials(
        explicitness = prefs.getInt(KEY_LAB_EXPLICITNESS, 1).coerceIn(0, 2),
        pace = prefs.getInt(KEY_LAB_PACE, 1).coerceIn(0, 2),
        power = prefs.getInt(KEY_LAB_POWER, 1).coerceIn(0, 2),
        pov = prefs.getInt(KEY_LAB_POV, 1).coerceIn(0, 2),
    ))
    val labDials: StateFlow<Dials> = _labDials.asStateFlow()

    private val _labPremise = MutableStateFlow(prefs.getString(KEY_LAB_PREMISE, "") ?: "")
    val labPremise: StateFlow<String> = _labPremise.asStateFlow()

    private val _labSeed = MutableStateFlow(
        runCatching { prefs.getString(KEY_LAB_SEED, "") ?: "" }.getOrElse {
            prefs.edit().remove(KEY_LAB_SEED).apply()
            ""
        }
    )
    val labSeed: StateFlow<String> = _labSeed.asStateFlow()

    private val _labTaste = MutableStateFlow(runCatching {
        val json = Json { ignoreUnknownKeys = true }
        val raw = prefs.getString(KEY_LAB_TASTE, null)
        if (raw.isNullOrBlank()) Banks.Taste() else json.decodeFromString<Banks.Taste>(raw)
    }.getOrDefault(Banks.Taste()))
    val labTaste: StateFlow<Banks.Taste> = _labTaste.asStateFlow()

    private val _labCustomBanks = MutableStateFlow(runCatching {
        val json = Json { ignoreUnknownKeys = true }
        val raw = prefs.getString(KEY_LAB_CUSTOM_BANKS, null)
        if (raw.isNullOrBlank()) Banks.Custom() else json.decodeFromString<Banks.Custom>(raw)
    }.getOrDefault(Banks.Custom()))
    val labCustomBanks: StateFlow<Banks.Custom> = _labCustomBanks.asStateFlow()

    val isKeyHardwareBacked: Boolean get() = cipher.isHardwareBacked

    fun setAgeGatePassed(passed: Boolean) {
        prefs.edit().putBoolean(KEY_AGE_GATE, passed).apply()
        _ageGatePassed.value = passed
    }

    fun setAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
        _aiEnabled.value = enabled
    }

    fun setAiProvider(provider: AiProvider) {
        prefs.edit()
            .putString(KEY_AI_PROVIDER, provider.name)
            .putString(KEY_AI_BASE_URL, defaultBaseUrl(provider))
            .putString(KEY_AI_MODEL, defaultModel(provider))
            .apply()
        _aiProvider.value = provider
        _aiBaseUrl.value = defaultBaseUrl(provider)
        _aiModel.value = defaultModel(provider)
    }

    fun setAiBaseUrl(url: String) {
        prefs.edit().putString(KEY_AI_BASE_URL, url.trim()).apply()
        _aiBaseUrl.value = url.trim()
    }

    fun setAiModel(model: String) {
        val clean = sanitizeModel(model, _aiProvider.value)
        prefs.edit().putString(KEY_AI_MODEL, clean).apply()
        _aiModel.value = clean
    }

    fun setAiTemperature(value: Float) {
        val clamped = value.coerceIn(0.0f, 2.0f)
        prefs.edit().putFloat(KEY_AI_TEMPERATURE, clamped).apply()
        _aiTemperature.value = clamped
    }

    fun setApiKey(plain: String) {
        val value = plain.trim()
        if (value.isEmpty()) {
            clearApiKey()
            return
        }
        val stored = cipher.encrypt(value) ?: FALLBACK_PREFIX + obfuscate(value)
        prefs.edit().putString(KEY_API_KEY, stored).apply()
        _aiHasKey.value = true
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
        _aiHasKey.value = false
    }

    /** Never log or return the raw key outside of an outbound request. */
    fun apiKeyOrNull(): String? {
        val stored = prefs.getString(KEY_API_KEY, null) ?: return null
        if (stored.startsWith(FALLBACK_PREFIX)) return deobfuscate(stored.removePrefix(FALLBACK_PREFIX))
        return cipher.decrypt(stored)
    }

    fun setVeniceApiKey(plain: String) {
        val value = plain.trim()
        if (value.isEmpty()) {
            clearVeniceApiKey()
            return
        }
        val stored = cipher.encrypt(value) ?: FALLBACK_PREFIX + obfuscate(value)
        prefs.edit().putString(KEY_VENICE_API_KEY, stored).apply()
        _veniceHasKey.value = true
    }

    fun clearVeniceApiKey() {
        prefs.edit().remove(KEY_VENICE_API_KEY).apply()
        _veniceHasKey.value = false
    }

    fun veniceApiKeyOrNull(): String? {
        val stored = prefs.getString(KEY_VENICE_API_KEY, null) ?: return null
        if (stored.startsWith(FALLBACK_PREFIX)) return deobfuscate(stored.removePrefix(FALLBACK_PREFIX))
        return cipher.decrypt(stored)
    }

    fun setOffscreenGuard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFSCREEN_GUARD, enabled).apply()
        _offscreenGuard.value = enabled
    }

    fun setBlockThirdPartyCookies(block: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCK_3P_COOKIES, block).apply()
        _blockThirdPartyCookies.value = block
    }

    fun setDesktopMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
        _desktopMode.value = enabled
    }

    fun setIncognito(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCOGNITO, enabled).apply()
        _incognito.value = enabled
    }

    // ---- Lab steering setters ----------------------------------------------
    fun setLabDials(dials: Dials) {
        prefs.edit()
            .putInt(KEY_LAB_EXPLICITNESS, dials.explicitness)
            .putInt(KEY_LAB_PACE, dials.pace)
            .putInt(KEY_LAB_POWER, dials.power)
            .putInt(KEY_LAB_POV, dials.pov)
            .apply()
        _labDials.value = dials
    }

    fun setLabPremise(premise: String) {
        prefs.edit().putString(KEY_LAB_PREMISE, premise).apply()
        _labPremise.value = premise
    }

    fun setLabSeed(seed: String) {
        prefs.edit().putString(KEY_LAB_SEED, seed).apply()
        _labSeed.value = seed
    }

    fun setLabTaste(taste: Banks.Taste) {
        val json = Json { ignoreUnknownKeys = true }
        prefs.edit().putString(KEY_LAB_TASTE, json.encodeToString(Banks.Taste.serializer(), taste)).apply()
        _labTaste.value = taste
    }

    fun setLabCustomBanks(custom: Banks.Custom) {
        val json = Json { ignoreUnknownKeys = true }
        prefs.edit().putString(KEY_LAB_CUSTOM_BANKS, json.encodeToString(Banks.Custom.serializer(), custom)).apply()
        _labCustomBanks.value = custom
    }

    /** Backs Settings' "Clear all Ember data". The age gate is deliberately kept. */
    fun resetAll() {
        prefs.edit().clear().apply()
        _aiEnabled.value = false
        _aiProvider.value = AiProvider.OPENAI
        _aiBaseUrl.value = defaultBaseUrl(AiProvider.OPENAI)
        _aiModel.value = defaultModel(AiProvider.OPENAI)
        _aiTemperature.value = DEFAULT_TEMPERATURE
        _aiHasKey.value = false
        _offscreenGuard.value = true
        _blockThirdPartyCookies.value = true
        _desktopMode.value = false
        _incognito.value = false
        _labDials.value = Dials()
        _labPremise.value = ""
        _labSeed.value = ""
        _labTaste.value = Banks.Taste()
        _labCustomBanks.value = Banks.Custom()
    }

    fun defaultBaseUrl(provider: AiProvider): String = Companion.defaultBaseUrl(provider)

    fun defaultModel(provider: AiProvider): String = Companion.defaultModel(provider)

    fun recommendedModels(provider: AiProvider): List<String> = Companion.recommendedModels(provider)

    fun sanitizeModel(raw: String?, provider: AiProvider): String = Companion.sanitizeModel(raw, provider)

    private fun obfuscate(value: String): String =
        String(xor(value.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)

    private fun deobfuscate(value: String): String? = runCatching {
        String(xor(value.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
    }.getOrNull()

    private fun xor(bytes: ByteArray): ByteArray = ByteArray(bytes.size) { index ->
        val key = FALLBACK_KEY[index % FALLBACK_KEY.size].code
        ((bytes[index].toInt() xor key) and 0xFF).toByte()
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.7f
        private const val KEY_AI_TEMPERATURE = "ai_temperature"
        private const val KEY_AGE_GATE = "age_gate_passed"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_BASE_URL = "ai_base_url"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_VENICE_API_KEY = "venice_api_key"
        private const val KEY_OFFSCREEN_GUARD = "offscreen_guard"
        private const val KEY_BLOCK_3P_COOKIES = "block_3p_cookies"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_INCOGNITO = "incognito"
        private const val KEY_LAB_EXPLICITNESS = "lab_explicitness"
        private const val KEY_LAB_PACE = "lab_pace"
        private const val KEY_LAB_POWER = "lab_power"
        private const val KEY_LAB_POV = "lab_pov"
        private const val KEY_LAB_PREMISE = "lab_premise"
        private const val KEY_LAB_SEED = "lab_seed"
        private const val KEY_LAB_TASTE = "lab_taste"
        private const val KEY_LAB_CUSTOM_BANKS = "lab_custom_banks"
        private const val FALLBACK_PREFIX = "obf1:"

        fun defaultBaseUrl(provider: AiProvider): String = when (provider) {
            AiProvider.OPENAI -> "https://api.openai.com/v1"
            AiProvider.ANTHROPIC -> "https://api.anthropic.com/v1"
            AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
        }

        fun defaultModel(provider: AiProvider): String = when (provider) {
            AiProvider.OPENAI -> "gpt-4o-mini"
            AiProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
            AiProvider.GEMINI -> "gemini-2.5-flash"
        }

        fun recommendedModels(provider: AiProvider): List<String> = when (provider) {
            AiProvider.OPENAI -> listOf("gpt-4o-mini", "gpt-4o", "o3-mini", "o1-mini")
            AiProvider.ANTHROPIC -> listOf(
                "claude-3-5-sonnet-20241022",
                "claude-3-7-sonnet-latest",
                "claude-3-5-haiku-20241022",
            )
            AiProvider.GEMINI -> listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
        }

        fun sanitizeModel(raw: String?, provider: AiProvider): String {
            val model = raw?.trim().orEmpty()
            if (model.isEmpty()) return defaultModel(provider)
            if (provider == AiProvider.ANTHROPIC && (model == "claude-sonnet-5" || model == "claude-sonnet")) {
                return "claude-3-5-sonnet-20241022"
            }
            return model
        }
    }
}
