package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class Phase7Test {
    @Test fun equalSharesAddUpExactlyAndGiveLeftoverPaiseToTheFirstPeople() {
        val shares = BillSplit.equalShares(BigDecimal("100.00"), 3)
        assertEquals(listOf("33.34", "33.33", "33.33"), shares.map { it.toPlainString() })
        assertEquals(0, BillSplit.sum(shares).compareTo(BigDecimal("100.00")))
        assertEquals(listOf("50.00", "50.00"), BillSplit.equalShares(BigDecimal("100"), 2).map { it.toPlainString() })
        assertEquals(listOf("0.01", "0.00", "0.00"), BillSplit.equalShares(BigDecimal("0.01"), 3).map { it.toPlainString() })
        assertEquals(0, BillSplit.sum(BillSplit.equalShares(BigDecimal("1234.57"), 7)).compareTo(BigDecimal("1234.57")))
    }

    @Test fun splitAmountsMustBeValidPaymentAmounts() {
        assertNotNull(BillSplit.amountText("12.5")); assertNull(BillSplit.amountText("abc")); assertNull(BillSplit.amountText("0"))
    }

    @Test fun spokenMessageSaysAmountAndOnlyTheNameWhenGiven() {
        assertEquals("Payment of 10 rupees successful.", SpokenConfirmation.message("10", null, "en"))
        assertEquals("Payment of 10 rupees 50 paise to Demo Shop successful.", SpokenConfirmation.message("10.50", "Demo Shop", "en"))
        assertEquals("Payment of 10 rupees 5 paise successful.", SpokenConfirmation.message("10.05", null, "en"))
        assertTrue(SpokenConfirmation.message("10", null, "hi")!!.contains("10 रुपये"))
        assertTrue(SpokenConfirmation.message("10", null, "te")!!.contains("10 రూపాయలు"))
        assertEquals("falls back to English", SpokenConfirmation.message("7", null, "fr"), "Payment of 7 rupees successful.")
        assertNull(SpokenConfirmation.message("abc", "X", "en"))
    }

    @Test fun carrierReplyIsClassifiedPlainly() {
        val menu = "Select Option: State Bank Of India 1. Send Money 3. Check Balance 4. My Profile"
        assertEquals(CarrierDoctor.TestOutcome.MENU, CarrierDoctor.classify(menu, null, 5_000).outcome)
        assertEquals(CarrierDoctor.TestOutcome.ECHO, CarrierDoctor.classify("3", null, 30_000).outcome)
        assertEquals(CarrierDoctor.TestOutcome.REJECTED, CarrierDoctor.classify("Connection problem or invalid MMI code.", null, 2_000).outcome)
        assertEquals(CarrierDoctor.TestOutcome.OTHER_REPLY, CarrierDoctor.classify("Welcome to the service", null, 2_000).outcome)
        assertEquals(CarrierDoctor.TestOutcome.FAILED, CarrierDoctor.classify(null, -1, 100).outcome)
        assertEquals(CarrierDoctor.TestOutcome.TIMEOUT, CarrierDoctor.classify(null, null, 45_000).outcome)
        assertEquals(CarrierDoctor.Level.OK, CarrierDoctor.explain(CarrierDoctor.classify(menu, null, 5_000)).level)
        assertEquals(CarrierDoctor.Level.FAIL, CarrierDoctor.explain(CarrierDoctor.classify("3", null, 30_000)).level)
    }

    @Test fun phoneCheckFlagsTwoSimsAndDroppedSessions() {
        val facts = CarrierDoctor.Facts(true, true, false, true, 2, "SIM 1 · airtel", "airtel", 3)
        val f = CarrierDoctor.analyze(facts)
        assertTrue(f.any { it.title == "Two SIMs are on" && it.level == CarrierDoctor.Level.WARN })
        assertTrue(f.any { it.title == "The carrier cut sessions short" })
        val bad = CarrierDoctor.analyze(facts.copy(phonePermission = false, airplane = true, simReady = false, activeSimCount = 1, droppedSessionsRecorded = 0))
        assertEquals(3, bad.count { it.level == CarrierDoctor.Level.FAIL })
        assertTrue(bad.none { it.title == "The carrier cut sessions short" })
    }
}
