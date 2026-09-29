package com.ember.companion.ui.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ember.companion.ui.EmberViewModel
import com.ember.companion.data.FormField
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.Icons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSelectionScreen(vm: EmberViewModel) {
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    val fields by vm.platformFields.collectAsStateWithLifecycle()
    val payload by vm.exportPayload.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platform Helper") },
                navigationIcon = {
                    IconButton(onClick = { vm.goBackToLab() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Platform URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { vm.loadPlatformFields(urlInput) },
                    modifier = Modifier.weight(1f)
                ) { Text("Fetch Fields") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { vm.buildExportPayload(urlInput) },
                    modifier = Modifier.weight(1f)
                ) { Text("Generate JSON") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (fields.isNotEmpty()) {
                Text("Discovered Fields:", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(fields) { field ->
                        val example = field.example?.let { "example: $it" } ?: ""
                        Text("- ${field.name} (${field.type}) $example")
                    }
                }
            }
            if (!payload.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Export Payload:", style = MaterialTheme.typography.titleMedium)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(payload!!, modifier = Modifier.padding(8.dp))
                }
                IconButton(onClick = { vm.copyAiOutput(context) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy JSON")
                }
            }
        }
    }
}
