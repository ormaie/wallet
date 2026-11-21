package com.gemwallet.android.services

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.gemwallet.android.ui.R

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_ADDRESS) return
        val address = intent.getStringExtra(EXTRA_ADDRESS)?.takeIf { it.isNotBlank() } ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.wallet_copy_address), address))

        if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.common_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_COPY_ADDRESS = "com.gemwallet.android.action.NOTIFICATION_COPY_ADDRESS"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun copyAddressIntent(context: Context, address: String, notificationId: Int): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_COPY_ADDRESS)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }
}
