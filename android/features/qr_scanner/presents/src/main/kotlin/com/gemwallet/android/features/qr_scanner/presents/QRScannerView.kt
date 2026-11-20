package com.gemwallet.android.features.qr_scanner.presents

import android.util.Log
import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gemwallet.android.ui.theme.space4
import kotlinx.coroutines.CancellationException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.min

private val QR_ANALYSIS_RESOLUTION = Size(1280, 720)
internal const val FINDER_SCALE = 0.66f
private val FINDER_CORNER_RADIUS = 25.dp
private val FINDER_CORNER_LENGTH = 25.dp
private val FINDER_STROKE_WIDTH = space4
private const val FINDER_DIM_ALPHA = 0.33f
private const val REPEAT_COOLDOWN_NS = 1_500_000_000L

@ExperimentalGetImage
@Composable
fun QRScannerView(onCameraUnavailable: () -> Unit, continuous: Boolean = false, listener: (String) -> Unit) {
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentListener by rememberUpdatedState(listener)
    val preview = remember { androidx.camera.core.Preview.Builder().build() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }
    LaunchedEffect(continuous) {
        try {
            val provider = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                androidx.camera.lifecycle.ProcessCameraProvider.getInstance(localContext).get()
            }
            val mainExecutor = ContextCompat.getMainExecutor(localContext)
            val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                        .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            androidx.camera.core.resolutionselector.ResolutionStrategy(
                                QR_ANALYSIS_RESOLUTION,
                                androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(
                        analysisExecutor,
                        QRCodeAnalyzer(continuous = continuous) { text ->
                            if (!continuous) {
                                imageAnalysis.clearAnalyzer()
                            }
                            mainExecutor.execute { currentListener(text) }
                        },
                    )
                }
            val cameras = provider.availableCameraInfos
            val camera = cameras.firstOrNull { it.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK } ?: cameras.firstOrNull() ?: error("No camera available")
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                camera.cameraSelector,
                preview,
                imageAnalyzer,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Camera preview did not start", error)
            onCameraUnavailable()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(preview, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            FinderView()
        }
    }
}

private class QRCodeAnalyzer(private val continuous: Boolean, val callback: (String) -> Unit) : androidx.camera.core.ImageAnalysis.Analyzer {
    private var lastDelivered: String? = null
    private var lastDeliveredAtNs: Long = 0L

    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        imageProxy.use {
            val plane = it.planes.first()
            val decoded = QRImageDecoder.decode(plane.buffer.toByteArray(), plane.rowStride, it.width, it.height) ?: return
            if (continuous) {
                val now = System.nanoTime()
                if (decoded == lastDelivered && now - lastDeliveredAtNs < REPEAT_COOLDOWN_NS) {
                    return
                }
                lastDelivered = decoded
                lastDeliveredAtNs = now
            }
            callback(decoded)
        }
    }
}

@Composable
private fun FinderView() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boxSize = min(size.width, size.height) * FINDER_SCALE
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerRadius = FINDER_CORNER_RADIUS.toPx()
        val cornerLength = FINDER_CORNER_LENGTH.toPx()
        val cornerDiameter = cornerRadius * 2f

        val cutout = Path().apply {
            addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(cornerRadius)))
        }
        clipPath(cutout, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = FINDER_DIM_ALPHA), topLeft = Offset.Zero, size)
        }

        val brackets = Path().apply {
            moveTo(left, top + cornerRadius)
            arcTo(Rect(left, top, left + cornerDiameter, top + cornerDiameter), 180f, 90f, false)
            moveTo(left + cornerRadius, top)
            lineTo(left + cornerRadius + cornerLength, top)
            moveTo(left, top + cornerRadius)
            lineTo(left, top + cornerRadius + cornerLength)

            moveTo(right - cornerRadius, top)
            arcTo(Rect(right - cornerDiameter, top, right, top + cornerDiameter), 270f, 90f, false)
            moveTo(right - cornerRadius - cornerLength, top)
            lineTo(right - cornerRadius, top)
            moveTo(right, top + cornerRadius)
            lineTo(right, top + cornerRadius + cornerLength)

            moveTo(right, bottom - cornerRadius)
            arcTo(Rect(right - cornerDiameter, bottom - cornerDiameter, right, bottom), 0f, 90f, false)
            moveTo(right - cornerRadius - cornerLength, bottom)
            lineTo(right - cornerRadius, bottom)
            moveTo(right, bottom - cornerRadius)
            lineTo(right, bottom - cornerRadius - cornerLength)

            moveTo(left + cornerRadius, bottom)
            arcTo(Rect(left, bottom - cornerDiameter, left + cornerDiameter, bottom), 90f, 90f, false)
            moveTo(left + cornerRadius, bottom)
            lineTo(left + cornerRadius + cornerLength, bottom)
            moveTo(left, bottom - cornerRadius)
            lineTo(left, bottom - cornerRadius - cornerLength)
        }
        drawPath(
            path = brackets,
            color = Color.White,
            style = Stroke(width = FINDER_STROKE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    return ByteArray(remaining()).also {
        get(it)
    }
}

private const val TAG = "QRScanner"
