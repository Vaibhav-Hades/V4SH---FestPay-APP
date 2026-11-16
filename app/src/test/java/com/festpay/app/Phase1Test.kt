package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase1Test {
    private fun p(v: String, n: String, ok: Boolean) = PastPayee(v, n, ok)

    @Test fun frequentPayeesCountOnlyCarrierConfirmedPaymentsAndRankByCount() {
        val list = FrequentPayees.from(listOf(
            p("a@ybl", "Asha", true), p("b@sbi", "Bala", true), p("b@sbi", "", true), p("c@upi", "Chitra", false), p("A@ybl", "Asha", true), p("bad", "Bad", true)))
        assertEquals(listOf("a@ybl", "b@sbi"), list.map { it.vpa })
        assertEquals(2, list[0].times); assertEquals(2, list[1].times)
        assertEquals("Bala", list[1].name)
    }

    @Test fun frequentPayeesAreLimited() {
        val many = (1..9).map { p("u$it@ybl", "U$it", true) }
        assertEquals(5, FrequentPayees.from(many).size)
        assertTrue(FrequentPayees.from(emptyList()).isEmpty())
    }

    @Test fun problemReportHasDeviceFactsAndTitlesOnly() {
        val text = ProblemReport.build(ProblemReport.Facts("1.0", 34, "Xiaomi", "Redmi", "airtel", true, false, true, "SIM 1", listOf("Balance unavailable", "Balance unavailable", "Payment not started")))
        assertTrue(text.contains("Xiaomi Redmi")); assertTrue(text.contains("airtel")); assertTrue(text.contains("Accessibility: off"))
        assertEquals(1, Regex("Balance unavailable").findAll(text).count())
        assertFalse(text.contains("@"))
    }
}
