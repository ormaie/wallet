package com.gemwallet.android.application.notifications.cases

interface ShowSystemNotification {
    fun ensureChannels()

    suspend fun showNotification(title: String?, subtitle: String?, type: String?, rawData: String? = null)
}
