package com.festpay.app

import com.festpay.app.data.TransactionPresentation
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TransactionPresentationTest {
    @Test fun `persisted instant is displayed in the requested timezone`() {
        assertEquals("02 Nov 2026, 21:00:00 IST", TransactionPresentation.timestamp(
            "2026-11-02T15:30:00Z", ZoneId.of("Asia/Kolkata")))
    }

    @Test fun `missing legacy timestamp is never replaced by the current time`() {
        for (value in listOf("", "Today, Just now", "invalid")) {
            assertEquals("Timestamp unavailable for this older record", TransactionPresentation.timestamp(value))
        }
    }
}
