package com.ember.companion.ui.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ember.companion.ui.EmberViewModel

/**
 * Replaced by [PlatformFieldsDialog], which does the same job properly: real
 * per-platform field lists with limits, saved drafts, and form importing.
 *
 * Kept only as a compile-time guard — if anything still routes here it is a bug,
 * so this says so rather than quietly rendering a dead end.
 */
@Deprecated("Use PlatformFieldsDialog; reachable from the Lab's card card.")
@Composable
fun PlatformSelectionScreen(vm: EmberViewModel) {
    val id by vm.selectedSchemaId.collectAsState()
    val fields by vm.platformFields.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Platform helper has moved")
        Text("Use “Platform fields” in the Lab. Selected: $id (${fields.size} fetched)")
    }
}