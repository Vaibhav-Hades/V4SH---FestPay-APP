package com.festpay.app.data

import android.content.Context
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A payment as the summary, export and receipt features see it. Only the fields they need. */
data class PaymentFact(
    val timestamp: String, val payeeName: String, val payeeVpa: String, val amount: String,
    val carrierOutcome: String?, val reference: String?, val localId: String, val statusLabel: String
)

object SpendingSummary {
    data class Month(val month: YearMonth, val total: BigDecimal, val count: Int)
    data class Payee(val vpa: String, val name: String, val total: BigDecimal, val count: Int)
    data class Result(val months: List<Month>, val topPayees: List<Payee>)

    /** Only payments the carrier itself closed as successful are counted. Newest month first. */
    fun of(facts: List<PaymentFact>, zone: ZoneId = ZoneId.systemDefault()): Result {
        val ok = facts.filter { it.carrierOutcome == "SUCCESS" }.mapNotNull { f ->
            val at = runCatching { Instant.parse(f.timestamp) }.getOrNull() ?: return@mapNotNull null
            val amount = f.amount.toBigDecimalOrNull() ?: return@mapNotNull null
            Triple(YearMonth.from(at.atZone(zone)), amount, f)
        }
        val months = ok.groupBy { it.first }.map { (m, rows) -> Month(m, rows.fold(BigDecimal.ZERO) { a, r -> a + r.second }, rows.size) }
            .sortedByDescending { it.month }
        val payees = ok.groupBy { it.third.payeeVpa.lowercase() }.map { (_, rows) ->
            Payee(rows.first().third.payeeVpa, rows.map { it.third.payeeName }.firstOrNull { it.isNotBlank() }.orEmpty(),
                rows.fold(BigDecimal.ZERO) { a, r -> a + r.second }, rows.size)
        }.sortedByDescending { it.total }.take(5)
        return Result(months, payees)
    }
}

object HistoryExport {
    val HEADER = listOf("Date (UTC)", "Name (unverified)", "UPI ID", "Amount (INR)", "Status", "Carrier reference", "V4SH attempt ID")

    /** Spreadsheet programs run cells that start with = + - @ as formulas, so those cells are defanged. */
    fun cell(value: String): String {
        var v = value.replace("\r", " ").replace("\n", " ")
        if (v.isNotEmpty() && v[0] in "=+-@\t") v = "'$v"
        return if (v.any { it == ',' || it == '"' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun csv(facts: List<PaymentFact>): String = buildString {
        append(HEADER.joinToString(",")).append("\r\n")
        facts.forEach { f ->
            append(listOf(f.timestamp, f.payeeName, f.payeeVpa, f.amount, f.statusLabel, f.reference.orEmpty(), f.localId).joinToString(",") { cell(it) }).append("\r\n")
        }
    }
}

/** What a shareable receipt says. Only for payments the carrier closed as successful, and it says whose word that is. */
object ReceiptModel {
    data class Receipt(val title: String, val rows: List<Pair<String, String>>, val footer: String)

    fun of(f: PaymentFact, shortTime: String): Receipt? {
        if (f.carrierOutcome != "SUCCESS") return null
        return Receipt("Payment successful",
            listOfNotNull(
                "Amount" to "₹${f.amount}",
                "To" to f.payeeName.ifBlank { "Name not recorded" },
                "UPI ID" to f.payeeVpa,
                "Date" to shortTime,
                f.reference?.let { "Reference" to it }
            ),
            "Taken from the carrier's own final message on the sender's phone. Not a bank statement.")
    }
}
