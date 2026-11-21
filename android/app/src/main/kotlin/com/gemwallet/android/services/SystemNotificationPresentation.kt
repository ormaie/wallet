package com.gemwallet.android.services

import android.app.NotificationManager
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.gemwallet.android.ext.toAssetId
import com.gemwallet.android.ui.R
import com.wallet.core.primitives.AssetId
import com.wallet.core.primitives.Transaction
import com.wallet.core.primitives.TransactionDirection
import com.wallet.core.primitives.TransactionId
import com.wallet.core.primitives.TransactionState
import com.wallet.core.primitives.TransactionType
import com.wallet.core.primitives.WalletId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val notificationPayloadJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal enum class SystemNotificationChannel(
    val id: String,
    @param:StringRes val nameRes: Int,
    val importance: Int,
    val priority: Int,
    val category: String,
) {
    Transactions(
        id = "transaction",
        nameRes = R.string.activity_title,
        importance = NotificationManager.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        category = NotificationCompat.CATEGORY_STATUS,
    ),
    PriceAlerts(
        id = "priceAlert",
        nameRes = R.string.settings_price_alerts_title,
        importance = NotificationManager.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        category = NotificationCompat.CATEGORY_STATUS,
    ),
    Staking(
        id = "stake",
        nameRes = R.string.wallet_stake,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
        category = NotificationCompat.CATEGORY_STATUS,
    ),
    Rewards(
        id = "rewards",
        nameRes = R.string.rewards_title,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
        category = NotificationCompat.CATEGORY_PROMO,
    ),
    Support(
        id = "support",
        nameRes = R.string.settings_support,
        importance = NotificationManager.IMPORTANCE_HIGH,
        priority = NotificationCompat.PRIORITY_HIGH,
        category = NotificationCompat.CATEGORY_MESSAGE,
    ),
    General(
        id = "default",
        nameRes = R.string.settings_notifications_title,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        priority = NotificationCompat.PRIORITY_DEFAULT,
        category = NotificationCompat.CATEGORY_STATUS,
    ),
    ;

    val groupKey: String
        get() = "com.gemwallet.android.notifications.$id"

    companion object {
        val legacyChannelIds: List<String> = listOf(
            "fiatTransaction",
            "swapAsset",
            "asset",
            "buyAsset",
            "test",
        )

        fun fromType(type: String?): SystemNotificationChannel = when (type) {
            "transaction", "fiatTransaction", "swapAsset" -> Transactions
            "priceAlert" -> PriceAlerts
            "stake" -> Staking
            "rewards" -> Rewards
            "support" -> Support
            else -> General
        }
    }
}

internal enum class NotificationBadgeKind(@param:ColorInt val accentColor: Int) {
    Send(0xFF2D5BE6.toInt()),
    Receive(0xFF1B9A6C.toInt()),
    Swap(0xFF6C5CE7.toInt()),
    PriceUp(0xFF1B9A6C.toInt()),
    PriceDown(0xFFF84E4E.toInt()),
    Failed(0xFFF84E4E.toInt()),
    Reward(0xFFD97706.toInt()),
    Stake(0xFF2D5BE6.toInt()),
    Buy(0xFF2D5BE6.toInt()),
    Support(0xFF2D5BE6.toInt()),
    General(0xFF2D5BE6.toInt()),
}

internal data class ParsedNotificationPayload(
    val assetId: AssetId? = null,
    val walletId: WalletId? = null,
    val transactionId: TransactionId? = null,
)

internal data class SystemNotificationContent(
    val channel: SystemNotificationChannel,
    val badge: NotificationBadgeKind,
    val title: String,
    val body: String?,
    val expandedBody: String?,
    val subText: String?,
    @param:StringRes val primaryActionTitleRes: Int?,
    val copyAddress: String?,
)

internal fun parseNotificationPayload(rawData: String?): ParsedNotificationPayload {
    if (rawData.isNullOrBlank()) return ParsedNotificationPayload()
    val jsonObject = runCatching { notificationPayloadJson.parseToJsonElement(rawData) as? JsonObject }.getOrNull()
        ?: return ParsedNotificationPayload()
    val assetId = (jsonObject["assetId"] ?: jsonObject["fromAssetId"])?.jsonPrimitive?.contentOrNull?.toAssetId()
    val walletId = jsonObject["walletId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let(::WalletId)
    val transactionId = jsonObject["transactionId"]?.jsonPrimitive?.contentOrNull?.let(TransactionId::from)
    return ParsedNotificationPayload(
        assetId = assetId,
        walletId = walletId,
        transactionId = transactionId,
    )
}

internal fun formatSystemNotification(
    rawTitle: String?,
    rawSubtitle: String?,
    type: String?,
    chainName: String?,
    walletName: String?,
    transaction: Transaction?,
    fallbackTitle: String,
): SystemNotificationContent {
    val channel = SystemNotificationChannel.fromType(type)
    val badge = resolveBadgeKind(type = type, rawTitle = rawTitle, rawSubtitle = rawSubtitle, transaction = transaction)
    val cleanTitle = polishNotificationTitle(rawTitle)?.ifBlank { null } ?: fallbackTitle
    val cleanSubtitle = polishNotificationBody(rawSubtitle)?.ifBlank { null }
    val normalizedWalletName = walletName?.trim()?.takeIf { it.isNotEmpty() }

    val collapsedBody = composeCollapsedBody(
        title = cleanTitle,
        body = cleanSubtitle,
        chainName = chainName,
    )
    val subText = listOfNotNull(normalizedWalletName, chainName).joinToString(" · ").ifEmpty { null }
    val expandedBody = composeExpandedBody(
        body = cleanSubtitle,
        collapsedBody = collapsedBody,
        subText = subText,
        walletName = normalizedWalletName,
    )
    val copyAddress = resolveCopyAddress(transaction)
    val primaryActionTitleRes = resolvePrimaryActionTitleRes(type)

    return SystemNotificationContent(
        channel = channel,
        badge = badge,
        title = cleanTitle,
        body = collapsedBody,
        expandedBody = expandedBody,
        subText = subText,
        primaryActionTitleRes = primaryActionTitleRes,
        copyAddress = copyAddress,
    )
}

private fun resolveBadgeKind(
    type: String?,
    rawTitle: String?,
    rawSubtitle: String?,
    transaction: Transaction?,
): NotificationBadgeKind {
    if (transaction != null) {
        when (transaction.state) {
            TransactionState.Failed,
            TransactionState.Reverted,
            -> return NotificationBadgeKind.Failed

            TransactionState.Pending,
            TransactionState.Confirmed,
            TransactionState.InTransit,
            TransactionState.Refunded,
            -> Unit
        }
        return when (transaction.type) {
            TransactionType.Transfer,
            TransactionType.TransferNFT,
            TransactionType.SmartContractCall,
            -> when (transaction.direction) {
                TransactionDirection.Incoming -> NotificationBadgeKind.Receive
                TransactionDirection.Outgoing,
                TransactionDirection.SelfTransfer,
                -> NotificationBadgeKind.Send
            }

            TransactionType.Swap -> NotificationBadgeKind.Swap
            TransactionType.StakeRewards -> NotificationBadgeKind.Reward

            TransactionType.TokenApproval,
            TransactionType.StakeDelegate,
            TransactionType.StakeUndelegate,
            TransactionType.StakeRedelegate,
            TransactionType.StakeWithdraw,
            TransactionType.StakeFreeze,
            TransactionType.StakeUnfreeze,
            TransactionType.AssetActivation,
            TransactionType.EarnDeposit,
            TransactionType.EarnWithdraw,
            -> NotificationBadgeKind.Stake

            TransactionType.PerpetualOpenPosition,
            TransactionType.PerpetualClosePosition,
            TransactionType.PerpetualModifyPosition,
            -> badgeFromSignalText(rawTitle, rawSubtitle) ?: NotificationBadgeKind.General
        }
    }

    val title = rawTitle.orEmpty()
    if (title.contains("❌")) return NotificationBadgeKind.Failed

    return when (type) {
        "priceAlert" -> when {
            title.contains("📉") -> NotificationBadgeKind.PriceDown
            else -> NotificationBadgeKind.PriceUp
        }

        "swapAsset" -> NotificationBadgeKind.Swap
        "stake" -> NotificationBadgeKind.Reward
        "rewards" -> NotificationBadgeKind.Reward
        "support" -> NotificationBadgeKind.Support
        "buyAsset" -> NotificationBadgeKind.Buy
        "fiatTransaction" -> if (title.contains("💰")) NotificationBadgeKind.Receive else NotificationBadgeKind.Buy
        else -> badgeFromSignalText(rawTitle, rawSubtitle) ?: NotificationBadgeKind.General
    }
}

private fun badgeFromSignalText(rawTitle: String?, rawSubtitle: String?): NotificationBadgeKind? {
    val title = rawTitle.orEmpty()
    val subtitle = rawSubtitle.orEmpty()
    return when {
        title.contains("❌") -> NotificationBadgeKind.Failed
        title.contains("💸") -> NotificationBadgeKind.Send
        title.contains("💰") -> NotificationBadgeKind.Receive
        title.contains("🔄") -> NotificationBadgeKind.Swap
        title.contains("🎁") || title.contains("💎") -> NotificationBadgeKind.Reward
        title.contains("🔒") || title.contains("🔓") || title.contains("✅") -> NotificationBadgeKind.Stake
        title.contains("📉") || subtitle.contains("😕") -> NotificationBadgeKind.PriceDown
        title.contains("📈") || title.contains("🔥") || title.contains("🎯") -> NotificationBadgeKind.PriceUp
        title.contains("🚀") -> NotificationBadgeKind.Buy
        else -> null
    }
}

private fun resolvePrimaryActionTitleRes(type: String?): Int? = when (type) {
    "transaction", "fiatTransaction", "priceAlert", "asset", "buyAsset", "swapAsset", "support" -> R.string.common_details
    "stake" -> R.string.transfer_claim_rewards_title
    "rewards" -> R.string.rewards_title
    else -> null
}

private fun resolveCopyAddress(transaction: Transaction?): String? {
    if (transaction == null) return null
    val candidate = when (transaction.type) {
        TransactionType.Transfer,
        TransactionType.TransferNFT,
        TransactionType.TokenApproval,
        TransactionType.SmartContractCall,
        TransactionType.StakeDelegate,
        TransactionType.StakeUndelegate,
        TransactionType.StakeRedelegate,
        TransactionType.StakeWithdraw,
        TransactionType.EarnDeposit,
        TransactionType.EarnWithdraw,
        -> when (transaction.direction) {
            TransactionDirection.Incoming -> transaction.from
            TransactionDirection.Outgoing,
            TransactionDirection.SelfTransfer,
            -> transaction.to
        }

        TransactionType.Swap,
        TransactionType.StakeRewards,
        TransactionType.StakeFreeze,
        TransactionType.StakeUnfreeze,
        TransactionType.AssetActivation,
        TransactionType.PerpetualOpenPosition,
        TransactionType.PerpetualClosePosition,
        TransactionType.PerpetualModifyPosition,
        -> null
    }
    return candidate
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.contains("...") && !it.contains("…") }
}

private fun composeCollapsedBody(title: String, body: String?, chainName: String?): String? {
    if (body == null) return chainName
    if (chainName == null) return body
    val alreadyNamed = body.contains(chainName, ignoreCase = true) || title.contains(chainName, ignoreCase = true)
    return if (alreadyNamed) body else "$body · $chainName"
}

private fun composeExpandedBody(
    body: String?,
    collapsedBody: String?,
    subText: String?,
    walletName: String?,
): String? {
    if (collapsedBody == null) return subText
    if (walletName == null || subText == null) return collapsedBody
    val primaryLine = body ?: collapsedBody
    return if (primaryLine.equals(subText, ignoreCase = true)) primaryLine else "$primaryLine\n$subText"
}

internal fun polishNotificationTitle(rawTitle: String?): String? {
    if (rawTitle == null) return null
    var cleaned = stripDecorations(rawTitle)
    cleaned = CJK_SWAP_REGEX.replace(cleaned) { match ->
        "兑换 ${match.groupValues[1].trim()} → ${match.groupValues[2].trim()}"
    }
    cleaned = CJK_FAILED_COLON_REGEX.replace(cleaned, "$1失败")
    cleaned = CJK_NFT_COLON_REGEX.replace(cleaned, "$1 · ")
    cleaned = CJK_COLON_VALUE_REGEX.replace(cleaned, "$1 ")
    cleaned = CJK_BUY_PREFIX_REGEX.replace(cleaned, "购买 $1")
    cleaned = CJK_ATH_SUFFIX_REGEX.replace(cleaned, "$1 历史最高")
    return MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
}

internal fun polishNotificationBody(rawBody: String?): String? {
    if (rawBody == null) return null
    var cleaned = stripDecorations(rawBody)
    cleaned = CJK_DIRECTION_PREFIX_REGEX.replace(cleaned, "$1 $2")
    cleaned = SWAP_ARROW_REGEX.replace(cleaned, " → ")
    return MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
}

private fun stripDecorations(input: String): String {
    var result = input.replace("\u2068", "").replace("\u2069", "")
    for (token in DECORATIVE_TOKENS) {
        result = result.replace(token, "")
    }
    return result.trim()
}

private val DECORATIVE_TOKENS = listOf(
    "💸",
    "💰",
    "🔄",
    "✅",
    "🔒",
    "🔓",
    "🎁",
    "🖼️",
    "🖼",
    "📈",
    "📉",
    "🔥",
    "🎯",
    "🚀",
    "💎",
    "❌",
    "😕",
    "\uFE0F",
    "\uFE0E",
)

private val CJK_SWAP_REGEX = Regex("^从\\s*(.+?)\\s*兑换为\\s*(.+)$")
private val CJK_FAILED_COLON_REGEX = Regex("^([\\p{IsHan}A-Za-z0-9\\s]+?)\\s*：\\s*失败$")
private val CJK_NFT_COLON_REGEX = Regex("^(已发送 NFT|收到 NFT)\\s*：\\s*")
private val CJK_COLON_VALUE_REGEX = Regex("^([\\p{IsHan}]+)\\s*：\\s*")
private val CJK_BUY_PREFIX_REGEX = Regex("^购买([A-Za-z0-9])")
private val CJK_ATH_SUFFIX_REGEX = Regex("([A-Za-z0-9)])历史最高$")
private val CJK_DIRECTION_PREFIX_REGEX = Regex("^(至|来自)([A-Za-z0-9])")
private val SWAP_ARROW_REGEX = Regex("\\s+>\\s+")
private val MULTI_SPACE_REGEX = Regex("\\s{2,}")
