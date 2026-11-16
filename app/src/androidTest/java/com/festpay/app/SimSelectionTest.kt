package com.festpay.app

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.SimSelection
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SimSelectionTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(name: String) = object : ContextWrapper(base) {
        override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode)
    }

    @Test fun theChoiceIsRememberedAndCanBeClearedBackToThePhoneDefault() {
        val name = "test_sim_${UUID.randomUUID()}"; val c = isolated(name)
        try {
            assertNull(SimSelection.chosen(c))
            SimSelection.choose(c, 3); assertEquals(3, SimSelection.chosen(c))
            SimSelection.choose(c, null); assertNull(SimSelection.chosen(c))
        } finally { base.deleteSharedPreferences(name) }
    }

    @Test fun theDialIntentIsTheSameSingleStarNinetyNineWithTheChosenSimOnlyWhenOneResolves() {
        val c = isolated("test_sim_${UUID.randomUUID()}")
        val plain = SimSelection.dialIntent(c) { null }
        assertEquals(Intent.ACTION_CALL, plain.action); assertEquals("*99#", plain.data?.schemeSpecificPart); assertEquals("tel", plain.data?.scheme)
        assertFalse(plain.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE))
        val handle = PhoneAccountHandle(ComponentName("com.android.phone", "Fixture"), "2")
        val withSim = SimSelection.dialIntent(c) { handle }
        assertEquals("*99#", withSim.data?.schemeSpecificPart)
        @Suppress("DEPRECATION") assertEquals(handle, withSim.getParcelableExtra<PhoneAccountHandle>(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE))
    }

    @Test fun withoutAChoiceNoSimIsForcedAndTheLabelSaysPhoneDefault() {
        val c = isolated("test_sim_${UUID.randomUUID()}")
        assertNull(SimSelection.handleFor(c)); assertEquals("Phone default", SimSelection.label(c))
    }
}
