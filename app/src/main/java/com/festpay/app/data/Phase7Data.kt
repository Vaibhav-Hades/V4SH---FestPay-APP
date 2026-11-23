package com.festpay.app.data

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode

// ---------------------------------------------------------------------------------------------------------------------------
// Split a bill
// ---------------------------------------------------------------------------------------------------------------------------
object BillSplit {
    /** Equal shares of [total] between [people], in paise. Any leftover paise go to the first shares, one each, so the shares add up exactly. */
    fun equalShares(total: BigDecimal, people: Int): List<BigDecimal> {
        require(people in 1..50)
        val paise = total.setScale(2, RoundingMode.DOWN).movePointRight(2).toBigInteger()
        val n = people.toBigInteger()
        val base = paise.divide(n)
        val extra = paise.mod(n).toInt()
        return List(people) { i -> BigDecimal(base.add(if (i < extra) java.math.BigInteger.ONE else java.math.BigInteger.ZERO)).movePointLeft(2) }
    }

    fun sum(shares: List<BigDecimal>): BigDecimal = shares.fold(BigDecimal.ZERO) { a, b -> a + b }

    /** Canonical amount text ("12.50") if [text] is a valid payment amount, else null. */
    fun amountText(text: String): String? = PaymentInputValidator.amount(text.trim())
}

/** One person's share while the owner pays them one after another. Lives only in memory. */
data class SplitRow(val id: Int, val vpa: String = "", val name: String = "", val amount: String = "")

object SplitSession {
    var total: String = ""
    var rows: List<SplitRow> = emptyList()
    /** When this split began (epoch millis); only successful payments after this count as "paid". */
    var startedAt: Long = System.currentTimeMillis()

    fun reset() { total = ""; rows = emptyList(); startedAt = System.currentTimeMillis() }
}

// ---------------------------------------------------------------------------------------------------------------------------
// Spoken confirmation
// ---------------------------------------------------------------------------------------------------------------------------
object SpokenConfirmation {
    private const val PREFS = "v4sh_settings"
    private const val KEY_ON = "spoken_confirmation"
    private const val KEY_NAME = "spoken_confirmation_name"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    fun setEnabled(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply() }
    /** Saying the receiver's name aloud is off by default: other people nearby can hear it. */
    fun sayName(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NAME, false)
    fun setSayName(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NAME, on).apply() }

    /** "10 rupees" or "10 rupees 50 paise". Null when the amount is not a valid payment amount. */
    fun amountWords(amount: String): Pair<String, String?>? {
        val canonical = PaymentInputValidator.amount(amount) ?: return null
        val (rupees, paise) = canonical.split('.').let { it[0] to it.getOrElse(1) { "00" } }
        return rupees to paise.takeIf { it != "00" }?.trimStart('0')?.ifEmpty { "0" }
    }

    /** The sentence spoken after the carrier confirms a payment. [language] is "en", "hi" or "te"; anything else is English. */
    fun message(amount: String, payee: String?, language: String): String? {
        val (rupees, paise) = amountWords(amount) ?: return null
        val name = payee?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
        return when (language) {
            "hi" -> (if (name != null) "$name को " else "") + "$rupees रुपये" + (paise?.let { " $it पैसे" } ?: "") + " का भुगतान सफल हुआ।"
            "te" -> (if (name != null) "$name కు " else "") + "$rupees రూపాయలు" + (paise?.let { " $it పైసలు" } ?: "") + " చెల్లింపు విజయవంతమైంది."
            else -> "Payment of $rupees rupees" + (paise?.let { " $it paise" } ?: "") + (if (name != null) " to $name" else "") + " successful."
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------
// Carrier Doctor
// ---------------------------------------------------------------------------------------------------------------------------
object CarrierDoctor {
    enum class Level { OK, WARN, FAIL, INFO }
    data class Finding(val level: Level, val title: String, val advice: String)

    data class Facts(
        val phonePermission: Boolean, val accessibilityConnected: Boolean, val airplane: Boolean?,
        val simReady: Boolean, val activeSimCount: Int?, val chosenSimLabel: String,
        val operator: String?, val droppedSessionsRecorded: Int
    )

    /** Reads the phone's state without dialling anything. */
    fun analyze(f: Facts): List<Finding> = buildList {
        add(if (f.phonePermission) Finding(Level.OK, "Phone permission", "Allowed.")
            else Finding(Level.FAIL, "Phone permission", "V4SH cannot start *99# without it. Allow phone calls."))
        add(when {
            f.airplane == true -> Finding(Level.FAIL, "Airplane mode", "Turn airplane mode off.")
            else -> Finding(Level.OK, "Airplane mode", "Off.")
        })
        add(if (f.simReady) Finding(Level.OK, "SIM", "Ready" + (f.operator?.let { " · $it" } ?: "") + ".")
            else Finding(Level.FAIL, "SIM", "No usable SIM right now. Insert or unlock the SIM registered with your bank."))
        add(when {
            f.activeSimCount == null -> Finding(Level.INFO, "Number of active SIMs", "Unknown. Open Calling SIM once to let V4SH list your SIMs.")
            f.activeSimCount > 1 -> Finding(Level.WARN, "Two SIMs are on",
                "On some phones a second active SIM breaks the back-and-forth of *99# (replies come back as just a digit). If a test fails, switch the other SIM off in Android settings and try again.")
            else -> Finding(Level.OK, "Number of active SIMs", "One.")
        })
        add(Finding(Level.INFO, "Calling SIM in V4SH", f.chosenSimLabel + ". Make sure this is the SIM your bank has registered."))
        add(if (f.accessibilityConnected) Finding(Level.OK, "Accessibility", "V4SH is on, so it can type the menu steps.")
            else Finding(Level.WARN, "Accessibility", "V4SH is off. Turn it on before a payment or balance check."))
        if (f.droppedSessionsRecorded > 0) add(Finding(Level.WARN, "The carrier cut sessions short",
            "${f.droppedSessionsRecorded} recent attempt(s) ended with the carrier answering only the digit V4SH sent. Try the test below, switch off VoLTE for this SIM, or ask your SIM company and bank to check *99# for your number."))
    }

    enum class TestOutcome { MENU, ECHO, REJECTED, OTHER_REPLY, FAILED, TIMEOUT, NOT_ALLOWED }
    data class TestResult(val outcome: TestOutcome, val millis: Long, val detail: String?)

    /** Turns the carrier's reply to a lone *99# request into a plain result. */
    fun classify(response: String?, failureCode: Int?, millis: Long): TestResult {
        if (response == null) return TestResult(if (failureCode == null) TestOutcome.TIMEOUT else TestOutcome.FAILED, millis, failureCode?.toString())
        val text = response.trim()
        return when {
            UssdNavigationPolicy.isRootMenu(text) -> TestResult(TestOutcome.MENU, millis, null)
            UssdNavigationPolicy.isEchoedInput(text) -> TestResult(TestOutcome.ECHO, millis, text.take(40))
            UssdNavigationPolicy.isCarrierRejection(text) -> TestResult(TestOutcome.REJECTED, millis, text.take(80))
            else -> TestResult(TestOutcome.OTHER_REPLY, millis, text.take(80))
        }
    }

    fun explain(r: TestResult): Finding = when (r.outcome) {
        TestOutcome.MENU -> Finding(Level.OK, "*99# answered with its menu in ${seconds(r.millis)}", "The service works on this SIM right now. Wait about two minutes before paying, so this test session can close on the network.")
        TestOutcome.ECHO -> Finding(Level.FAIL, "*99# replied with just a digit", "The carrier is not running the menu. Try again in a while, switch off the other SIM and VoLTE for this SIM, or ask your SIM company and bank to check *99# for your number.")
        TestOutcome.REJECTED -> Finding(Level.FAIL, "The carrier rejected the request", "It said: \"${r.detail}\". This usually means *99# is not available or your number is not registered for UPI on this SIM. Ask your bank and SIM company.")
        TestOutcome.OTHER_REPLY -> Finding(Level.WARN, "The carrier replied with something V4SH does not recognise", "It said: \"${r.detail}\".")
        TestOutcome.FAILED -> Finding(Level.FAIL, "The phone could not send the request", "Android reported error ${r.detail}. Check signal, airplane mode and that the SIM is registered on the network.")
        TestOutcome.TIMEOUT -> Finding(Level.FAIL, "No answer from the carrier", "Nothing came back in time. Check your signal and try again later.")
        TestOutcome.NOT_ALLOWED -> Finding(Level.FAIL, "Android did not allow the test", "Allow phone calls for V4SH and try again.")
    }

    private fun seconds(ms: Long) = String.format(java.util.Locale.ENGLISH, "%.1f s", ms / 1000.0)
}
