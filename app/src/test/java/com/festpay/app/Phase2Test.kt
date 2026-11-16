package com.festpay.app

import com.festpay.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.YearMonth

class Phase2Test {
    private fun fact(ts: String, vpa: String, name: String, amt: String, outcome: String?, ref: String? = null) =
        PaymentFact(ts, name, vpa, amt, outcome, ref, "id-$ts-$vpa", outcome ?: "Unclear")

    @Test fun spendingCountsOnlyCarrierConfirmedSuccessesPerMonthAndPayee() {
        val r = SpendingSummary.of(listOf(
            fact("2026-11-02T10:00:00Z", "a@ybl", "Asha", "10.50", "SUCCESS"),
            fact("2026-11-05T10:00:00Z", "A@ybl", "", "4.50", "SUCCESS"),
            fact("2026-10-30T10:00:00Z", "b@sbi", "Bala", "100", "SUCCESS"),
            fact("2026-11-06T10:00:00Z", "c@upi", "Chitra", "999", "FAILED"),
            fact("2026-11-07T10:00:00Z", "d@upi", "Dev", "999", null),
            fact("not a time", "e@upi", "Eve", "5", "SUCCESS")), ZoneId.of("UTC"))
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), r.months.map { it.month })
        assertEquals("15.00", r.months[0].total.setScale(2).toPlainString()); assertEquals(2, r.months[0].count)
        assertEquals("b@sbi", r.topPayees[0].vpa); assertEquals("Asha", r.topPayees[1].name)
    }

    @Test fun csvQuotesAndDefangsFormulas() {
        val csv = HistoryExport.csv(listOf(fact("2026-11-02T10:00:00Z", "a@ybl", "=HYPERLINK(\"x\"),Bob", "1.00", "SUCCESS", "123")))
        val lines = csv.trimEnd().split("\r\n")
        assertEquals(HistoryExport.HEADER.joinToString(","), lines[0])
        assertTrue(lines[1].contains("\"'=HYPERLINK(\"\"x\"\"),Bob\""))
        assertEquals("'+91", HistoryExport.cell("+91")); assertEquals("a b", HistoryExport.cell("a\nb"))
    }

    @Test fun receiptsExistOnlyForCarrierSuccessAndSayWhoseWordItIs() {
        assertNull(ReceiptModel.of(fact("t", "a@ybl", "A", "1", "FAILED"), "x"))
        assertNull(ReceiptModel.of(fact("t", "a@ybl", "A", "1", null), "x"))
        val r = ReceiptModel.of(fact("t", "a@ybl", "", "1.00", "SUCCESS", "9876"), "2 Nov 2026")!!
        assertTrue(r.rows.contains("Reference" to "9876")); assertTrue(r.rows.contains("To" to "Name not recorded"))
        assertTrue(r.footer.contains("carrier"))
    }
}
