package com.festpay.app

import com.festpay.app.data.SleepMode
import org.junit.Assert.*
import org.junit.Test

class SleepModeTest {
    @Test fun sleepIsRefusedWhileACarrierSessionIsRunningAndWhenThereIsNothingToSwitchOff() {
        assertEquals(SleepMode.Refusal.SESSION_ACTIVE, SleepMode.canSleepNow(sessionActive = true, serviceRunning = true))
        assertEquals(SleepMode.Refusal.SESSION_ACTIVE, SleepMode.canSleepNow(sessionActive = true, serviceRunning = false))
        assertEquals(SleepMode.Refusal.NOT_RUNNING, SleepMode.canSleepNow(sessionActive = false, serviceRunning = false))
        assertEquals(SleepMode.Refusal.NONE, SleepMode.canSleepNow(sessionActive = false, serviceRunning = true))
    }
}
