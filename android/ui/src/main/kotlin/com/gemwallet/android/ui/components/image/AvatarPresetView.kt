package com.gemwallet.android.ui.components.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withTranslation
import kotlin.math.min

@Composable
fun AvatarPresetView(preset: AvatarPreset, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val press = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Spacer(
        modifier = modifier
            .clip(CircleShape)
            .then(press)
            .drawWithCache {
                val dimension = min(size.width, size.height)
                if (dimension <= 0f) {
                    return@drawWithCache onDrawBehind {}
                }
                val originX = (size.width - dimension) / 2f
                val originY = (size.height - dimension) / 2f
                val prepared = prepareAvatarPreset(preset, dimension)
                onDrawBehind {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.withTranslation(originX, originY) {
                            prepared.draw(this)
                        }
                    }
                }
            },
    )
}
