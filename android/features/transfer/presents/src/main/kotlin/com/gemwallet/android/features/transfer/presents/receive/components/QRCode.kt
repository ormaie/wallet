package com.gemwallet.android.features.transfer.presents.receive.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val defaultQrCodeSize = 180.dp
private const val QUIET_MODULES = 0.5f
private val qrInkColor = Color.rgb(0x11, 0x11, 0x13)

@Composable
fun rememberQRCodePainter(content: String, size: Dp = defaultQrCodeSize): BitmapPainter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }

    val painter by produceState<BitmapPainter?>(initialValue = null, content, sizePx) {
        value = withContext(Dispatchers.Default) {
            BitmapPainter(generateQr(content, sizePx).asImageBitmap())
        }
    }
    return painter
}

private fun generateQr(content: String, sizePx: Int): Bitmap {
    if (sizePx <= 0 || content.isEmpty()) {
        return createBitmap(1, 1)
    }
    val hints = mapOf(
        EncodeHintType.MARGIN to 0,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = runCatching {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 1, 1, hints)
    }.getOrNull() ?: return createBitmap(sizePx, sizePx).apply { eraseColor(Color.WHITE) }

    val moduleCount = matrix.width
    val bitmap = createBitmap(sizePx, sizePx).apply { eraseColor(Color.WHITE) }
    val canvas = Canvas(bitmap)
    val cellSize = sizePx / (moduleCount + QUIET_MODULES * 2f)
    val offset = QUIET_MODULES * cellSize
    val paint = Paint().apply {
        color = qrInkColor
        style = Paint.Style.FILL
    }

    for (y in 0 until moduleCount) {
        val top = (offset + y * cellSize).toInt().toFloat()
        val bottom = (offset + (y + 1) * cellSize).toInt().toFloat()
        for (x in 0 until moduleCount) {
            if (matrix.get(x, y)) {
                val left = (offset + x * cellSize).toInt().toFloat()
                val right = (offset + (x + 1) * cellSize).toInt().toFloat()
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
    }
    return bitmap
}
