package com.gemwallet.android.data.coordinators.update

import com.gemwallet.android.data.coordinators.di.UpdateModule
import com.gemwallet.android.testkit.mockBuildInfo
import com.gemwallet.android.testkit.mockGemAppUpdateOffer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.gemstone.GemAppUpdateService
import uniffi.gemstone.GemAppUpdateServiceInterface
import uniffi.gemstone.PlatformStore

class DisabledAppUpdateServiceTest {

    @Test
    fun `check returns null without querying delegate`() = runTest {
        val delegate = mockk<GemAppUpdateServiceInterface>()
        val service = DisabledAppUpdateService(delegate)

        assertNull(service.check(PlatformStore.APK_UNIVERSAL, "2.114.100"))
        coVerify(exactly = 0) { delegate.check(any(), any()) }
    }

    @Test
    fun `newest returns null without querying delegate`() = runTest {
        val delegate = mockk<GemAppUpdateServiceInterface>()
        val service = DisabledAppUpdateService(delegate)

        assertNull(service.newest(PlatformStore.GOOGLE_PLAY, "2.114.100"))
        coVerify(exactly = 0) { delegate.newest(any(), any()) }
    }

    @Test
    fun `version comparison and skip delegate to underlying service`() {
        val offer = mockGemAppUpdateOffer(version = "2.115.0")
        val delegate = mockk<GemAppUpdateServiceInterface> {
            every { isVersionHigher("2.115.0", "2.114.100") } returns true
            every { skip(offer) } returns Unit
        }
        val service = DisabledAppUpdateService(delegate)

        assertTrue(service.isVersionHigher("2.115.0", "2.114.100"))
        service.skip(offer)

        verify(exactly = 1) { delegate.isVersionHigher("2.115.0", "2.114.100") }
        verify(exactly = 1) { delegate.skip(offer) }
    }

    @Test
    fun `module wires coordinator with disabled update checks`() = runTest {
        val delegate = mockk<GemAppUpdateService> {
            coEvery { check(any(), any()) } returns mockGemAppUpdateOffer(version = "2.115.0")
        }
        val service = UpdateModule.provideAppUpdateServiceInterface(delegate)
        val coordinator = UpdateModule.provideAppUpdateCoordinator(service, mockBuildInfo())

        assertNull(coordinator.syncAppUpdate())
        assertNull(coordinator.observeAppUpdateOffer().first())
        coVerify(exactly = 0) { delegate.check(any(), any()) }
    }
}
