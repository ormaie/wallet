package com.gemwallet.android.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.gemwallet.android.ext.runCatchingCancellable
import com.gemwallet.android.ext.toChain
import com.gemwallet.android.ext.toGem
import com.gemwallet.android.ui.R
import com.gemwallet.android.ui.components.image.iconResource
import com.wallet.core.primitives.Asset
import com.wallet.core.primitives.AssetId
import com.wallet.core.primitives.AssetType
import kotlinx.coroutines.withTimeoutOrNull
import uniffi.gemstone.GemAssetIconImage
import uniffi.gemstone.assetText

internal object SystemNotificationIconRenderer {

    private const val CANVAS_SIZE_DP = 48f
    private const val BADGE_RADIUS_RATIO = 0.20f
    private const val BADGE_CUTOUT_DP = 1.75f
    private const val REMOTE_ICON_TIMEOUT_MS = 1_500L

    suspend fun render(context: Context, assetId: AssetId?, badge: NotificationBadgeKind): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val sizePx = (CANVAS_SIZE_DP * density).toInt().coerceAtLeast(64)
        val baseBitmap = if (assetId != null) {
            loadAssetBitmap(context, assetId, sizePx) ?: renderEmblemBitmap(context, badge, sizePx)
        } else {
            renderEmblemBitmap(context, badge, sizePx)
        }
        return if (assetId != null && hasCornerBadge(badge)) {
            composeWithCornerBadge(baseBitmap, badge, sizePx, density)
        } else {
            baseBitmap
        }
    }

    private suspend fun loadAssetBitmap(context: Context, assetId: AssetId, sizePx: Int): Bitmap? {
        val icon = runCatching {
            val asset = Asset(
                id = assetId,
                name = "",
                symbol = "",
                decimals = 0u,
                type = if (assetId.tokenId.isNullOrEmpty()) AssetType.NATIVE else AssetType.TOKEN,
            )
            assetText(asset.toGem()).icon
        }.getOrNull()

        val rawBitmap = when (val image = icon?.image) {
            is GemAssetIconImage.Local -> image.chain.toChain().iconResource()?.let { renderDrawableBitmap(context, it, sizePx) }
            is GemAssetIconImage.LocalToken -> renderDrawableBitmap(context, image.token.iconResource(), sizePx)
            is GemAssetIconImage.Remote -> fetchRemoteBitmap(context, image.url, sizePx)
                ?: assetId.chain.iconResource()?.let { renderDrawableBitmap(context, it, sizePx) }
            null -> assetId.chain.iconResource()?.let { renderDrawableBitmap(context, it, sizePx) }
        } ?: return null

        return clipToCircle(rawBitmap, sizePx)
    }

    private suspend fun fetchRemoteBitmap(context: Context, url: String, sizePx: Int): Bitmap? = withTimeoutOrNull(REMOTE_ICON_TIMEOUT_MS) {
        runCatchingCancellable {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(sizePx, sizePx)
                .allowHardware(false)
                .build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    }

    private fun renderDrawableBitmap(context: Context, @DrawableRes resId: Int, sizePx: Int): Bitmap? {
        val drawable: Drawable = ContextCompat.getDrawable(context, resId) ?: return null
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun clipToCircle(source: Bitmap, sizePx: Int): Bitmap {
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, null, RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), paint)
        return output
    }

    private fun renderEmblemBitmap(context: Context, badge: NotificationBadgeKind, sizePx: Int): Bitmap {
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)
        val center = sizePx / 2f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badge.accentColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center, fillPaint)
        when (badge) {
            NotificationBadgeKind.General,
            NotificationBadgeKind.Support,
            -> {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_gem_notification)
                if (drawable != null) {
                    val inset = (sizePx * 0.24f).toInt()
                    drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
                    drawable.draw(canvas)
                } else {
                    drawBadgeGlyph(canvas, badge, center, center, sizePx * 0.34f)
                }
            }

            else -> drawBadgeGlyph(canvas, badge, center, center, sizePx * 0.34f)
        }
        return output
    }

    private fun composeWithCornerBadge(base: Bitmap, badge: NotificationBadgeKind, sizePx: Int, density: Float): Bitmap {
        val output = createBitmap(sizePx, sizePx)
        val canvas = Canvas(output)
        canvas.drawBitmap(base, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))

        val badgeRadius = sizePx * BADGE_RADIUS_RATIO
        val cutoutPadding = BADGE_CUTOUT_DP * density
        val badgeCenterX = sizePx - badgeRadius
        val badgeCenterY = sizePx - badgeRadius

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius + cutoutPadding, clearPaint)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badge.accentColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(badgeCenterX, badgeCenterY, badgeRadius, circlePaint)
        drawBadgeGlyph(canvas, badge, badgeCenterX, badgeCenterY, badgeRadius)
        return output
    }

    private fun hasCornerBadge(badge: NotificationBadgeKind): Boolean = when (badge) {
        NotificationBadgeKind.Send,
        NotificationBadgeKind.Receive,
        NotificationBadgeKind.Swap,
        NotificationBadgeKind.PriceUp,
        NotificationBadgeKind.PriceDown,
        NotificationBadgeKind.Failed,
        NotificationBadgeKind.Reward,
        NotificationBadgeKind.Stake,
        NotificationBadgeKind.Buy,
        -> true

        NotificationBadgeKind.Support,
        NotificationBadgeKind.General,
        -> false
    }

    private fun drawBadgeGlyph(canvas: Canvas, badge: NotificationBadgeKind, cx: Float, cy: Float, radius: Float) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (radius * 0.22f).coerceAtLeast(1.8f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val d = radius * 0.44f
        val path = Path()
        when (badge) {
            NotificationBadgeKind.Send -> {
                path.moveTo(cx - d, cy + d)
                path.lineTo(cx + d, cy - d)
                path.moveTo(cx - d * 0.1f, cy - d)
                path.lineTo(cx + d, cy - d)
                path.lineTo(cx + d, cy + d * 0.1f)
            }

            NotificationBadgeKind.Receive -> {
                path.moveTo(cx + d, cy - d)
                path.lineTo(cx - d, cy + d)
                path.moveTo(cx + d * 0.1f, cy + d)
                path.lineTo(cx - d, cy + d)
                path.lineTo(cx - d, cy - d * 0.1f)
            }

            NotificationBadgeKind.Swap -> {
                val yOffset = d * 0.48f
                path.moveTo(cx - d, cy - yOffset)
                path.lineTo(cx + d, cy - yOffset)
                path.lineTo(cx + d * 0.45f, cy - yOffset - d * 0.45f)
                path.moveTo(cx + d, cy + yOffset)
                path.lineTo(cx - d, cy + yOffset)
                path.lineTo(cx - d * 0.45f, cy + yOffset + d * 0.45f)
            }

            NotificationBadgeKind.PriceUp -> {
                path.moveTo(cx - d, cy + d * 0.65f)
                path.lineTo(cx - d * 0.15f, cy - d * 0.15f)
                path.lineTo(cx + d * 0.25f, cy + d * 0.25f)
                path.lineTo(cx + d, cy - d * 0.65f)
                path.moveTo(cx + d * 0.3f, cy - d * 0.65f)
                path.lineTo(cx + d, cy - d * 0.65f)
                path.lineTo(cx + d, cy + d * 0.05f)
            }

            NotificationBadgeKind.PriceDown -> {
                path.moveTo(cx - d, cy - d * 0.65f)
                path.lineTo(cx - d * 0.15f, cy + d * 0.15f)
                path.lineTo(cx + d * 0.25f, cy - d * 0.25f)
                path.lineTo(cx + d, cy + d * 0.65f)
                path.moveTo(cx + d * 0.3f, cy + d * 0.65f)
                path.lineTo(cx + d, cy + d * 0.65f)
                path.lineTo(cx + d, cy - d * 0.05f)
            }

            NotificationBadgeKind.Failed -> {
                val cross = d * 0.85f
                path.moveTo(cx - cross, cy - cross)
                path.lineTo(cx + cross, cy + cross)
                path.moveTo(cx + cross, cy - cross)
                path.lineTo(cx - cross, cy + cross)
            }

            NotificationBadgeKind.Reward -> {
                path.moveTo(cx, cy - d)
                path.lineTo(cx + d * 0.3f, cy - d * 0.3f)
                path.lineTo(cx + d, cy)
                path.lineTo(cx + d * 0.3f, cy + d * 0.3f)
                path.lineTo(cx, cy + d)
                path.lineTo(cx - d * 0.3f, cy + d * 0.3f)
                path.lineTo(cx - d, cy)
                path.lineTo(cx - d * 0.3f, cy - d * 0.3f)
                path.close()
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawPath(path, fillPaint)
                return
            }

            NotificationBadgeKind.Stake -> {
                path.moveTo(cx - d * 0.9f, cy + d * 0.05f)
                path.lineTo(cx - d * 0.2f, cy + d * 0.75f)
                path.lineTo(cx + d * 0.95f, cy - d * 0.65f)
            }

            NotificationBadgeKind.Buy -> {
                path.moveTo(cx - d, cy)
                path.lineTo(cx + d, cy)
                path.moveTo(cx, cy - d)
                path.lineTo(cx, cy + d)
            }

            NotificationBadgeKind.Support,
            NotificationBadgeKind.General,
            -> return
        }
        canvas.drawPath(path, strokePaint)
    }
}
