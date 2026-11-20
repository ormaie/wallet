package com.gemwallet.android.ui.components.qrcode

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.gemwallet.android.ui.theme.paddingDefault
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val defaultAnimatedQrSize = 260.dp
private val qrContainerCornerRadius = 16.dp
private const val QR_MARGIN = 2
private const val FRAME_INTERVAL_MS = 125L

@Composable
fun AnimatedQRCode(
    frames: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = defaultAnimatedQrSize,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val bitmaps by produceState<List<ImageBitmap>>(initialValue = emptyList(), frames, sizePx) {
        value = withContext(Dispatchers.Default) {
            frames.map { frame -> encodeQrBitmap(frame, sizePx).asImageBitmap() }
        }
    }
    var frameIndex by remember(frames) { mutableIntStateOf(0) }

    LaunchedEffect(bitmaps.size) {
        frameIndex = 0
        if (bitmaps.size > 1) {
            while (true) {
                delay(FRAME_INTERVAL_MS)
                frameIndex = (frameIndex + 1) % bitmaps.size
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(qrContainerCornerRadius))
            .background(Color.White)
            .padding(paddingDefault),
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmaps.getOrNull(frameIndex % bitmaps.size.coerceAtLeast(1))
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None,
            )
        } else {
            Box(modifier = Modifier.size(size))
        }
    }
}

private fun encodeQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to QR_MARGIN,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
    )
    val matrix = runCatching {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }.getOrNull() ?: return createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply { eraseColor(android.graphics.Color.WHITE) }

    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565)
}
