package com.gemwallet.android.integration

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemwallet.android.data.services.store.queries.TransactionQuery
import com.gemwallet.android.data.services.store.queries.WalletQuery
import com.gemwallet.android.services.NotificationActionReceiver
import com.gemwallet.android.services.NotificationBadgeKind
import com.gemwallet.android.services.ShowSystemNotificationImpl
import com.gemwallet.android.services.SystemNotificationChannel
import com.gemwallet.android.testkit.mockAssetId
import com.gemwallet.android.testkit.mockTransaction
import com.gemwallet.android.testkit.mockTransactionExtended
import com.gemwallet.android.testkit.mockTransactionId
import com.gemwallet.android.testkit.mockWallet
import com.gemwallet.android.testkit.mockWalletId
import com.gemwallet.android.ui.R
import com.wallet.core.primitives.Chain
import com.wallet.core.primitives.TransactionDirection
import com.wallet.core.primitives.TransactionState
import com.wallet.core.primitives.TransactionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ShowSystemNotificationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val walletQuery = mockk<WalletQuery>()
    private val transactionQuery = mockk<TransactionQuery>()
    private val service = ShowSystemNotificationImpl(context, walletQuery, transactionQuery)

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager.cancelAll()
    }

    @Test
    fun ensureChannels_createsNamedChannelsAndRemovesLegacyDuplicates() {
        notificationManager.createNotificationChannel(
            NotificationChannel("fiatTransaction", "Notifications", NotificationManager.IMPORTANCE_DEFAULT),
        )

        service.ensureChannels()

        val channelsById = notificationManager.notificationChannels.associateBy { it.id }
        assertEquals(context.getString(R.string.activity_title), channelsById[SystemNotificationChannel.Transactions.id]?.name)
        assertEquals(context.getString(R.string.settings_price_alerts_title), channelsById[SystemNotificationChannel.PriceAlerts.id]?.name)
        assertEquals(context.getString(R.string.wallet_stake), channelsById[SystemNotificationChannel.Staking.id]?.name)
        assertEquals(context.getString(R.string.rewards_title), channelsById[SystemNotificationChannel.Rewards.id]?.name)
        assertEquals(context.getString(R.string.settings_support), channelsById[SystemNotificationChannel.Support.id]?.name)
        assertEquals(context.getString(R.string.settings_notifications_title), channelsById[SystemNotificationChannel.General.id]?.name)
        assertNull(channelsById["fiatTransaction"])
    }

    @Test
    fun showNotification_buildsCleanNativeStyleWithLargeIconAndActions() = runBlocking {
        val walletId = mockWalletId("0x1")
        val assetId = mockAssetId(Chain.Base, "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913")
        val transactionId = mockTransactionId(Chain.Base, "0xhash")
        val recipient = "0x24cF51234567890abcdef1234567890abcd0A655"
        every { walletQuery(walletId) } returns flowOf(mockWallet(id = walletId, name = "Main Wallet"))
        every { transactionQuery(walletId, transactionId) } returns flowOf(
            mockTransactionExtended(
                transaction = mockTransaction(
                    id = transactionId,
                    assetId = assetId,
                    to = recipient,
                    type = TransactionType.Transfer,
                    state = TransactionState.Confirmed,
                    direction = TransactionDirection.Outgoing,
                ),
            ),
        )

        val rawData = """{"walletId":"${walletId.id}","assetId":"base_0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913","transactionId":"base_0xhash"}"""
        service.showNotification(
            title = "💸 已发送：\u206811.00 USDC\u2069",
            subtitle = "至\u20680x24cF5...0A655\u2069",
            type = "transaction",
            rawData = rawData,
        )

        val posted = shadowOf(notificationManager).allNotifications
        assertEquals(1, posted.size)
        val notification = posted.single()
        assertEquals(SystemNotificationChannel.Transactions.id, notification.channelId)
        assertEquals(NotificationBadgeKind.Send.accentColor, notification.color)
        assertEquals("已发送 11.00 USDC", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("至 0x24cF5...0A655 · Base", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals("Main Wallet · Base", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        assertEquals("至 0x24cF5...0A655\nMain Wallet · Base", notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        assertNotNull(notification.getLargeIcon())
        assertEquals(
            listOf(context.getString(R.string.common_details), context.getString(R.string.wallet_copy_address)),
            notification.actions.map { it.title.toString() },
        )
    }

    @Test
    fun notificationActionReceiver_copiesFullAddressAndCancelsNotification() {
        val notificationId = 42
        val address = "0x24cF51234567890abcdef1234567890abcd0A655"
        notificationManager.createNotificationChannel(
            NotificationChannel("transaction", "Activity", NotificationManager.IMPORTANCE_HIGH),
        )
        notificationManager.notify(
            notificationId,
            Notification.Builder(context, "transaction").setSmallIcon(R.drawable.ic_gem_notification).setContentTitle("Test").build(),
        )
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)

        val receiver = NotificationActionReceiver()
        receiver.onReceive(context, NotificationActionReceiver.copyAddressIntent(context, address, notificationId))

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(address, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }
}
