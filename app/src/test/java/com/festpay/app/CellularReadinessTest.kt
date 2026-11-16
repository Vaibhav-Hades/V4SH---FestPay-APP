package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class CellularReadinessTest {
    @Test fun onlyKnownUnavailableConditionsBlockLaunch() {
        assertNotNull(CellularReadiness(false, false, SimReadiness.ABSENT, null).launchBlock)
        assertNotNull(CellularReadiness(true, true, SimReadiness.READY, "Operator").launchBlock)
        assertNotNull(CellularReadiness(true, false, SimReadiness.ABSENT, null).launchBlock)
        assertNotNull(CellularReadiness(true, false, SimReadiness.LOCKED, null).launchBlock)
        assertNull(CellularReadiness(true, null, SimReadiness.CANNOT_VERIFY, null).launchBlock)
        assertNull(CellularReadiness(true, false, SimReadiness.READY, "Operator").launchBlock)
    }
}
