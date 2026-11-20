package com.gemwallet.android.data.coordinators.update

import uniffi.gemstone.GemAppUpdateOffer
import uniffi.gemstone.GemAppUpdateServiceInterface
import uniffi.gemstone.PlatformStore
import uniffi.gemstone.Release

class DisabledAppUpdateService(delegate: GemAppUpdateServiceInterface) : GemAppUpdateServiceInterface by delegate {

    override suspend fun newest(store: PlatformStore, currentVersion: String): Release? = null

    override suspend fun check(store: PlatformStore, currentVersion: String): GemAppUpdateOffer? = null
}
