package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.ApiProvider
import com.example.model.CustomApiSettings
import com.example.model.ZenvixModelSpec
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LoraFontFamily
import com.example.viewmodel.ApiConnectionTestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsBottomSheet(
    initialSettings: CustomApiSettings,
    testState: ApiConnectionTestState,
    onTestConnection: (CustomApiSettings, String) -> Unit,
    onSave: (CustomApiSettings) -> Unit,
    onClearKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var provider by remember(initialSettings) { mutableStateOf(initialSettings.provider) }
    var baseUrl by remember(initialSettings) { mutableStateOf(initialSettings.baseUrl) }
    var apiKey by remember(initialSettings) { mutableStateOf(initialSettings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var models by remember(initialSettings) { mutableStateOf(initialSettings.customModels) }

    var newModelName by remember { mutableStateOf("") }
    var newModelId by remember { mutableStateOf("") }

    val buildDraftSettings = {
        CustomApiSettings(
            provider = provider,
            baseUrl = baseUrl.trim().ifBlank { provider.defaultBaseUrl },
            apiKey = apiKey.trim(),
            customModels = models
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "API Key & Custom Models",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = LoraFontFamily)
                    )
                    Text(
                        text = "Use Opus 5.5, Febal 5.1, Sonnet 5.5, or any custom endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "Your API key is encrypted locally using Android Keystore and never uploaded to the cloud.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Text("API Provider Format", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiProvider.entries.forEach { item ->
                    FilterChip(
                        selected = provider == item,
                        onClick = {
                            val wasDefault = baseUrl.trim() == provider.defaultBaseUrl || baseUrl.isBlank()
                            provider = item
                            if (wasDefault) {
                                baseUrl = item.defaultBaseUrl
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
            Text(
                text = provider.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text(provider.defaultBaseUrl) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_base_url_input")
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-... or your provider key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showKey) "Hide API key" else "Show API key"
                            )
                        }
                        if (apiKey.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    apiKey = ""
                                    onClearKey()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Clear saved API key",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val firstModel = models.firstOrNull()?.id ?: "opus-5.5"
                        onTestConnection(buildDraftSettings(), firstModel)
                    },
                    enabled = !testState.isTesting && apiKey.isNotBlank(),
                    modifier = Modifier.testTag("test_api_button")
                ) {
                    if (testState.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing…")
                    } else {
                        Text("Test Connection")
                    }
                }

                testState.message?.let { msg ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (testState.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (testState.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (testState.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            maxLines = 2
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("Configured API Models (Editable IDs)", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "Adjust exact model IDs if your provider expects a prefix (e.g. anthropic/opus-5.5).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            models.forEachIndexed { index, spec ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = spec.id,
                        onValueChange = { updatedId ->
                            models = models.toMutableList().apply {
                                this[index] = spec.copy(id = updatedId)
                            }
                        },
                        label = { Text(spec.displayName) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.weight(1f)
                    )
                    if (models.size > 1) {
                        IconButton(
                            onClick = {
                                models = models.filterIndexed { i, _ -> i != index }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove ${spec.displayName}"
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newModelName,
                    onValueChange = { newModelName = it },
                    label = { Text("Label") },
                    placeholder = { Text("Custom") },
                    singleLine = true,
                    modifier = Modifier.weight(0.42f)
                )
                OutlinedTextField(
                    value = newModelId,
                    onValueChange = { newModelId = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("model-id") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.weight(0.58f)
                )
                IconButton(
                    onClick = {
                        val cleanId = newModelId.trim()
                        val cleanName = newModelName.trim().ifBlank { cleanId }
                        if (cleanId.isNotEmpty() && models.none { it.id == cleanId }) {
                            models = models + ZenvixModelSpec(
                                id = cleanId,
                                displayName = cleanName,
                                badge = "API • Custom",
                                subtitle = "Custom API endpoint model",
                                isExternalApi = true,
                                isFreeBuiltIn = false
                            )
                            newModelName = ""
                            newModelId = ""
                        }
                    },
                    enabled = newModelId.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add custom model")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(buildDraftSettings()) },
                    modifier = Modifier.testTag("save_api_settings_button")
                ) {
                    Text("Save Configuration")
                }
            }
        }
    }
}
