package com.festpay.app

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.festpay.app.data.AccessibilityRuntime
import com.festpay.app.data.OwnedUssdCommand
import com.festpay.app.data.SleepMode
import com.festpay.app.data.UssdSessionHost
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SleepModeSettingsTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun autoSleepIsOffByDefaultAndTheChoiceIsRemembered() {
        val name = "test_sleep_${UUID.randomUUID()}"
        val c = object : ContextWrapper(base) { override fun getSharedPreferences(ignored: String?, mode: Int) = base.getSharedPreferences(name, mode) }
        try {
            assertFalse(SleepMode.autoSleep(c))
            SleepMode.setAutoSleep(c, true); assertTrue(SleepMode.autoSleep(c))
            SleepMode.setAutoSleep(c, false); assertFalse(SleepMode.autoSleep(c))
        } finally { base.deleteSharedPreferences(name) }
    }

    @Test fun sleepNowReachesTheConnectedServiceHostOnly() {
        var slept = 0
        val host = object : UssdSessionHost {
            override fun connectionAlive() = true
            override fun armInitializedSession(command: OwnedUssdCommand, connectionId: String) = false
            override fun sleepNow() { slept++ }
        }
        try {
            AccessibilityRuntime.sleepNow(); assertEquals("nothing connected", 0, slept)
            AccessibilityRuntime.connection.connect(host)
            AccessibilityRuntime.sleepNow(); assertEquals(1, slept)
        } finally { AccessibilityRuntime.connection.disconnect(host) }
    }
}
