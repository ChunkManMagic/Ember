package com.ember.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.companion.ui.discover.DiscoverScreen
import com.ember.companion.ui.lab.ScenarioLabScreen
import com.ember.companion.ui.library.LibraryScreen
import com.ember.companion.ui.search.SearchScreen
import com.ember.companion.ui.settings.SettingsScreen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.dp

@Composable
fun EmberRoot(vm: EmberViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val message by vm.snackMessage.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    val direction = LocalLayoutDirection.current

    // Back handling for tab navigation backstack
    BackHandler(enabled = vm.canNavigateBackTab()) {
        vm.navigateBackTab()
    }

    // Lets the Lab share text without the view model holding a Context.
    val chooser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { }
    vm.exportLauncher = { intent -> chooser.launch(intent) }

    // Card export goes through the system file picker rather than a direct
    // Downloads write: it needs no storage permission on any API level the app
    // supports, and the user chooses where the file lands.
    val createCard = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri -> uri?.let { vm.writePendingCard(it) } }
    vm.cardSaveLauncher = { mime, name ->
        createCard.launch(name) // mime is carried for the sheet title only
    }

    val pickCard = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.readCardFile(it) } }
    vm.cardOpenLauncher = { pickCard.launch(arrayOf("*/*")) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackHost.showSnackbar(text)
        vm.consumeMessage()
    }

    // Chat is a tab like any other: burying it in Settings behind a button that
    // is disabled until an API key exists made it undiscoverable, which is not
    // the same as "a small side feature". Tab back-stack still applies, so back
    // from Chat returns to wherever the user came from.
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    tonalElevation = 4.dp,
                ) {
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
            // Screens get bottom padding for the NavigationBar and horizontal insets.
            // Top padding is set to 0.dp so child screen TopAppBars handle their own
            // status bar insets without double-padding.
            val screenPadding = PaddingValues(
                top = 0.dp,
                bottom = inner.calculateBottomPadding(),
                start = inner.calculateStartPadding(direction),
                end = inner.calculateEndPadding(direction),
            )

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                    (slideIntoContainer(direction, animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(slideOutOfContainer(direction, animationSpec = tween(220)) + fadeOut(animationSpec = tween(220)))
                },
                label = "TabAnimatedContent",
                modifier = Modifier.fillMaxSize(),
            ) { currentTab ->
                Box(Modifier.fillMaxSize()) {
                    when (currentTab) {
                        Tab.DISCOVER -> DiscoverScreen(vm = vm, contentPadding = screenPadding)
                        Tab.SEARCH -> SearchScreen(vm = vm, contentPadding = screenPadding)
                        Tab.LIBRARY -> LibraryScreen(vm = vm, contentPadding = screenPadding)
                        Tab.LAB -> ScenarioLabScreen(vm = vm, contentPadding = screenPadding)
                        Tab.CHAT -> ChatScreen(vm = vm, contentPadding = screenPadding)
                        Tab.SETTINGS -> SettingsScreen(vm = vm, contentPadding = screenPadding)
                    }
                }
            }
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.DISCOVER -> Icons.Filled.Explore
    Tab.SEARCH -> Icons.Filled.Search
    Tab.LIBRARY -> Icons.Filled.VideoLibrary
    Tab.LAB -> Icons.Filled.Science
    Tab.CHAT -> Icons.AutoMirrored.Filled.Chat
    Tab.SETTINGS -> Icons.Filled.Tune
}
