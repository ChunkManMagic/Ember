package com.ember.companion.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.companion.core.AiClient
import com.ember.companion.core.AiResult
import com.ember.companion.core.ChatTurn
import com.ember.companion.core.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One bubble in the conversation. */
private data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    /** True while the model is still writing this reply. */
    val pending: Boolean = false,
    /** True when this bubble reports a failure rather than conversation. */
    val isError: Boolean = false,
)

/**
 * Persona presets. Each is a system prompt describing a conversation partner;
 * the custom box overrides them. Ember is 18+ at the AgeGate and the app's
 * existing system prompt keeps every participant an adult.
 */
private val PERSONA_PRESETS = listOf(
    "Warm & Direct" to "You are a warm, direct adult conversation partner. Be present and responsive, " +
        "match the user's energy, and keep replies conversational rather than list-like.",
    "Playful & Confident" to "You are a playful, confident adult companion with a sense of humour. " +
        "Tease back, stay warm, and keep the exchange feeling spontaneous.",
    "Mysterious & Reserved" to "You are a mysterious, slightly guarded adult conversationalist. " +
        "Reveal yourself gradually, ask questions back, and keep an undercurrent of tension.",
    "Cinematic Slow Burn" to "You are a cinematic adult partner who builds tension slowly. Use concrete " +
        "sensory detail, deliberate pacing, and mutual agency rather than rushing the scene.",
)

/**
 * The session timer reports elapsed wall-clock time and nothing else. It is a
 * clock, not a meter: there is no rate, no balance, and no charge anywhere in
 * this screen.
 */
private const val MAX_HISTORY_TURNS = 40

@Composable
fun ChatScreen(onBack: () -> Unit = {}) {
    // The clock needs a state source to recompose against. derivedStateOf around
    // System.currentTimeMillis() would read the same value forever, because the
    // clock is not observable Compose state.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    val sessionStart = remember { System.currentTimeMillis() }
    val elapsedSeconds = remember(tick, sessionStart) {
        ((System.currentTimeMillis() - sessionStart) / 1000L).toInt()
    }
    val elapsedLabel = remember(elapsedSeconds) {
        String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
    }

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var personaIndex by remember { mutableStateOf(0) }
    var customPersona by remember { mutableStateOf("") }
    var awaitingReply by remember { mutableStateOf(false) }

    val personaPrompt = customPersona.ifBlank { PERSONA_PRESETS[personaIndex].second }
    val personaLabel = if (customPersona.isBlank()) PERSONA_PRESETS[personaIndex].first else "Custom"

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Built once: SettingsStore reads from disk-backed state, so rebuilding it
    // per keystroke would re-read preferences for nothing.
    val aiClient = remember(context) { AiClient(SettingsStore(context)) }

    val listState = rememberLazyListState()
    // Follow the conversation as it grows, including the reply that arrives
    // after a pause. Keyed on size because that is what actually changes.
    LaunchedEffect(messages.size, awaitingReply) {
        val target = messages.size + if (awaitingReply) 1 else 0
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || awaitingReply) return
        input = ""
        val history = messages
            .filterNot { it.pending || it.isError }
            .takeLast(MAX_HISTORY_TURNS)
            .map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text) } +
            ChatTurn("user", text)
        messages = messages + ChatMessage(System.currentTimeMillis(), fromUser = true, text = text)
        awaitingReply = true
        scope.launch {
            val result = aiClient.chat(
                turns = history,
                systemPrompt = buildChatSystemPrompt(personaPrompt),
                maxTokens = 600,
            )
            awaitingReply = false
            messages = messages + when (result) {
                is AiResult.Ok -> ChatMessage(System.currentTimeMillis(), fromUser = false, text = result.text.trim())
                is AiResult.Failure -> ChatMessage(
                    System.currentTimeMillis(),
                    fromUser = false,
                    text = result.message,
                    isError = true,
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The chat is layered over the Scaffold, so it inherits no padding
            // from it and has to inset itself around the system bars.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ChatHeader(
            personaLabel = personaLabel,
            elapsedLabel = elapsedLabel,
            onBack = onBack,
        )

        PersonaStrip(
            presetCount = PERSONA_PRESETS.size,
            selectedIndex = personaIndex,
            customPersona = customPersona,
            enabled = !awaitingReply,
            onPresetSelected = { personaIndex = it; customPersona = "" },
            onCustomPersonaChange = { customPersona = it },
            onRandom = {
                personaIndex = PERSONA_PRESETS.indices.random()
                customPersona = ""
            },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                MessageBubble(message)
            }
            if (awaitingReply) {
                item(key = "pending") { TypingBubble() }
            }
        }

        ChatComposer(
            value = input,
            enabled = !awaitingReply,
            onValueChange = { input = it },
            onSend = ::send,
        )
    }
}

/**
 * Wraps the chosen persona in Ember's standing safety frame rather than
 * replacing it, so switching persona cannot quietly drop the 18+ guarantee the
 * rest of the app already enforces.
 */
private fun buildChatSystemPrompt(persona: String): String = """
    $persona

    You are the other half of a private, one-to-one adult conversation inside the Ember app.
    Every participant is an adult (18+) and consenting. Never produce sexual content involving
    minors, children, or anyone whose age is ambiguous or could read as under 18 — if asked,
    decline briefly and offer an explicitly-adult alternative instead. Never depict a real,
    identifiable person in a sexual scenario.

    How to reply:
    - Write only your own side of the conversation, as spoken dialogue or narration.
    - Keep replies to roughly one conversational turn (2-5 sentences) so the exchange can
      breathe; do not write the user's lines for them.
    - React to what was just said. Reference earlier turns when they matter.
    - Stay in character for the persona above.
""".trimIndent()

@Composable
private fun ChatHeader(personaLabel: String, elapsedLabel: String, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "AI",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = personaLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "Connected · session $elapsedLabel",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Elapsed time only. Deliberately no rate or balance: nothing here
            // is billed.
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = elapsedLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun PersonaStrip(
    presetCount: Int,
    selectedIndex: Int,
    customPersona: String,
    enabled: Boolean,
    onPresetSelected: (Int) -> Unit,
    onCustomPersonaChange: (String) -> Unit,
    onRandom: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PERSONA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onRandom, enabled = enabled) {
                    Text("Shuffle", fontSize = 11.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (index in 0 until presetCount) {
                    val isSelected = customPersona.isBlank() && index == selectedIndex
                    FilterChip(
                        selected = isSelected,
                        enabled = enabled,
                        onClick = { onPresetSelected(index) },
                        label = {
                            Text(PERSONA_PRESETS[index].first, fontSize = 10.sp, maxLines = 1)
                        },
                        modifier = Modifier.heightIn(min = 28.dp),
                    )
                }
            }
            OutlinedTextField(
                value = customPersona,
                onValueChange = onCustomPersonaChange,
                enabled = enabled,
                label = { Text("Custom persona", fontSize = 11.sp) },
                placeholder = { Text("Describe who you're talking to…", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        val container = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            message.fromUser -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val content = when {
            message.isError -> MaterialTheme.colorScheme.onErrorContainer
            message.fromUser -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            color = container,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (message.isError) 0.dp else 1.dp,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                fontSize = 15.sp,
                color = content,
            )
        }
    }
}

/** Three pulsing dots while the model is composing its reply. */
@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "typingAlpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(14.dp)
                        .alpha(alpha),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "typing…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                placeholder = { Text("Say something…", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}