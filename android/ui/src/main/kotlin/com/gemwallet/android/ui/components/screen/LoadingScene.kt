package com.gemwallet.android.ui.components.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gemwallet.android.ui.theme.sceneContentPaddingValues

@Composable
fun LoadingScene(title: String, onCancel: () -> Unit, closeIcon: Boolean = false, containerColor: Color = MaterialTheme.colorScheme.surface) {
    Scene(
        title = title,
        padding = sceneContentPaddingValues(),
        onClose = onCancel,
        closeIcon = closeIcon,
        containerColor = containerColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
