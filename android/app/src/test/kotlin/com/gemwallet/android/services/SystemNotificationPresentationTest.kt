package com.gemwallet.android.services

import com.gemwallet.android.testkit.mockAssetId
import com.gemwallet.android.testkit.mockTransaction
import com.gemwallet.android.testkit.mockTransactionId
import com.gemwallet.android.testkit.mockWalletId
import com.gemwallet.android.ui.R
import com.wallet.core.primitives.Chain
import com.wallet.core.primitives.TransactionDirection
import com.wallet.core.primitives.TransactionState
import com.wallet.core.primitives.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemNotificationPresentationTest {

    @Test
    fun `parses transaction and swap payloads safely`() {
        val txPayload = parseNotificationPayload(
            """{"walletId":"multicoin_0x1","assetId":"base_0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","transactionId":"base_0xabc"}""",
        )
        assertEquals(mockAssetId(Chain.Base, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"), txPayload.assetId)
        assertEquals(mockWalletId("0x1"), txPayload.walletId)
        assertEquals(mockTransactionId(Chain.Base, "0xabc"), txPayload.transactionId)

        val swapPayload = parseNotificationPayload("""{"fromAssetId":"ethereum","toAssetId":"solana"}""")
        assertEquals(mockAssetId(Chain.Ethereum), swapPayload.assetId)
        assertNull(swapPayload.walletId)

        assertEquals(ParsedNotificationPayload(), parseNotificationPayload("not-json"))
        assertEquals(ParsedNotificationPayload(), parseNotificationPayload(null))
    }

    @Test
    fun `cleans Chinese sent notification and appends chain and wallet context`() {
        val fullRecipient = "0x24cF51234567890abcdef1234567890abcd0A655"
        val tx = mockTransaction(
            assetId = mockAssetId(Chain.Base, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"),
            to = fullRecipient,
            type = TransactionType.Transfer,
            state = TransactionState.Confirmed,
            direction = TransactionDirection.Outgoing,
        )
        val content = formatSystemNotification(
            rawTitle = "💸 已发送：\u206811.00 USDC\u2069",
            rawSubtitle = "至\u20680x24cF5...0A655\u2069",
            type = "transaction",
            chainName = "Base",
            walletName = "主钱包",
            transaction = tx,
            fallbackTitle = "Gem Wallet",
        )

        assertEquals(SystemNotificationChannel.Transactions, content.channel)
        assertEquals(NotificationBadgeKind.Send, content.badge)
        assertEquals("已发送 11.00 USDC", content.title)
        assertEquals("至 0x24cF5...0A655 · Base", content.body)
        assertEquals("主钱包 · Base", content.subText)
        assertEquals("至 0x24cF5...0A655\n主钱包 · Base", content.expandedBody)
        assertEquals(R.string.common_details, content.primaryActionTitleRes)
        assertEquals(fullRecipient, content.copyAddress)
    }

    @Test
    fun `cleans swap failed price alert and English notifications`() {
        val swap = formatSystemNotification(
            rawTitle = "🔄 从\u2068ETH\u2069兑换为\u2068USDC\u2069",
            rawSubtitle = "\u20680.5 ETH\u2069 > \u20681,420.50 USDC\u2069",
            type = "transaction",
            chainName = "Ethereum",
            walletName = null,
            transaction = null,
            fallbackTitle = "Gem Wallet",
        )
        assertEquals(NotificationBadgeKind.Swap, swap.badge)
        assertEquals("兑换 ETH → USDC", swap.title)
        assertEquals("0.5 ETH → 1,420.50 USDC · Ethereum", swap.body)
        assertNull(swap.copyAddress)

        val failed = formatSystemNotification(
            rawTitle = "❌ \u2068转账\u2069：失败",
            rawSubtitle = "至\u20680x24cF5...0A655\u2069",
            type = "transaction",
            chainName = "Base",
            walletName = null,
            transaction = null,
            fallbackTitle = "Gem Wallet",
        )
        assertEquals(NotificationBadgeKind.Failed, failed.badge)
        assertEquals("转账失败", failed.title)
        assertEquals("至 0x24cF5...0A655 · Base", failed.body)

        val priceAlert = formatSystemNotification(
            rawTitle = "📉 \u2068Bitcoin (BTC)\u2069 价格提醒",
            rawSubtitle = "价格下跌 \u2068-4.18%\u2069，至 \u2068$91,200\u2069",
            type = "priceAlert",
            chainName = "Bitcoin",
            walletName = null,
            transaction = null,
            fallbackTitle = "Gem Wallet",
        )
        assertEquals(SystemNotificationChannel.PriceAlerts, priceAlert.channel)
        assertEquals(NotificationBadgeKind.PriceDown, priceAlert.badge)
        assertEquals("Bitcoin (BTC) 价格提醒", priceAlert.title)
        assertEquals("价格下跌 -4.18%，至 $91,200", priceAlert.body)

        val englishSend = formatSystemNotification(
            rawTitle = "💸 Sent: \u206811.00 USDC\u2069",
            rawSubtitle = "To \u20680x24cF5...0A655\u2069",
            type = "transaction",
            chainName = "Base",
            walletName = "Main",
            transaction = null,
            fallbackTitle = "Gem Wallet",
        )
        assertEquals("Sent: 11.00 USDC", englishSend.title)
        assertEquals("To 0x24cF5...0A655 · Base", englishSend.body)
        assertEquals("Main · Base", englishSend.subText)
    }

    @Test
    fun `never exposes truncated address for copy action`() {
        val tx = mockTransaction(
            assetId = mockAssetId(Chain.Ethereum),
            from = "0x89aB1...9F120",
            type = TransactionType.Transfer,
            state = TransactionState.Confirmed,
            direction = TransactionDirection.Incoming,
        )
        val content = formatSystemNotification(
            rawTitle = "💰 已收到：1.25 ETH",
            rawSubtitle = "来自0x89aB1...9F120",
            type = "transaction",
            chainName = "Ethereum",
            walletName = null,
            transaction = tx,
            fallbackTitle = "Gem Wallet",
        )

        assertEquals(NotificationBadgeKind.Receive, content.badge)
        assertNull(content.copyAddress)
    }

    @Test
    fun `maps push types to dedicated channels and actions`() {
        assertEquals(SystemNotificationChannel.Transactions, SystemNotificationChannel.fromType("transaction"))
        assertEquals(SystemNotificationChannel.Transactions, SystemNotificationChannel.fromType("fiatTransaction"))
        assertEquals(SystemNotificationChannel.Transactions, SystemNotificationChannel.fromType("swapAsset"))
        assertEquals(SystemNotificationChannel.PriceAlerts, SystemNotificationChannel.fromType("priceAlert"))
        assertEquals(SystemNotificationChannel.Staking, SystemNotificationChannel.fromType("stake"))
        assertEquals(SystemNotificationChannel.Rewards, SystemNotificationChannel.fromType("rewards"))
        assertEquals(SystemNotificationChannel.Support, SystemNotificationChannel.fromType("support"))
        assertEquals(SystemNotificationChannel.General, SystemNotificationChannel.fromType("buyAsset"))
        assertEquals(SystemNotificationChannel.General, SystemNotificationChannel.fromType(null))

        val stake = formatSystemNotification(
            rawTitle = "🎁 质押奖励",
            rawSubtitle = "在 Solana 上有 0.52 质押奖励可领取",
            type = "stake",
            chainName = "Solana",
            walletName = null,
            transaction = null,
            fallbackTitle = "Gem Wallet",
        )
        assertEquals(R.string.transfer_claim_rewards_title, stake.primaryActionTitleRes)
        assertEquals("在 Solana 上有 0.52 质押奖励可领取", stake.body)
    }
}
