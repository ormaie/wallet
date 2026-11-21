package com.gemwallet.android.services

import android.Manifest
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gemwallet.android.MainActivity
import com.gemwallet.android.application.notifications.cases.ShowSystemNotification
import com.gemwallet.android.data.services.store.queries.TransactionQuery
import com.gemwallet.android.data.services.store.queries.WalletQuery
import com.gemwallet.android.ext.networkName
import com.gemwallet.android.ext.runCatchingCancellable
import com.gemwallet.android.putNotificationPayload
import com.gemwallet.android.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ShowSystemNotificationImpl @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val walletQuery: WalletQuery,
    private val transactionQuery: TransactionQuery,
) : ShowSystemNotification {

    override fun ensureChannels() {
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        for (channel in SystemNotificationChannel.entries) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channel.id,
                    applicationContext.getString(channel.nameRes),
                    channel.importance,
                ),
            )
        }
        for (legacyId in SystemNotificationChannel.legacyChannelIds) {
            notificationManager.deleteNotificationChannel(legacyId)
        }
    }

    override suspend fun showNotification(title: String?, subtitle: String?, type: String?, rawData: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannels()

        val payload = parseNotificationPayload(rawData)
        val (walletName, transaction) = withContext(Dispatchers.IO) {
            coroutineScope {
                val walletDeferred = payload.walletId?.let { walletId ->
                    async { runCatchingCancellable { walletQuery(walletId).firstOrNull()?.name }.getOrNull() }
                }
                val transactionDeferred = if (payload.walletId != null && payload.transactionId != null) {
                    async { runCatchingCancellable { transactionQuery(payload.walletId, payload.transactionId).firstOrNull()?.transaction }.getOrNull() }
                } else {
                    null
                }
                walletDeferred?.await() to transactionDeferred?.await()
            }
        }

        val content = formatSystemNotification(
            rawTitle = title,
            rawSubtitle = subtitle,
            type = type,
            chainName = payload.assetId?.chain?.networkName(),
            walletName = walletName,
            transaction = transaction,
            fallbackTitle = applicationContext.getString(R.string.application_name),
        )
        val largeIcon = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                SystemNotificationIconRenderer.render(
                    context = applicationContext,
                    assetId = payload.assetId,
                    badge = content.badge,
                )
            }.getOrNull()
        }

        val notificationId = "$type:$rawData".hashCode()
        val contentIntent = Intent(applicationContext, MainActivity::class.java)
            .putNotificationPayload(type = type, rawData = rawData)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(applicationContext, content.channel.id)
            .setSmallIcon(R.drawable.ic_gem_notification)
            .setColor(content.badge.accentColor)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setSubText(content.subText)
            .setLargeIcon(largeIcon)
            .setPriority(content.channel.priority)
            .setCategory(content.channel.category)
            .setGroup(content.channel.groupKey)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        content.expandedBody?.let { expanded ->
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
        }

        content.primaryActionTitleRes?.let { actionTitleRes ->
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    applicationContext.getString(actionTitleRes),
                    contentPendingIntent,
                ).build(),
            )
        }

        content.copyAddress?.let { address ->
            val copyIntent = NotificationActionReceiver.copyAddressIntent(
                context = applicationContext,
                address = address,
                notificationId = notificationId,
            )
            val copyPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                notificationId xor COPY_ACTION_REQUEST_SALT,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    applicationContext.getString(R.string.wallet_copy_address),
                    copyPendingIntent,
                ).build(),
            )
        }

        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
    }

    companion object {
        private const val COPY_ACTION_REQUEST_SALT = 0x434F5059
    }
}
