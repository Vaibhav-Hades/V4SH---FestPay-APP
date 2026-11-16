package com.festpay.app.data

/**
 * Extracts the reference the carrier itself prints in its final message ("... for Rs.1.00 is successful (RefId: 214749357315)").
 * It is the carrier's wording, never verified by a bank, and is only returned when the message shows exactly one reference
 * and exactly one amount equal to the reviewed request.
 */
object CarrierReference {
    private val reference = Regex("""\bref\s*-?\s*(?:[il]d|no|number)?\s*[:#-]?\s*(\d{6,20})\b""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""(?:\u20b9|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    fun parse(message: String, requestAmount: String): String? {
        val refs = reference.findAll(message).map { it.groupValues[1] }.distinct().toList()
        val amounts = amount.findAll(message).map { it.groupValues[1].replace(",", "") }.distinct().toList()
        val ref = refs.singleOrNull() ?: return null
        val shown = amounts.singleOrNull()?.let { PaymentInputValidator.amount(it) } ?: return null
        return ref.takeIf { shown == requestAmount }
    }
}
