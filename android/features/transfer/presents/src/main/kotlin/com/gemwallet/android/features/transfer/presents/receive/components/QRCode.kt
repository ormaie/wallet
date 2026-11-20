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
private const val CENTER_CLEAR_RADIUS_RATIO = 0.105f
private const val MODULE_INSET_RATIO = 0.04f
private const val MODULE_CORNER_RATIO = 0.14f
private const val FINDER_OUTER_CORNER_RATIO = 1.2f
private const val FINDER_INNER_CORNER_RATIO = 0.6f
private const val FINDER_SPAN = 7
private const val FINDER_QUIET_SPAN = 8
private val qrInkColor = Color.rgb(0x11, 0x11, 0x13)

@Composable
fun rememberQRCodePainter(content: String, size: Dp = defaultQrCodeSize, clearCenter: Boolean = true): BitmapPainter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }

    val painter by produceState<BitmapPainter?>(initialValue = null, content, sizePx, clearCenter) {
        value = withContext(Dispatchers.Default) {
            BitmapPainter(generateQr(content, sizePx, clearCenter).asImageBitmap())
        }
    }
    return painter
}

private fun generateQr(content: String, sizePx: Int, clearCenter: Boolean): Bitmap {
    if (sizePx <= 0 || content.isEmpty()) {
        return createBitmap(1, 1)
    }
    val hints = mapOf(
        EncodeHintType.MARGIN to 0,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
    )
    val matrix = runCatching {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 1, 1, hints)
    }.getOrNull() ?: return createBitmap(sizePx, sizePx).apply { eraseColor(Color.WHITE) }

    val moduleCount = matrix.width
    val bitmap = createBitmap(sizePx, sizePx).apply { eraseColor(Color.WHITE) }
    val canvas = Canvas(bitmap)
    val cellSize = sizePx / (moduleCount + QUIET_MODULES * 2f)
    val offset = QUIET_MODULES * cellSize
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = qrInkColor
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = qrInkColor
        style = Paint.Style.STROKE
        strokeWidth = cellSize
    }

    val centerCoord = moduleCount / 2f
    val clearRadius = if (clearCenter) moduleCount * CENTER_CLEAR_RADIUS_RATIO else 0f
    val clearRadiusSquared = clearRadius * clearRadius
    val inset = cellSize * MODULE_INSET_RATIO
    val radius = cellSize * MODULE_CORNER_RATIO

    for (y in 0 until moduleCount) {
        for (x in 0 until moduleCount) {
            if (isFinderZone(x, y, moduleCount)) continue
            if (clearCenter) {
                val dx = x + 0.5f - centerCoord
                val dy = y + 0.5f - centerCoord
                if (dx * dx + dy * dy <= clearRadiusSquared) continue
            }
            if (matrix.get(x, y)) {
                val left = offset + x * cellSize + inset
                val top = offset + y * cellSize + inset
                val right = offset + (x + 1) * cellSize - inset
                val bottom = offset + (y + 1) * cellSize - inset
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, fillPaint)
            }
        }
    }

    drawFinderPattern(canvas, fillPaint, strokePaint, 0, 0, offset, cellSize)
    drawFinderPattern(canvas, fillPaint, strokePaint, moduleCount - FINDER_SPAN, 0, offset, cellSize)
    drawFinderPattern(canvas, fillPaint, strokePaint, 0, moduleCount - FINDER_SPAN, offset, cellSize)
    return bitmap
}

private fun isFinderZone(x: Int, y: Int, moduleCount: Int): Boolean =
    (x < FINDER_QUIET_SPAN && y < FINDER_QUIET_SPAN) ||
        (x >= moduleCount - FINDER_QUIET_SPAN && y < FINDER_QUIET_SPAN) ||
        (x < FINDER_QUIET_SPAN && y >= moduleCount - FINDER_QUIET_SPAN)

private fun drawFinderPattern(
    canvas: Canvas,
    fillPaint: Paint,
    strokePaint: Paint,
    ox: Int,
    oy: Int,
    offset: Float,
    cellSize: Float,
) {
    val outerRadius = cellSize * FINDER_OUTER_CORNER_RATIO
    canvas.drawRoundRect(
        offset + (ox + 0.5f) * cellSize,
        offset + (oy + 0.5f) * cellSize,
        offset + (ox + 6.5f) * cellSize,
        offset + (oy + 6.5f) * cellSize,
        outerRadius,
        outerRadius,
        strokePaint,
    )

    val innerRadius = cellSize * FINDER_INNER_CORNER_RATIO
    canvas.drawRoundRect(
        offset + (ox + 2f) * cellSize,
        offset + (oy + 2f) * cellSize,
        offset + (ox + 5f) * cellSize,
        offset + (oy + 5f) * cellSize,
        innerRadius,
        innerRadius,
        fillPaint,
    )
}
