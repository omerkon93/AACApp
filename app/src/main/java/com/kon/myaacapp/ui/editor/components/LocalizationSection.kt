package com.kon.myaacapp.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kon.myaacapp.R

@Composable
fun LocalizationSection(
    labelFeminine: String,
    onLabelFeminineChange: (String) -> Unit,
    ttsTextFeminine: String,
    onTtsTextFeminineChange: (String) -> Unit
) {
    EditSection(
        title = stringResource(R.string.section_hebrew),
        icon = Icons.Default.Translate,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        iconColor = MaterialTheme.colorScheme.secondary,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.label_feminine_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = labelFeminine,
                    onValueChange = onLabelFeminineChange,
                    placeholder = { Text(stringResource(R.string.feminine_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.tts_feminine_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ttsTextFeminine,
                    onValueChange = onTtsTextFeminineChange,
                    placeholder = { Text(stringResource(R.string.tts_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                )
            }
        }
    }
}