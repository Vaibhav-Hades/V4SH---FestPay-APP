package com.festpay.app.data

/** What the owner pasted when paying: a UPI link, a UPI ID inside some text, or nothing usable. */
sealed class PastedPayee {
    data class Link(val raw: String) : PastedPayee()
    data class Vpa(val vpa: String) : PastedPayee()
    object Nothing : PastedPayee()
}

object ClipboardPayee {
    private val vpaToken = Regex("""[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}""")

    fun parse(text: CharSequence?): PastedPayee {
        val t = text?.toString()?.trim().orEmpty()
        if (t.isEmpty() || t.length > 2_000) return PastedPayee.Nothing
        if (t.startsWith("upi://", ignoreCase = true)) return PastedPayee.Link(t)
        PaymentInputValidator.normalizedVpa(t)?.let { return PastedPayee.Vpa(it) }
        // A UPI ID inside a sentence is accepted only if it is the one ID in the text.
        val found = vpaToken.findAll(t).map { it.value.trimEnd('.', ',', ';') }.distinct().toList()
        val single = found.singleOrNull() ?: return PastedPayee.Nothing
        return PaymentInputValidator.normalizedVpa(single)?.let { PastedPayee.Vpa(it) } ?: PastedPayee.Nothing
    }
}

/** Opening Check Balance from Home asks it to start straight away, once. */
object BalanceQuick {
    @Volatile private var pending = false
    fun request() { pending = true }
    fun consume(): Boolean { val p = pending; pending = false; return p }
}

/** Plain-language wording for technical failures, with the one thing that usually fixes them. */
object ErrorHelp {
    data class Plain(val sentence: String, val fix: IssueFix)

    fun friendly(message: String?): Plain? {
        val m = message?.lowercase() ?: return null
        return when {
            "accessibility is not running" in m || "accessibility is not connected" in m || "accessibility disconnected" in m || "screen assistance stopped" in m ->
                Plain("V4SH is switched off. Turn it on, then try again.", IssueFix.ACCESSIBILITY_SETTINGS)
            "phone permission" in m -> Plain("V4SH needs permission to make the *99# call.", IssueFix.PHONE_PERMISSION)
            "another cellular operation" in m -> Plain("An earlier *99# session is still marked as running.", IssueFix.STOP_STUCK_OPERATION)
            "invalid mmi" in m || "connection problem" in m ->
                Plain("Your SIM company did not accept *99#. Try the other SIM, or check that this SIM is linked to your bank.", IssueFix.NONE)
            "no signal" in m || "cellular signal" in m || "sim" in m && "not ready" in m ->
                Plain("The phone has no usable mobile signal or SIM right now.", IssueFix.NONE)
            "carrier screen was closed" in m -> Plain("The carrier screen was closed before the answer came.", IssueFix.NONE)
            else -> null
        }
    }
}
