package com.ember.companion

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ember.companion.core.Diag
import com.ember.companion.ui.AgeGate
import com.ember.companion.ui.CrashScreen
import com.ember.companion.ui.EmberRoot
import com.ember.companion.ui.EmberViewModel
import com.ember.companion.ui.theme.EmberTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as EmberApp).container

        // Optional: FLAG_SECURE keeps Ember out of screenshots, the recent-apps
        // thumbnail and screen recording. Read at launch, so toggling it needs a
        // restart — which is what the settings copy says.
        if (container.settings.offscreenGuard.value) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        setContent {
            var crashReport by remember { mutableStateOf(Diag.consumePending(this)) }

            EmberTheme(darkTheme = isSystemInDarkTheme()) {
                when {
                    crashReport != null -> CrashScreen(
                        report = crashReport!!,
                        onDismiss = {
                            Diag.clearPending(this)
                            crashReport = null
                        },
                    )

                    else -> {
                        val vm: EmberViewModel = viewModel(factory = EmberViewModel.factory(container))
                        val passed by vm.ageGatePassed.collectAsStateWithLifecycle()
                        if (passed) {
                            EmberRoot(vm = vm)
                        } else {
                            AgeGate(
                                onAccept = { vm.passAgeGate() },
                                onDecline = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }
}
